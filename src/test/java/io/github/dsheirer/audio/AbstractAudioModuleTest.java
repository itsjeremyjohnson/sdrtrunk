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

import io.github.dsheirer.module.decode.p25.identifier.radio.APCO25RadioIdentifier;
import io.github.dsheirer.module.decode.p25.identifier.talkgroup.APCO25Talkgroup;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbstractAudioModuleTest
{
    @Test
    void preservesPcmCallAcrossInternalAudioSegmentRollover() throws Exception
    {
        TestAudioModule module = new TestAudioModule(1);
        module.addAudio(new float[8]);

        Field callIdField = AbstractAudioModule.class.getDeclaredField("mPcmCallId");
        callIdField.setAccessible(true);
        callIdField.set(module, "active-call");

        module.addAudio(new float[8]);

        assertEquals("active-call", callIdField.get(module));
        module.stop();
    }

    @Test
    void retainsFirstDecodedFrameTimestampWhilePcmServerStarts() throws Exception
    {
        TestAudioModule module = new TestAudioModule();
        module.addTimestampedAudio(new float[8], 1_700_000_000_123L);
        module.addTimestampedAudio(new float[8], 1_700_000_001_456L);

        Field timestampField = AbstractAudioModule.class.getDeclaredField("mPcmFirstFrameTimestamp");
        timestampField.setAccessible(true);
        assertEquals(1_700_000_000_123L, timestampField.getLong(module));
        module.stop();
    }

    @Test
    void refreshesPcmMetadataWhenIdentifiersArriveLate() throws Exception
    {
        TestAudioModule module = new TestAudioModule();
        module.addAudio(new float[8]);
        module.getIdentifierCollection().update(APCO25Talkgroup.create(1001));
        module.getIdentifierCollection().update(APCO25RadioIdentifier.createFrom(1234));
        module.addAudio(new float[8]);

        Field talkgroupField = AbstractAudioModule.class.getDeclaredField("mPcmCachedTalkgroup");
        talkgroupField.setAccessible(true);
        Field fromField = AbstractAudioModule.class.getDeclaredField("mPcmCachedFrom");
        fromField.setAccessible(true);
        assertEquals("1001", talkgroupField.get(module));
        assertEquals("1234", fromField.get(module));
        module.stop();
    }

    private static class TestAudioModule extends AbstractAudioModule
    {
        private TestAudioModule()
        {
            this(1_000);
        }

        private TestAudioModule(int maximumAudioSegmentLengthMilliseconds)
        {
            super(null, DEFAULT_TIMESLOT, maximumAudioSegmentLengthMilliseconds);
        }

        private void addTimestampedAudio(float[] audio, long timestamp)
        {
            addAudio(audio, timestamp);
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
