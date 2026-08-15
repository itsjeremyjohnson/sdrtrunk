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
package io.github.dsheirer.module.discovery;

import io.github.dsheirer.buffer.INativeBuffer;
import io.github.dsheirer.sample.Listener;
import io.github.dsheirer.sample.complex.ComplexSamples;
import io.github.dsheirer.source.SourceException;
import io.github.dsheirer.source.tuner.TunerController;
import io.github.dsheirer.source.tuner.TunerType;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TunerControlImplTest
{
    @Test
    void removeWidebandSampleListener_usesControllerThatRegisteredAdapter()
    {
        RecordingTunerController first = new RecordingTunerController();
        RecordingTunerController second = new RecordingTunerController();
        AtomicReference<TunerController> current = new AtomicReference<>(first);
        TunerControlImpl tunerControl = new TunerControlImpl(current::get);
        Listener<ComplexSamples> listener = samples -> {};

        tunerControl.addWidebandSampleListener(listener);
        current.set(second);
        tunerControl.removeWidebandSampleListener(listener);

        assertEquals(1, first.mAddCount);
        assertEquals(1, first.mRemoveCount);
        assertSame(first.mAddedListener, first.mRemovedListener);
        assertEquals(0, second.mRemoveCount);
    }

    private static class RecordingTunerController extends TunerController
    {
        private int mAddCount;
        private int mRemoveCount;
        private Listener<INativeBuffer> mAddedListener;
        private Listener<INativeBuffer> mRemovedListener;

        RecordingTunerController()
        {
            super(null);
        }

        @Override
        public void addBufferListener(Listener<INativeBuffer> listener)
        {
            mAddCount++;
            mAddedListener = listener;
        }

        @Override
        public void removeBufferListener(Listener<INativeBuffer> listener)
        {
            mRemoveCount++;
            mRemovedListener = listener;
        }

        @Override
        public long getTunedFrequency()
        {
            return 100_000_000L;
        }

        @Override
        public void setTunedFrequency(long frequency)
        {
        }

        @Override
        public double getCurrentSampleRate()
        {
            return 1_000_000.0;
        }

        @Override
        public void start() throws SourceException
        {
        }

        @Override
        public void stop()
        {
        }

        @Override
        public TunerType getTunerType()
        {
            return null;
        }

        @Override
        public int getBufferSampleCount()
        {
            return 1;
        }
    }
}
