/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * *****************************************************************************
 */
package io.github.dsheirer.module.decode.p25.phase1;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class P25P1MessageFramerTest
{
    @Test
    void preservesErrorFreeTerminatorAfterVoiceFrame()
    {
        P25P1MessageFramer framer = new P25P1MessageFramer();
        framer.setPreviousDataUnitID(P25P1DataUnitID.LOGICAL_LINK_DATA_UNIT_1);
        framer.setMaxConsecutiveDuidCorrections(3);

        framer.nidDetected(0x293, P25P1DataUnitID.TERMINATOR_DATA_UNIT, 0);

        assertEquals(0, framer.getDuidCorrectionCount());
    }

    @Test
    void correctsUncertainTerminatorDuringVoiceFrame()
    {
        P25P1MessageFramer framer = new P25P1MessageFramer();
        framer.setPreviousDataUnitID(P25P1DataUnitID.LOGICAL_LINK_DATA_UNIT_1);
        framer.setMaxConsecutiveDuidCorrections(3);

        framer.nidDetected(0x293, P25P1DataUnitID.TERMINATOR_DATA_UNIT, 8);

        assertEquals(1, framer.getDuidCorrectionCount());
    }
}
