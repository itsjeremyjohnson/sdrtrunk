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

import io.github.dsheirer.protocol.Protocol;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
public class TalkerAliasLogger
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
        String protocolSuffix = protocol == Protocol.DMR ? "_dmr" : "";
        mAliasFile = logDirectory.resolve(systemName + protocolSuffix + "_talker_aliases.csv")
            .toAbsolutePath().normalize();
        mIdentifierFactory = protocol == Protocol.DMR ? DmrTalkerAliasIdentifier::create :
            P25TalkerAliasIdentifier::create;
        mFileState = FILE_STATES.computeIfAbsent(mAliasFile, ignored -> new AliasFileState());
    }

    /**
     * Called whenever the alias map changes. Writes the merged aliases from every manager using this system file.
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

    /**
     * Coordinates snapshots from all logger instances that target the same system file.
     */
    private static class AliasFileState
    {
        private final Map<Object, Map<Integer, String>> mSnapshots = new LinkedHashMap<>();
        private boolean mLoaded;
        private String mLastWrittenContent;

        public synchronized Map<Integer, String> bootstrap(Path aliasFile, Object sourceKey)
        {
            ensureLoaded(aliasFile);
            Map<Integer, String> merged = getMergedAliases();
            mSnapshots.put(sourceKey, new HashMap<>(merged));
            return merged;
        }

        public synchronized void update(Path aliasFile, Object sourceKey,
                                        Map<Integer, TalkerAliasIdentifier> aliases)
        {
            ensureLoaded(aliasFile);
            Map<Integer, String> snapshot = new HashMap<>();
            aliases.forEach((radioId, alias) -> snapshot.put(radioId, alias.getValue()));

            // Reinsert so the latest manager update wins if two managers report the same radio.
            mSnapshots.remove(sourceKey);
            mSnapshots.put(sourceKey, snapshot);
            write(aliasFile, getMergedAliases());
        }

        private void ensureLoaded(Path aliasFile)
        {
            if(mLoaded)
            {
                return;
            }

            Map<Integer, String> loaded = read(aliasFile);
            if(!loaded.isEmpty())
            {
                mSnapshots.put(new Object(), loaded);
            }
            mLoaded = true;
        }

        private Map<Integer, String> getMergedAliases()
        {
            Map<Integer, String> merged = new HashMap<>();
            mSnapshots.values().forEach(merged::putAll);
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

                        if(aliasText.startsWith("TA-"))
                        {
                            aliasText = aliasText.substring(3);
                        }

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

                mLastWrittenContent = content;
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
