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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlChannelHeartbeatTest
{
    @Test
    void matchesSystemRfssAndSite()
    {
        ControlChannelHeartbeat heartbeat = new ControlChannelHeartbeat(291, 2, 7, "Site", "", "", 30);

        assertTrue(heartbeat.matches(291, 2, 7));
        assertFalse(heartbeat.matches(291, 1, 7));
        assertFalse(heartbeat.matches(291, 2, 8));
        assertFalse(heartbeat.matches(292, 2, 7));
    }
}
