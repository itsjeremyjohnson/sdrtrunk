/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.audio.broadcast;

import io.github.dsheirer.audio.broadcast.zello.ZelloConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfiguredBroadcastTest
{
    @Test
    void fallsBackToLastBadStateWithoutDetailedError()
    {
        ConfiguredBroadcast configured = new ConfiguredBroadcast(new ZelloConfiguration());
        configured.lastBadBroadcastStateProperty().set(BroadcastState.ERROR);
        assertEquals(BroadcastState.ERROR.toString(), configured.lastErrorDisplayProperty().get());

        configured.lastErrorDetailProperty().set("specific failure");
        assertEquals("specific failure", configured.lastErrorDisplayProperty().get());
    }
}
