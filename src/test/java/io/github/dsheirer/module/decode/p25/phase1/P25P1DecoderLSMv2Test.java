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

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class P25P1DecoderLSMv2Test
{
    @Test
    void idleNoiseRemainsSilenceUntilEnergyRisesAboveTheLearnedFloor()
    {
        P25P1DecoderLSMv2 decoder = new P25P1DecoderLSMv2();
        decoder.detectTransmissionBoundary(samples(3000, 0.01f), samples(3000, 0.0f));

        assertEquals(0, decoder.getBoundaryResetCount());

        decoder.detectTransmissionBoundary(samples(1000, 0.10f), samples(1000, 0.0f));
        assertEquals(1, decoder.getBoundaryResetCount());
    }

    private float[] samples(int length, float value)
    {
        float[] samples = new float[length];
        Arrays.fill(samples, value);
        return samples;
    }
}
