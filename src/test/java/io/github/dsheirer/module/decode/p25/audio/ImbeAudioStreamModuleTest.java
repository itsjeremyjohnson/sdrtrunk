/*
 * *****************************************************************************
 * Copyright (C) 2026 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * ****************************************************************************
 */
package io.github.dsheirer.module.decode.p25.audio;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ImbeAudioStreamModuleTest
{
    @Test
    void callStartUsesSourceLduTimestamp()
    {
        long timestamp = 1_700_000_000_000L;
        String expected = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        String json = ImbeAudioStreamModule.createCallStartJson(
            "call-1", "System", "1001", "1234", timestamp);

        assertTrue(json.contains("\"timestamp\":\"" + expected + "\""));
    }
}
