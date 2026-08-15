/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.audio.broadcast.zello;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZelloConfigurationTest
{
    @Test
    void defaultsBothStreamGuardsToFiveHundredMilliseconds()
    {
        assertEquals(500, new ZelloConfiguration().getStreamGuardMs());
        assertEquals(500, new ZelloConsumerConfiguration().getStreamGuardMs());
    }
}
