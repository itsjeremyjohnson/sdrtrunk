/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * ****************************************************************************
 */
package io.github.dsheirer.identifier.alias;

import io.github.dsheirer.module.Module;
import io.github.dsheirer.protocol.Protocol;
import io.github.dsheirer.util.StringUtils;
import io.github.dsheirer.util.ThreadPool;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persists observed talker aliases to a CSV file and bootstraps them back on startup.
 */
public class TalkerAliasLogger extends Module
{
    private static final Logger mLog = LoggerFactory.getLogger(TalkerAliasLogger.class);
    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
        .setHeader("RADIO_ID", "TALKER_ALIAS")
        .setSkipHeaderRecord(false)
        .setRecordSeparator('\n')
        .get();
    private static final Map<Path, AliasFileState> FILE_STATES = new ConcurrentHashMap<>();

    private final Path mAliasFile;
    private final Function<String, TalkerAliasIdentifier> mIdentifierFactory;
    private final AliasFileState mFileState;
    private final Object mSourceKey = new Object();

    /**
     * Constructs a P25 logger for backward compatibility.
     * @param logDirectory where the alias CSV file is stored
     * @param systemName used as the filename prefix
     */
    public TalkerAliasLogger(Path logDirectory, String systemName)
    {
        this(logDirectory, systemName, Protocol.APCO25);
    }

    /**
     * Constructs an instance.
     * @param logDirectory where the alias CSV file is stored
     * @param systemName used as the filename prefix
     * @param protocol protocol used to reconstruct persisted identifiers
     */
    public TalkerAliasLogger(Path logDirectory, String systemName, Protocol protocol)
    {
        mAliasFile = logDirectory.resolve(getAliasFileName(systemName, protocol)).toAbsolutePath().normalize();
        mIdentifierFactory = protocol == Protocol.DMR ? DmrTalkerAliasIdentifier::create :
            P25TalkerAliasIdentifier::create;
        mFileState = FILE_STATES.computeIfAbsent(mAliasFile, ignored -> new AliasFileState());
    }

