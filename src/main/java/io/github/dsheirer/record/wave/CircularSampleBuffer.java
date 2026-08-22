/*
 * *****************************************************************************
 * Copyright (C) 2014-2025 Dennis Sheirer
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
package io.github.dsheirer.record.wave;

import io.github.dsheirer.sample.complex.ComplexSamples;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Duration-bounded buffer of ComplexSamples references used for activity-recording
 * pre-trigger audio. Capacity is in samples, not buffer count, so the retained
 * window stays at the configured duration regardless of channelizer chunk size.
 */
public class CircularSampleBuffer
{
    private final Deque<ComplexSamples> mBuffer = new ArrayDeque<>();
    private final long mMaximumSampleCount;
    private long mSampleCount;

    /**
     * Constructs an instance with the specified sample capacity.
     * @param maximumSampleCount maximum number of complex samples to retain
     */
    public CircularSampleBuffer(long maximumSampleCount)
    {
        if(maximumSampleCount <= 0)
        {
            throw new IllegalArgumentException("Maximum sample count must be positive");
        }

        mMaximumSampleCount = maximumSampleCount;
    }

    /**
     * Adds samples and evicts oldest complete buffers until retained duration is
     * within the configured bound.
     */
    public void add(ComplexSamples samples)
    {
        mBuffer.addLast(samples);
        mSampleCount += samples.length();

        while(mBuffer.size() > 1 && mSampleCount > mMaximumSampleCount)
        {
            mSampleCount -= mBuffer.removeFirst().length();
        }
    }

    /**
     * Drains all buffered samples in chronological order and clears the buffer.
     * @return list of ComplexSamples in the order they were added (oldest first)
     */
    public List<ComplexSamples> drain()
    {
        List<ComplexSamples> result = new ArrayList<>(mBuffer);
        clear();
        return result;
    }

    /**
     * Clears the buffer without returning entries.
     */
    public void clear()
    {
        mBuffer.clear();
        mSampleCount = 0;
    }

    /**
     * @return current number of buffer entries
     */
    public int size()
    {
        return mBuffer.size();
    }

    /**
     * @return currently retained sample count
     */
    long getSampleCount()
    {
        return mSampleCount;
    }
}
