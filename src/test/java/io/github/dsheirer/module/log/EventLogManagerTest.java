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
package io.github.dsheirer.module.log;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventLogManagerTest
{
    @Test
    void disambiguatesSystemNamesThatSanitizeToTheSamePrefix()
    {
        String spaced = EventLogManager.getSystemLoggerFilePrefix("County P25");
        String dashed = EventLogManager.getSystemLoggerFilePrefix("County-P25");

        assertTrue(spaced.startsWith("County-P25_"));
        assertTrue(dashed.startsWith("County-P25_"));
        assertNotEquals(spaced, dashed);
    }
}
