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
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persists observed P25 talker aliases to a CSV file and bootstraps them back on startup.
 */
public class TalkerAliasLogger
{
    private static final Logger mLog = LoggerFactory.getLogger(TalkerAliasLogger.class);
    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
        .setHeader("RADIO_ID", "TALKER_ALIAS")
        .setSkipHeaderRecord(false)
        .setRecordSeparator('\n')
        .get();

    private final Path mLogDirectory;
    private final String mSystemName;
    private String mLastWrittenContent = null;

    /**
     * Constructs an instance.
     * @param logDirectory where the alias CSV file is stored
     * @param systemName used as the filename prefix
     */
    public TalkerAliasLogger(Path logDirectory, String systemName)
    {
        mLogDirectory = logDirectory;
        mSystemName = systemName;
    }

    /**
     * Called whenever the alias map changes. Writes updated aliases to disk if the content has changed.
     * @param aliases current alias map (radioId -> TalkerAliasIdentifier)
     */
    public synchronized void onAliasUpdate(Map<Integer, TalkerAliasIdentifier> aliases)
    {
        String content;

        try(StringWriter writer = new StringWriter(); CSVPrinter printer = new CSVPrinter(writer, CSV_FORMAT))
        {
            for(Map.Entry<Integer, TalkerAliasIdentifier> entry : aliases.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder())).toList())
            {
                String aliasText = entry.getValue().getValue();
                printer.printRecord(entry.getKey(), aliasText != null ? aliasText : "");
            }
            printer.flush();
            content = writer.toString();
        }
        catch(IOException e)
        {
            mLog.error("Error formatting talker alias CSV", e);
            return;
        }

        if(!content.equals(mLastWrittenContent))
        {
            Path aliasFile = mLogDirectory.resolve(mSystemName + "_talker_aliases.csv");
            Path tempFile = null;

            try
            {
                Files.createDirectories(mLogDirectory);
                tempFile = Files.createTempFile(mLogDirectory, mSystemName + "_talker_aliases", ".tmp");
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

    /**
     * Reads previously persisted aliases and preloads them into the alias manager.
     * @param manager to preload
     */
    public void bootstrap(TalkerAliasManager manager)
    {
        Path aliasFile = mLogDirectory.resolve(mSystemName + "_talker_aliases.csv");

        Map<Integer, TalkerAliasIdentifier> loaded = new HashMap<>();
        CSVFormat readFormat = CSV_FORMAT.builder().setSkipHeaderRecord(true).get();

        try(CSVParser parser = CSVParser.parse(aliasFile, StandardCharsets.UTF_8, readFormat))
        {
            for(CSVRecord record : parser)
            {
                try
                {
                    int radioId = Integer.parseInt(record.get("RADIO_ID").trim());
                    String aliasText = record.get("TALKER_ALIAS");

                    // Strip "TA-" prefix for compatibility with older files.
                    if(aliasText.startsWith("TA-"))
                    {
                        aliasText = aliasText.substring(3);
                    }

                    loaded.put(radioId, P25TalkerAliasIdentifier.create(aliasText));
                }
                catch(NumberFormatException e)
                {
                    mLog.debug("Skipping invalid talker alias record: " + record);
                }
            }
        }
        catch(NoSuchFileException e)
        {
            return;
        }
        catch(IOException | IllegalArgumentException e)
        {
            mLog.warn("Could not read talker alias bootstrap file [" + aliasFile + "]", e);
            return;
        }

        if(!loaded.isEmpty())
        {
            manager.preload(loaded);
            mLog.info("Preloaded " + loaded.size() + " talker aliases for system [" + mSystemName + "]");
        }
    }
}
