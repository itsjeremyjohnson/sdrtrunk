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

import io.github.dsheirer.identifier.Form;
import io.github.dsheirer.identifier.Identifier;
import io.github.dsheirer.identifier.IdentifierClass;
import io.github.dsheirer.identifier.MutableIdentifierCollection;
import io.github.dsheirer.identifier.Role;
import io.github.dsheirer.module.decode.p25.identifier.radio.APCO25RadioIdentifier;
import io.github.dsheirer.protocol.Protocol;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TalkerAliasLoggerTest
{
    @Test
    void persistsLatestAliasStateInDeterministicOrder(@TempDir Path logDirectory) throws IOException
    {
        TalkerAliasLogger logger = new TalkerAliasLogger(logDirectory, "test-system");
        Map<Integer, TalkerAliasIdentifier> aliases = new HashMap<>();
        aliases.put(20, P25TalkerAliasIdentifier.create("Second"));
        aliases.put(10, P25TalkerAliasIdentifier.create("First,\nAlias"));

        logger.onAliasUpdate(aliases);

        Path aliasFile = logDirectory.resolve("test-system_talker_aliases.csv");
        assertEquals("RADIO_ID,TALKER_ALIAS\n10,\"First,\nAlias\"\n20,Second\n", Files.readString(aliasFile));

        TalkerAliasManager manager = new TalkerAliasManager();
        logger.bootstrap(manager);
        assertTrue(manager.getAliasSummary().contains("TA-First,\nAlias"));

        aliases.clear();
        aliases.put(30, P25TalkerAliasIdentifier.create("Latest"));
        logger.onAliasUpdate(aliases);

        assertEquals("RADIO_ID,TALKER_ALIAS\n30,Latest\n", Files.readString(aliasFile));
        try(var files = Files.list(logDirectory))
        {
            assertEquals(0, files.filter(path -> path.getFileName().toString().endsWith(".tmp")).count());
        }
    }

    @Test
    void mergesSnapshotsFromManagersForTheSameSystem(@TempDir Path logDirectory) throws IOException
    {
        TalkerAliasLogger firstLogger = new TalkerAliasLogger(logDirectory, "shared-system");
        TalkerAliasLogger secondLogger = new TalkerAliasLogger(logDirectory, "shared-system");

        firstLogger.onAliasUpdate(Map.of(10, P25TalkerAliasIdentifier.create("First")));
        secondLogger.onAliasUpdate(Map.of(20, P25TalkerAliasIdentifier.create("Second")));

        assertEquals("RADIO_ID,TALKER_ALIAS\n10,First\n20,Second\n",
            Files.readString(logDirectory.resolve("shared-system_talker_aliases.csv")));
    }

    @Test
    void separatesAliasFilesByProtocol(@TempDir Path logDirectory) throws IOException
    {
        TalkerAliasLogger p25Logger = new TalkerAliasLogger(logDirectory, "shared-name", Protocol.APCO25);
        TalkerAliasLogger dmrLogger = new TalkerAliasLogger(logDirectory, "shared-name", Protocol.DMR);

        p25Logger.onAliasUpdate(Map.of(10, P25TalkerAliasIdentifier.create("P25 Alias")));
        dmrLogger.onAliasUpdate(Map.of(10, DmrTalkerAliasIdentifier.create("DMR Alias")));

        assertTrue(Files.readString(logDirectory.resolve("shared-name_talker_aliases.csv")).contains("P25 Alias"));
        assertTrue(Files.readString(logDirectory.resolve("shared-name_dmr_talker_aliases.csv")).contains("DMR Alias"));
    }

    @Test
    void bootstrapsDmrAliasesAsDmrIdentifiers(@TempDir Path logDirectory) throws IOException
    {
        Files.writeString(logDirectory.resolve("dmr-system_dmr_talker_aliases.csv"),
            "RADIO_ID,TALKER_ALIAS\n10,Dispatch\n");
        TalkerAliasLogger logger = new TalkerAliasLogger(logDirectory, "dmr-system", Protocol.DMR);
        TalkerAliasManager manager = new TalkerAliasManager();
        logger.bootstrap(manager);

        MutableIdentifierCollection identifiers = new MutableIdentifierCollection();
        identifiers.update(APCO25RadioIdentifier.createFrom(10));
        manager.enrichMutable(identifiers);
        Identifier alias = identifiers.getIdentifier(IdentifierClass.USER, Form.TALKER_ALIAS, Role.FROM);

        assertTrue(alias instanceof DmrTalkerAliasIdentifier);
        assertEquals(Protocol.DMR, alias.getProtocol());
    }
}
