/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AbstractAudioModuleTest
{
    @Test
    void resumedAudioReschedulesPendingHangtimeClose() throws InterruptedException
    {
        TestAudioModule module = new TestAudioModule();
        module.setAudioHangtimeMs(120);
        module.addAudio(new float[160]);
        AudioSegment segment = module.getAudioSegment();
        module.requestClose();

        Thread.sleep(60);
        module.addAudio(new float[160]);
        Thread.sleep(80);

        assertSame(segment, module.getAudioSegment());
        assertFalse(segment.completeProperty().get());
        assertEquals(2, segment.getAudioBufferCount());

        Thread.sleep(80);
        assertTrue(segment.completeProperty().get());
        module.stop();
    }

    private static class TestAudioModule extends AbstractAudioModule
    {
        TestAudioModule()
        {
            super(null);
        }

        void requestClose()
        {
            closeAudioSegment();
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
