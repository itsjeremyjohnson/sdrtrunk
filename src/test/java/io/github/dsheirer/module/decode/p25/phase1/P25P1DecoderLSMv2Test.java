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

import io.github.dsheirer.source.SourceEvent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class P25P1DecoderLSMv2Test
{
    @Test
    void carriersPresentAtStartupAreDetectedWithoutColdBoundaryReset()
    {
        P25P1DecoderLSMv2 lsmV2 = new P25P1DecoderLSMv2();
        assertEquals(-1, lsmV2.detectTransmissionBoundary(samples(3000, 0.10f), samples(3000, 0.0f)));
        assertTrue(lsmV2.isSignalPresent());
        assertEquals(0, lsmV2.getBoundaryResetCount());

        P25P1DecoderC4FM c4fm = new P25P1DecoderC4FM();
        assertEquals(-1, c4fm.detectTransmissionBoundary(samples(3000, 0.10f), samples(3000, 0.0f)));
        assertTrue(c4fm.isSignalPresent());
        assertEquals(0, c4fm.getBoundaryResetCount());

        P25P1DecoderC4FMv2 c4fmV2 = new P25P1DecoderC4FMv2();
        assertEquals(-1, c4fmV2.detectTransmissionBoundary(samples(3000, 0.10f), samples(3000, 0.0f)));
        assertTrue(c4fmV2.isSignalPresent());
        assertEquals(0, c4fmV2.getBoundaryResetCount());
    }

    @Test
    void frequencyChangesResetTransmissionDetectionAcrossEnergyProviders()
    {
        P25P1DecoderLSMv2 lsmV2 = new P25P1DecoderLSMv2();
        lsmV2.detectTransmissionBoundary(samples(3000, 1.0f), samples(3000, 0.0f));
        lsmV2.getSourceEventListener().receive(SourceEvent.frequencyChange(null, 155250000));
        assertTrue(lsmV2.getMessageFramer().isInitialAcquisitionActive());
        assertEquals(-1, lsmV2.detectTransmissionBoundary(samples(3000, 0.04f), samples(3000, 0.0f)));
        assertTrue(lsmV2.isSignalPresent());

        P25P1DecoderC4FM c4fm = new P25P1DecoderC4FM();
        c4fm.detectTransmissionBoundary(samples(3000, 1.0f), samples(3000, 0.0f));
        c4fm.getSourceEventListener().receive(SourceEvent.frequencyChange(null, 155250000));
        assertTrue(c4fm.getMessageFramer().isInitialAcquisitionActive());
        assertEquals(-1, c4fm.detectTransmissionBoundary(samples(3000, 0.04f), samples(3000, 0.0f)));
        assertTrue(c4fm.isSignalPresent());

        P25P1DecoderC4FMv2 c4fmV2 = new P25P1DecoderC4FMv2();
        c4fmV2.detectTransmissionBoundary(samples(3000, 1.0f), samples(3000, 0.0f));
        c4fmV2.getSourceEventListener().receive(SourceEvent.frequencyChange(null, 155250000));
        assertTrue(c4fmV2.getMessageFramer().isInitialAcquisitionActive());
        assertEquals(-1, c4fmV2.detectTransmissionBoundary(samples(3000, 0.04f), samples(3000, 0.0f)));
        assertTrue(c4fmV2.isSignalPresent());
    }

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
    void partialCmaOverrideSurvivesBoundaryReset()
    {
        String previousAcquisitionMu = System.getProperty("cma.acq.mu");
        String previousTrackingMu = System.getProperty("cma.trk.mu");
        String previousShiftMs = System.getProperty("cma.shift.ms");

        try
        {
            System.setProperty("cma.acq.mu", "0.004");
            System.setProperty("cma.trk.mu", "0.001");
            System.setProperty("cma.shift.ms", "200");
            P25P1DecoderLSMv2 decoder = new P25P1DecoderLSMv2();

            decoder.setCMAConfig(0.006f, 0.0f, 0);
            decoder.getEqualizer().reset();

            assertEquals(0.006f, decoder.getEqualizer().getMu());
            assertEquals(0.006f, decoder.getEqualizer().getAcquisitionMu());
            assertEquals(0.001f, decoder.getEqualizer().getTrackingMu());
            assertEquals(5000, decoder.getEqualizer().getGearShiftSamples());
        }
        finally
        {
            restoreProperty("cma.acq.mu", previousAcquisitionMu);
            restoreProperty("cma.trk.mu", previousTrackingMu);
            restoreProperty("cma.shift.ms", previousShiftMs);
        }
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

    private void restoreProperty(String name, String value)
    {
        if(value == null)
        {
            System.clearProperty(name);
        }
        else
        {
            System.setProperty(name, value);
        }
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
