/*
 * *****************************************************************************
 * Copyright (C) 2026 Dennis Sheirer
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ****************************************************************************
 */
package io.github.dsheirer.identifier.alias;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TalkerAliasLoggerTest
{
    @Test
    void persistsLatestAliasStateInDeterministicOrder(@TempDir Path logDirectory) throws IOException
    {
        TalkerAliasLogger logger = new TalkerAliasLogger(logDirectory, "test-system");
        Map<Integer, TalkerAliasIdentifier> aliases = new HashMap<>();
        aliases.put(20, P25TalkerAliasIdentifier.create("Second"));
        aliases.put(10, P25TalkerAliasIdentifier.create("First, Alias"));

        logger.onAliasUpdate(aliases);

        Path aliasFile = logDirectory.resolve("test-system_talker_aliases.csv");
        assertEquals("RADIO_ID,TALKER_ALIAS\n10,First Alias\n20,Second\n", Files.readString(aliasFile));

        aliases.clear();
        aliases.put(30, P25TalkerAliasIdentifier.create("Latest"));
        logger.onAliasUpdate(aliases);

        assertEquals("RADIO_ID,TALKER_ALIAS\n30,Latest\n", Files.readString(aliasFile));
        try(var files = Files.list(logDirectory))
        {
            assertEquals(0, files.filter(path -> path.getFileName().toString().endsWith(".tmp")).count());
        }
    }
}
