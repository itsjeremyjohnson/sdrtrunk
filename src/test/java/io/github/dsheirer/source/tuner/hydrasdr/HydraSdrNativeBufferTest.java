/*
 * *****************************************************************************
 * Copyright (C) 2026 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * ****************************************************************************
 */

package io.github.dsheirer.source.tuner.hydrasdr;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HydraSdrNativeBufferTest
{
    @Test
    void reportsComplexSampleCount()
    {
        HydraSdrNativeBuffer buffer = new HydraSdrNativeBuffer(new float[16], new float[16], 0, 1.0f);

        assertEquals(16, buffer.sampleCount());
    }

    @Test
    void defaultFactoryEmitsAdvertisedControllerBufferSize()
    {
        HydraSdrNativeBufferFactory factory = new HydraSdrNativeBufferFactory(1_000);
        int half = HydraSdrTunerController.BUFFER_SAMPLE_COUNT / 2;

        assertTrue(factory.get(new float[half], new float[half], half, 1_000).isEmpty());
        List<HydraSdrNativeBuffer> buffers = factory.get(new float[half], new float[half], half, 2_000);

        assertEquals(1, buffers.size());
        assertEquals(HydraSdrTunerController.BUFFER_SAMPLE_COUNT, buffers.getFirst().sampleCount());
    }

    @Test
    void preservesTimestampForResidualSamples()
    {
        HydraSdrNativeBufferFactory factory = new HydraSdrNativeBufferFactory(1_000, 128);

        assertTrue(factory.get(new float[100], new float[100], 100, 1_000).isEmpty());

        List<HydraSdrNativeBuffer> first = factory.get(new float[100], new float[100], 100, 1_100);
        assertEquals(1, first.size());
        assertEquals(1_000, first.getFirst().getTimestamp());

        List<HydraSdrNativeBuffer> second = factory.get(new float[56], new float[56], 56, 1_200);
        assertEquals(1, second.size());
        assertEquals(1_128, second.getFirst().getTimestamp());
    }

    @Test
    void emitsConfiguredBufferSizeAcrossVariableCallbacks()
    {
        HydraSdrNativeBufferFactory factory = new HydraSdrNativeBufferFactory(1_000, 256);

        assertTrue(factory.get(new float[200], new float[200], 200, 1_000).isEmpty());
        assertTrue(factory.get(new float[40], new float[40], 40, 1_200).isEmpty());
        List<HydraSdrNativeBuffer> buffers = factory.get(new float[16], new float[16], 16, 1_240);

        assertEquals(1, buffers.size());
        assertEquals(256, buffers.getFirst().sampleCount());
        assertEquals(1_000, buffers.getFirst().getTimestamp());
    }

    @Test
    void splitsLargeCallbackByOffsetAndRetainsFinalRemainder()
    {
        HydraSdrNativeBufferFactory factory = new HydraSdrNativeBufferFactory(1_000, 4);
        float[] samples = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};

        List<HydraSdrNativeBuffer> buffers = factory.get(samples, samples, samples.length, 1_000);

        assertEquals(2, buffers.size());
        assertArrayEquals(new float[] {0, 1, 2, 3}, buffers.get(0).iterator().next().i());
        assertArrayEquals(new float[] {4, 5, 6, 7}, buffers.get(1).iterator().next().i());

        List<HydraSdrNativeBuffer> remainder = factory.get(new float[] {10, 11}, new float[] {10, 11}, 2, 1_010);
        assertEquals(1, remainder.size());
        assertArrayEquals(new float[] {8, 9, 10, 11}, remainder.getFirst().iterator().next().i());
    }

    @Test
    void sampleRateChangeDiscardsResidualSamplesAndTimestamp()
    {
        HydraSdrNativeBufferFactory factory = new HydraSdrNativeBufferFactory(1_000, 128);

        assertTrue(factory.get(new float[100], new float[100], 100, 1_000).isEmpty());
        factory.setSampleRate(2_000);

        assertTrue(factory.get(new float[100], new float[100], 100, 2_000).isEmpty());
        List<HydraSdrNativeBuffer> buffers = factory.get(new float[28], new float[28], 28, 2_050);

        assertEquals(1, buffers.size());
        assertEquals(2_000, buffers.getFirst().getTimestamp());
    }

    @Test
    void resetDiscardsResidualSamplesAndTimestamp()
    {
        HydraSdrNativeBufferFactory factory = new HydraSdrNativeBufferFactory(1_000, 128);

        assertTrue(factory.get(new float[100], new float[100], 100, 1_000).isEmpty());
        factory.reset();

        assertTrue(factory.get(new float[100], new float[100], 100, 2_000).isEmpty());
        List<HydraSdrNativeBuffer> buffers = factory.get(new float[28], new float[28], 28, 2_100);

        assertEquals(1, buffers.size());
        assertEquals(2_000, buffers.getFirst().getTimestamp());
    }
}
