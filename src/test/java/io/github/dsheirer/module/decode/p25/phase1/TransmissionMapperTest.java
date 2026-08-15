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

import io.github.dsheirer.sample.complex.ComplexSamples;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransmissionMapperTest
{
    private static final int SAMPLE_RATE = 48_000;
    private static final float NOISE_AMPLITUDE = 0.01f;
    private static final float SIGNAL_AMPLITUDE = 1.0f;

    @Test
    void runningVarianceUsesWelfordM2()
    {
        TransmissionMapper.RunningVariance variance = new TransmissionMapper.RunningVariance();

        variance.add(0);
        variance.add(2);

        assertEquals(1.0, variance.populationStandardDeviation(), 0.000001);
    }

    @Test
    void receiverNoiseDoesNotOpenTransmission()
    {
        ComplexSamples samples = constantSamples(500, NOISE_AMPLITUDE);

        List<Transmission> transmissions = new TransmissionMapper().mapSamplesForTest(samples, SAMPLE_RATE);

        assertTrue(transmissions.isEmpty());
    }

    @Test
    void sustainedCarrierAfterLeadingNoiseOpensOneTransmission()
    {
        ComplexSamples samples = concatenatedSamples(
                new Segment(200, NOISE_AMPLITUDE),
                new Segment(300, SIGNAL_AMPLITUDE),
                new Segment(100, NOISE_AMPLITUDE));

        List<Transmission> transmissions = new TransmissionMapper().mapSamplesForTest(samples, SAMPLE_RATE);

        assertEquals(1, transmissions.size());
        Transmission transmission = transmissions.getFirst();
        assertTrue(transmission.startMs() >= 200);
        assertTrue(transmission.startMs() < 220);
        assertTrue(transmission.endMs() > 500);
        assertTrue(transmission.isComplete());
    }

    private ComplexSamples constantSamples(int durationMs, float amplitude)
    {
        return concatenatedSamples(new Segment(durationMs, amplitude));
    }

    private ComplexSamples concatenatedSamples(Segment... segments)
    {
        int sampleCount = 0;

        for(Segment segment : segments)
        {
            sampleCount += samplesForMs(segment.durationMs());
        }

        float[] i = new float[sampleCount];
        float[] q = new float[sampleCount];
        int offset = 0;

        for(Segment segment : segments)
        {
            int length = samplesForMs(segment.durationMs());
            for(int x = 0; x < length; x++)
            {
                i[offset + x] = segment.amplitude();
            }
            offset += length;
        }

        return new ComplexSamples(i, q, 0);
    }

    private int samplesForMs(int durationMs)
    {
        return SAMPLE_RATE * durationMs / 1000;
    }

    private record Segment(int durationMs, float amplitude) {}
}
