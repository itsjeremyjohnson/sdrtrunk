/*
 * *****************************************************************************
 * Copyright (C) 2026 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * *****************************************************************************
 */
package io.github.dsheirer.module.decode.p25.phase1;

import io.github.dsheirer.identifier.IdentifierCollection;
import io.github.dsheirer.module.decode.event.DecodeEvent;
import io.github.dsheirer.module.decode.event.DecodeEventType;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkEventBroadcastModuleTest
{
    @Test
    void usesEventTimestampAndIncludesTimeslot()
    {
        long timestamp = 1_700_000_000_000L;
        DecodeEvent event = new DecodeEvent(DecodeEventType.CALL, timestamp);
        event.setIdentifierCollection(new IdentifierCollection());
        event.setTimeslot(2);

        String json = new NetworkEventBroadcastModule("County P25", null).toJson(event);
        String expectedTimestamp = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
            .toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        assertTrue(json.contains("\"system\":\"County P25\""));
        assertTrue(json.contains("\"timestamp\":\"" + expectedTimestamp + "\""));
        assertTrue(json.contains("\"timeslot\":2"));
    }

    @Test
    void omitsTimeslotWhenEventHasNone()
    {
        DecodeEvent event = new DecodeEvent(DecodeEventType.CALL, 1_700_000_000_000L);
        event.setIdentifierCollection(new IdentifierCollection());

        String json = new NetworkEventBroadcastModule("System", null).toJson(event);

        assertFalse(json.contains("\"timeslot\""));
    }
}
