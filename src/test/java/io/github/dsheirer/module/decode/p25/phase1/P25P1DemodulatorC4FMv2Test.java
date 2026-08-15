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
}
