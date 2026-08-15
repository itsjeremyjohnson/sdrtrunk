/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.module.decode.p25.phase1;

import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class P25P1MessageFramerTest
{
    @Test
    void rejectedNacsDoNotTrainCorrectionTracker()
    {
        P25P1MessageFramer framer = new P25P1MessageFramer();
        framer.setAllowedNACs(Set.of(0x123));

        for(int x = 0; x < 5; x++)
        {
            framer.trackNACIfAllowed(0x456);
        }
        assertEquals(0, framer.getTrackedNAC());

        for(int x = 0; x < 3; x++)
        {
            framer.trackNACIfAllowed(0x123);
        }
        assertEquals(0x123, framer.getTrackedNAC());
    }
}