    static String getAliasFileName(String systemIdentity, Protocol protocol)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(systemIdentity.getBytes(StandardCharsets.UTF_8));
            String identitySuffix = HexFormat.of().formatHex(digest, 0, 16);
            String protocolSuffix = protocol == Protocol.DMR ? "_dmr" : "";
            return StringUtils.replaceIllegalCharacters(systemIdentity) + "_" + identitySuffix + protocolSuffix +
                "_talker_aliases.csv";
        }
        catch(NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /**
     * Called whenever the alias map changes. Schedules persistence of the merged aliases from every manager using this
     * system file.
     * @param aliases current alias map (radioId -> TalkerAliasIdentifier)
     */
    public void onAliasUpdate(Map<Integer, TalkerAliasIdentifier> aliases)
    {
        mFileState.update(mAliasFile, mSourceKey, aliases);
    }

    /**
     * Reads previously persisted aliases and preloads them into the alias manager.
     * @param manager to preload
     */
    public void bootstrap(TalkerAliasManager manager)
    {
        Map<Integer, String> loaded = mFileState.bootstrap(mAliasFile, mSourceKey);
        Map<Integer, TalkerAliasIdentifier> identifiers = new HashMap<>();
        loaded.forEach((radioId, alias) -> identifiers.put(radioId, mIdentifierFactory.apply(alias)));

        if(!identifiers.isEmpty())
        {
            manager.preload(identifiers);
            mLog.info("Preloaded " + identifiers.size() + " talker aliases from [" + mAliasFile + "]");
        }
    }

    @Override
    public void reset()
    {
    }

    @Override
    public void start()
    {
    }

    @Override
    public void stop()
    {
    }

    void awaitPendingWrites()
    {
        mFileState.awaitPendingWrites(mAliasFile);
    }

    int getWriteCount()
    {
        return mFileState.getWriteCount();
    }

    @Override
    public void dispose()
    {
        mFileState.release(mAliasFile, mSourceKey);
        mFileState.awaitPendingWrites(mAliasFile);
        super.dispose();
    }

    /**
     * Coordinates snapshots from all logger instances that target the same system file.
     */
    private static class AliasFileState
    {
        private final Map<Integer, String> mBaseline = new HashMap<>();
        private final Map<Integer, Long> mBaselineOrders = new HashMap<>();
        private final Map<Object, Map<Integer, String>> mInheritedAliases = new HashMap<>();
        private final Map<Object, Map<Integer, String>> mSnapshots = new LinkedHashMap<>();
        private final Map<Object, Map<Integer, Long>> mSnapshotOrders = new HashMap<>();
        private long mUpdateSequence;
        private boolean mLoaded;
        private String mLastWrittenContent;
        private boolean mWriteDirty;
        private boolean mWriteScheduled;
        private boolean mWriterRunning;
        private int mWriteCount;

        public synchronized Map<Integer, String> bootstrap(Path aliasFile, Object sourceKey)
        {
            ensureLoaded(aliasFile);
            Map<Integer, String> inherited = getMergedAliases();
            mInheritedAliases.put(sourceKey, new HashMap<>(inherited));
            return inherited;
        }

        public synchronized void update(Path aliasFile, Object sourceKey,
                                        Map<Integer, TalkerAliasIdentifier> aliases)
        {
            ensureLoaded(aliasFile);
            Map<Integer, String> inherited = mInheritedAliases.getOrDefault(sourceKey, mBaseline);
            Map<Integer, String> previousSnapshot = mSnapshots.getOrDefault(sourceKey, Map.of());
            Map<Integer, Long> previousOrders = mSnapshotOrders.getOrDefault(sourceKey, Map.of());
            Map<Integer, String> snapshot = new HashMap<>();
            Map<Integer, Long> snapshotOrders = new HashMap<>();
            aliases.forEach((radioId, alias) ->
            {
                String aliasValue = alias.getValue();
                if(!Objects.equals(aliasValue, inherited.get(radioId)))
                {
                    snapshot.put(radioId, aliasValue);
                    if(Objects.equals(aliasValue, previousSnapshot.get(radioId)) && previousOrders.containsKey(radioId))
                    {
                        snapshotOrders.put(radioId, previousOrders.get(radioId));
                    }
                    else
                    {
                        snapshotOrders.put(radioId, ++mUpdateSequence);
                    }
                }
            });

            mSnapshots.put(sourceKey, snapshot);
            mSnapshotOrders.put(sourceKey, snapshotOrders);
            scheduleWrite(aliasFile);
        }

        public synchronized void release(Path aliasFile, Object sourceKey)
        {
            Map<Integer, String> snapshot = mSnapshots.remove(sourceKey);
            Map<Integer, Long> snapshotOrders = mSnapshotOrders.remove(sourceKey);
            if(snapshot != null && snapshotOrders != null)
            {
                snapshot.forEach((radioId, alias) ->
                {
                    long order = snapshotOrders.getOrDefault(radioId, 0L);
                    if(order >= mBaselineOrders.getOrDefault(radioId, 0L))
                    {
                        mBaseline.put(radioId, alias);
                        mBaselineOrders.put(radioId, order);
                    }
                });
                scheduleWrite(aliasFile);
            }
            mInheritedAliases.remove(sourceKey);
        }

        private synchronized void scheduleWrite(Path aliasFile)
        {
            mWriteDirty = true;
            if(!mWriteScheduled && !mWriterRunning)
            {
                mWriteScheduled = true;
                ThreadPool.SCHEDULED.schedule(() -> runWriter(aliasFile), 50, TimeUnit.MILLISECONDS);
            }
        }

        private void runWriter(Path aliasFile)
        {
            Map<Integer, String> aliases;
            synchronized(this)
            {
                mWriteScheduled = false;
                mWriterRunning = true;
                mWriteDirty = false;
                aliases = getMergedAliases();
            }

            write(aliasFile, aliases);

            synchronized(this)
            {
                mWriterRunning = false;
                if(mWriteDirty)
                {
                    scheduleWrite(aliasFile);
                }
                else
                {
                    notifyAll();
                }
            }
        }

        public synchronized int getWriteCount()
        {
            return mWriteCount;
        }

        public synchronized void awaitPendingWrites(Path aliasFile)
        {
            if(mWriteDirty && !mWriteScheduled && !mWriterRunning)
            {
                scheduleWrite(aliasFile);
            }

            while(mWriteScheduled || mWriterRunning)
            {
                try
                {
                    wait();
                }
                catch(InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        private void ensureLoaded(Path aliasFile)
        {
            if(mLoaded)
            {
                return;
            }

            mBaseline.putAll(read(aliasFile));
            mLoaded = true;
        }

        private Map<Integer, String> getMergedAliases()
        {
            Map<Integer, String> merged = new HashMap<>(mBaseline);
            Map<Integer, Long> mergedOrders = new HashMap<>(mBaselineOrders);
            mSnapshots.forEach((sourceKey, snapshot) ->
            {
                Map<Integer, Long> orders = mSnapshotOrders.getOrDefault(sourceKey, Map.of());
                snapshot.forEach((radioId, alias) ->
                {
                    long order = orders.getOrDefault(radioId, 0L);
                    if(order >= mergedOrders.getOrDefault(radioId, 0L))
                    {
                        merged.put(radioId, alias);
                        mergedOrders.put(radioId, order);
                    }
                });
            });
            return merged;
        }

        private Map<Integer, String> read(Path aliasFile)
        {
            Map<Integer, String> loaded = new HashMap<>();
            CSVFormat readFormat = CSV_FORMAT.builder().setSkipHeaderRecord(true).get();

            try(CSVParser parser = CSVParser.parse(aliasFile, StandardCharsets.UTF_8, readFormat))
            {
                for(CSVRecord record : parser)
                {
                    try
                    {
                        int radioId = Integer.parseInt(record.get("RADIO_ID").trim());
                        String aliasText = record.get("TALKER_ALIAS");
                        loaded.put(radioId, aliasText);
                    }
                    catch(NumberFormatException e)
                    {
                        mLog.debug("Skipping invalid talker alias record: " + record);
                    }
                }
            }
            catch(NoSuchFileException e)
            {
                // No persisted aliases yet.
            }
            catch(IOException | IllegalArgumentException e)
            {
                mLog.warn("Could not read talker alias bootstrap file [" + aliasFile + "]", e);
            }

            return loaded;
        }

        private void write(Path aliasFile, Map<Integer, String> aliases)
        {
            String content;

            try(StringWriter writer = new StringWriter(); CSVPrinter printer = new CSVPrinter(writer, CSV_FORMAT))
            {
                for(Map.Entry<Integer, String> entry : aliases.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder())).toList())
                {
                    printer.printRecord(entry.getKey(), entry.getValue() != null ? entry.getValue() : "");
                }
                printer.flush();
                content = writer.toString();
            }
            catch(IOException e)
            {
                mLog.error("Error formatting talker alias CSV", e);
                return;
            }

            if(content.equals(mLastWrittenContent))
            {
                return;
            }

            Path logDirectory = aliasFile.getParent();
            Path tempFile = null;

            try
            {
                Files.createDirectories(logDirectory);
                tempFile = Files.createTempFile(logDirectory, aliasFile.getFileName().toString(), ".tmp");
                Files.writeString(tempFile, content, StandardOpenOption.TRUNCATE_EXISTING);

                try
                {
                    Files.move(tempFile, aliasFile, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
                }
                catch(AtomicMoveNotSupportedException e)
                {
                    Files.move(tempFile, aliasFile, StandardCopyOption.REPLACE_EXISTING);
                }

                synchronized(this)
                {
                    mLastWrittenContent = content;
                    mWriteCount++;
                }
            }
            catch(IOException e)
            {
                mLog.error("Error writing talker alias file [" + aliasFile + "]", e);
            }
            finally
            {
                if(tempFile != null)
                {
                    try
                    {
                        Files.deleteIfExists(tempFile);
                    }
                    catch(IOException e)
                    {
                        mLog.warn("Could not delete temporary talker alias file [" + tempFile + "]", e);
                    }
                }
            }
        }
    }
}
