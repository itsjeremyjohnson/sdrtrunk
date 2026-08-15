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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DecodeQualityCompatibilityTest
{
    @Test
    void explicitZeroNacAndBchThresholdAreApplied()
    {
        ConfigurableDecoder decoder = new ConfigurableDecoder();
        ConfigurableFramer framer = new ConfigurableFramer();

        decoder.mFramer = framer;
        DecodeQualityTest.configureDecoder(decoder, 0, 3);

        assertEquals(0, decoder.mNac);
        assertEquals(3, framer.mMaxBchErrors);
    }

    @Test
    void duplicateBasenamesRetainRelativeDirectoryIdentity()
    {
        java.nio.file.Path root = java.nio.file.Path.of("samples").toAbsolutePath();
        assertEquals("north/call_baseband.wav", DecodeQualityTest.sampleIdentifier(root,
                root.resolve("north/call_baseband.wav")));
        assertEquals("south/call_baseband.wav", DecodeQualityTest.sampleIdentifier(root,
                root.resolve("south/call_baseband.wav")));
    }

    @Test
    void missingHistoricalTuningMethodIsIgnored()
    {
        HistoricalDecoder decoder = new HistoricalDecoder();

        assertDoesNotThrow(() -> DecodeQualityTest.configureDecoder(decoder, 0x293, 3));
        assertEquals(0, decoder.getCalls());
    }

    public static class ConfigurableDecoder
    {
        private int mNac = -1;
        private ConfigurableFramer mFramer;

        public void setConfiguredNAC(int nac)
        {
            mNac = nac;
        }

        public ConfigurableFramer getMessageFramer()
        {
            return mFramer;
        }
    }

    public static class ConfigurableFramer
    {
        private int mMaxBchErrors = -1;

        public void setMaxBchErrors(int maxBchErrors)
        {
            mMaxBchErrors = maxBchErrors;
        }
    }

    private static class HistoricalDecoder
    {
        private int mCalls;

        int getCalls()
        {
            return mCalls;
        }
    }
}
