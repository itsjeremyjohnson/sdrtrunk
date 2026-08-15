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
import static org.junit.jupiter.api.Assertions.assertTrue;

class P25P1DecoderLSMv2Test
{
    @Test
    void idleNoiseRemainsSilenceUntilEnergyRisesAboveTheLearnedFloor()
    {
        P25P1DecoderLSMv2 decoder = new P25P1DecoderLSMv2();
        decoder.detectTransmissionBoundary(samples(3000, 0.01f), samples(3000, 0.0f));

        assertEquals(0, decoder.getBoundaryResetCount());

        int boundary = decoder.detectTransmissionBoundary(samples(1000, 0.10f), samples(1000, 0.0f));
        assertEquals(1, decoder.getBoundaryResetCount());
        assertTrue(boundary >= 0);
    }

    @Test
    void c4fmIdleNoiseRemainsSilenceUntilEnergyRisesAboveTheLearnedFloor()
    {
        P25P1DecoderC4FM decoder = new P25P1DecoderC4FM();
        decoder.detectTransmissionBoundary(samples(3000, 0.01f), samples(3000, 0.0f));

        assertEquals(0, decoder.getBoundaryResetCount());

        int boundary = decoder.detectTransmissionBoundary(samples(1000, 0.10f), samples(1000, 0.0f));
        assertEquals(1, decoder.getBoundaryResetCount());
        assertTrue(boundary >= 0);
    }

    @Test
    void c4fmV2IdleNoiseRemainsSilenceUntilEnergyRisesAboveTheLearnedFloor()
    {
        P25P1DecoderC4FMv2 decoder = new P25P1DecoderC4FMv2();
        decoder.detectTransmissionBoundary(samples(3000, 0.01f), samples(3000, 0.0f));

        assertEquals(0, decoder.getBoundaryResetCount());

        int boundary = decoder.detectTransmissionBoundary(samples(1000, 0.10f), samples(1000, 0.0f));
        assertEquals(1, decoder.getBoundaryResetCount());
        assertTrue(boundary >= 0);
    }

    @Test
    void weakerCarrierRemainsPresentAfterLsmBoundaryReset()
    {
        P25P1DecoderLSMv2 decoder = new P25P1DecoderLSMv2();
        assertWeakerCarrierRemainsPresent(decoder::detectTransmissionBoundary, decoder::isSignalPresent);
    }

    @Test
    void weakerCarrierRemainsPresentAfterC4fmBoundaryReset()
    {
        P25P1DecoderC4FM decoder = new P25P1DecoderC4FM();
        assertWeakerCarrierRemainsPresent(decoder::detectTransmissionBoundary, decoder::isSignalPresent);
    }

    @Test
    void weakerCarrierRemainsPresentAfterC4fmV2BoundaryReset()
    {
        P25P1DecoderC4FMv2 decoder = new P25P1DecoderC4FMv2();
        assertWeakerCarrierRemainsPresent(decoder::detectTransmissionBoundary, decoder::isSignalPresent);
    }

    private void assertWeakerCarrierRemainsPresent(BoundaryDetector detector, SignalPresence signalPresence)
    {
        detector.detect(samples(4000, 0.001f), samples(4000, 0.0f));
        detector.detect(samples(4000, 1.0f), samples(4000, 0.0f));
        detector.detect(samples(20000, 0.001f), samples(20000, 0.0f));
        assertTrue(detector.detect(samples(4000, 0.10f), samples(4000, 0.0f)) >= 0);
        detector.detect(samples(20000, 0.10f), samples(20000, 0.0f));

        assertTrue(signalPresence.isPresent());
    }

    private float[] samples(int length, float value)
    {
        float[] samples = new float[length];
        Arrays.fill(samples, value);
        return samples;
    }

    private interface BoundaryDetector
    {
        int detect(float[] i, float[] q);
    }

    private interface SignalPresence
    {
        boolean isPresent();
    }
}
