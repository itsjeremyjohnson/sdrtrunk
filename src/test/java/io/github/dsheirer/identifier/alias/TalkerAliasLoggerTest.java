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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

        Path aliasFile = logDirectory.resolve(TalkerAliasLogger.getAliasFileName("test-system", Protocol.APCO25));
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
            Files.readString(logDirectory.resolve(TalkerAliasLogger.getAliasFileName("shared-system", Protocol.APCO25))));
    }

    @Test
    void keepsBootstrappedAliasesAsSharedBaseline(@TempDir Path logDirectory) throws IOException
    {
        Path aliasFile = logDirectory.resolve(TalkerAliasLogger.getAliasFileName("baseline-system", Protocol.APCO25));
        Files.writeString(aliasFile, "RADIO_ID,TALKER_ALIAS\n10,Original\n");
        TalkerAliasLogger firstLogger = new TalkerAliasLogger(logDirectory, "baseline-system");
        TalkerAliasLogger secondLogger = new TalkerAliasLogger(logDirectory, "baseline-system");
        firstLogger.bootstrap(new TalkerAliasManager());
        secondLogger.bootstrap(new TalkerAliasManager());

        firstLogger.onAliasUpdate(Map.of(10, P25TalkerAliasIdentifier.create("Updated")));
        secondLogger.onAliasUpdate(Map.of(
            10, P25TalkerAliasIdentifier.create("Original"),
            20, P25TalkerAliasIdentifier.create("New")));

        assertEquals("RADIO_ID,TALKER_ALIAS\n10,Updated\n20,New\n", Files.readString(aliasFile));
    }

    @Test
    void excludesAliasesInheritedFromLiveSnapshotsAfterRestart(@TempDir Path logDirectory) throws IOException
    {
        String systemName = "restart-system";
        Path aliasFile = logDirectory.resolve(TalkerAliasLogger.getAliasFileName(systemName, Protocol.APCO25));
        TalkerAliasLogger firstLogger = new TalkerAliasLogger(logDirectory, systemName);
        firstLogger.onAliasUpdate(Map.of(10, P25TalkerAliasIdentifier.create("Original")));

        TalkerAliasLogger restartedLogger = new TalkerAliasLogger(logDirectory, systemName);
        restartedLogger.bootstrap(new TalkerAliasManager());
        firstLogger.onAliasUpdate(Map.of(10, P25TalkerAliasIdentifier.create("Updated")));
        restartedLogger.onAliasUpdate(Map.of(
            10, P25TalkerAliasIdentifier.create("Original"),
            20, P25TalkerAliasIdentifier.create("New")));

        assertEquals("RADIO_ID,TALKER_ALIAS\n10,Updated\n20,New\n", Files.readString(aliasFile));
    }

    @Test
    void releasesSourceSnapshotWhilePreservingAliases(@TempDir Path logDirectory) throws IOException
    {
        String systemName = "dispose-system";
        Path aliasFile = logDirectory.resolve(TalkerAliasLogger.getAliasFileName(systemName, Protocol.APCO25));
        TalkerAliasLogger firstLogger = new TalkerAliasLogger(logDirectory, systemName);
        firstLogger.onAliasUpdate(Map.of(10, P25TalkerAliasIdentifier.create("First")));
        firstLogger.dispose();

        TalkerAliasLogger secondLogger = new TalkerAliasLogger(logDirectory, systemName);
        secondLogger.onAliasUpdate(Map.of(20, P25TalkerAliasIdentifier.create("Second")));

        assertEquals("RADIO_ID,TALKER_ALIAS\n10,First\n20,Second\n", Files.readString(aliasFile));
    }

    @Test
    void separatesSystemIdentitiesThatSanitizeToTheSameName(@TempDir Path logDirectory) throws IOException
    {
        TalkerAliasLogger spaced = new TalkerAliasLogger(logDirectory, "County P25");
        TalkerAliasLogger dashed = new TalkerAliasLogger(logDirectory, "County-P25");
        spaced.onAliasUpdate(Map.of(10, P25TalkerAliasIdentifier.create("Spaced")));
        dashed.onAliasUpdate(Map.of(10, P25TalkerAliasIdentifier.create("Dashed")));

        Path spacedFile = logDirectory.resolve(
            TalkerAliasLogger.getAliasFileName("County P25", Protocol.APCO25));
        Path dashedFile = logDirectory.resolve(
            TalkerAliasLogger.getAliasFileName("County-P25", Protocol.APCO25));
        assertTrue(Files.readString(spacedFile).contains("Spaced"));
        assertTrue(Files.readString(dashedFile).contains("Dashed"));
        assertNotEquals(spacedFile, dashedFile);
    }

    @Test
    void separatesAliasFilesByProtocol(@TempDir Path logDirectory) throws IOException
    {
        TalkerAliasLogger p25Logger = new TalkerAliasLogger(logDirectory, "shared-name", Protocol.APCO25);
        TalkerAliasLogger dmrLogger = new TalkerAliasLogger(logDirectory, "shared-name", Protocol.DMR);

        p25Logger.onAliasUpdate(Map.of(10, P25TalkerAliasIdentifier.create("P25 Alias")));
        dmrLogger.onAliasUpdate(Map.of(10, DmrTalkerAliasIdentifier.create("DMR Alias")));

        assertTrue(Files.readString(logDirectory.resolve(TalkerAliasLogger.getAliasFileName("shared-name", Protocol.APCO25))).contains("P25 Alias"));
        assertTrue(Files.readString(logDirectory.resolve(TalkerAliasLogger.getAliasFileName("shared-name", Protocol.DMR))).contains("DMR Alias"));
    }

    @Test
    void bootstrapsDmrAliasesAsDmrIdentifiers(@TempDir Path logDirectory) throws IOException
    {
        Files.writeString(logDirectory.resolve(TalkerAliasLogger.getAliasFileName("dmr-system", Protocol.DMR)),
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
