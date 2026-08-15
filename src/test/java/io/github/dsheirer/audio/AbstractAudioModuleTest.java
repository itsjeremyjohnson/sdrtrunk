/*
 * *****************************************************************************
 * Copyright (C) 2026
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * *****************************************************************************
 */
package io.github.dsheirer.audio;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbstractAudioModuleTest
{
    @Test
    void preservesPcmCallAcrossInternalAudioSegmentRollover() throws Exception
    {
        TestAudioModule module = new TestAudioModule();
        module.addAudio(new float[8]);

        Field callIdField = AbstractAudioModule.class.getDeclaredField("mPcmCallId");
        callIdField.setAccessible(true);
        callIdField.set(module, "active-call");

        module.addAudio(new float[8]);

        assertEquals("active-call", callIdField.get(module));
        module.stop();
    }

    private static class TestAudioModule extends AbstractAudioModule
    {
        private TestAudioModule()
        {
            super(null, DEFAULT_TIMESLOT, 1);
        }

        @Override
        public void reset()
        {
        }

        @Override
        public void start()
        {
        }
    }
}
