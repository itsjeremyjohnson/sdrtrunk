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
import static org.junit.jupiter.api.Assertions.assertTrue;

class P25P1DemodulatorC4FMv2Test
{
    @Test
    void enablesGardnerAndAfcLoopsForV2()
    {
        P25P1DemodulatorC4FMv2 demodulator =
                new P25P1DemodulatorC4FMv2(new P25P1MessageFramer(), null);

        assertTrue(demodulator.isGardnerEnabled());
        assertTrue(demodulator.isAfcEnabled());
    }

    @Test
    void boundsDuidCorrectionCycles()
    {
        P25P1DecoderC4FMv2 decoder = new P25P1DecoderC4FMv2();

        assertEquals(3, decoder.getMessageFramer().getMaxConsecutiveDuidCorrections());
    }

    @Test
    void configuredNacReachesBothC4fmDemodulators()
    {
        P25P1DecoderC4FM decoder = new P25P1DecoderC4FM();
        decoder.setConfiguredNAC(0x293);
        assertEquals(0x293, decoder.getDemodulator().getConfiguredNAC());

        P25P1DecoderC4FMv2 decoderV2 = new P25P1DecoderC4FMv2();
        decoderV2.setConfiguredNAC(0x293);
        assertEquals(0x293, decoderV2.getDemodulator().getConfiguredNAC());
    }
}
