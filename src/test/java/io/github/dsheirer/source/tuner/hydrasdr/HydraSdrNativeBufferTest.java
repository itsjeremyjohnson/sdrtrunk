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
    void preservesTimestampForResidualSamples()
    {
        HydraSdrNativeBufferFactory factory = new HydraSdrNativeBufferFactory(1_000);

        assertTrue(factory.get(new float[100], new float[100], 100, 1_000).isEmpty());

        List<HydraSdrNativeBuffer> first = factory.get(new float[100], new float[100], 100, 1_100);
        assertEquals(1, first.size());
        assertEquals(1_000, first.getFirst().getTimestamp());

        List<HydraSdrNativeBuffer> second = factory.get(new float[56], new float[56], 56, 1_200);
        assertEquals(1, second.size());
        assertEquals(1_128, second.getFirst().getTimestamp());
    }

    @Test
    void preservesBufferSizeUntilResidualSamplesAreEmitted()
    {
        HydraSdrNativeBufferFactory factory = new HydraSdrNativeBufferFactory(1_000);

        assertTrue(factory.get(new float[200], new float[200], 200, 1_000).isEmpty());
        assertTrue(factory.get(new float[40], new float[40], 40, 1_200).isEmpty());
        List<HydraSdrNativeBuffer> buffers = factory.get(new float[16], new float[16], 16, 1_240);

        assertEquals(1, buffers.size());
        assertEquals(256, buffers.getFirst().sampleCount());
        assertEquals(1_000, buffers.getFirst().getTimestamp());
    }

    @Test
    void sampleRateChangeDiscardsResidualSamplesAndTimestamp()
    {
        HydraSdrNativeBufferFactory factory = new HydraSdrNativeBufferFactory(1_000);

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
        HydraSdrNativeBufferFactory factory = new HydraSdrNativeBufferFactory(1_000);

        assertTrue(factory.get(new float[100], new float[100], 100, 1_000).isEmpty());
        factory.reset();

        assertTrue(factory.get(new float[100], new float[100], 100, 2_000).isEmpty());
        List<HydraSdrNativeBuffer> buffers = factory.get(new float[28], new float[28], 28, 2_100);

        assertEquals(1, buffers.size());
        assertEquals(2_000, buffers.getFirst().getTimestamp());
    }
}
