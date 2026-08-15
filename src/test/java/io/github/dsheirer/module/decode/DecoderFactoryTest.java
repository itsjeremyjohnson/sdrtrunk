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
package io.github.dsheirer.module.decode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecoderFactoryTest
{
    @Test
    void acceptsDistinctPortsAcrossAllEnabledStreams()
    {
        assertTrue(DecoderFactory.areStreamPortsUnique(true, 9500, 9501, true, 9503, true, 9502));
    }

    @Test
    void rejectsCollisionsAcrossEnabledStreamManagers()
    {
        assertFalse(DecoderFactory.areStreamPortsUnique(true, 9500, 9501, true, 9500, false, 9502));
        assertFalse(DecoderFactory.areStreamPortsUnique(true, 9500, 9501, false, 9503, true, 9501));
        assertFalse(DecoderFactory.areStreamPortsUnique(false, 9500, 9501, true, 9502, true, 9502));
    }

    @Test
    void ignoresPortsForDisabledStreams()
    {
        assertTrue(DecoderFactory.areStreamPortsUnique(true, 9500, 9501, false, 9500, false, 9501));
    }
}
