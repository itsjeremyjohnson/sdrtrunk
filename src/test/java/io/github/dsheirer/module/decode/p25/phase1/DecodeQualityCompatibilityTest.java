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

import java.io.StringReader;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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
    void audioDirectoryKeysDoNotCollideAfterPathSanitization()
    {
        assertEquals("beb14eab1dead80f6e8336d59a7d6c0de5aa9287021973d60faa65c05b88d7bd",
                DecodeQualityTest.audioDirectoryKey("a/b_baseband.wav"));
        assertNotEquals(DecodeQualityTest.audioDirectoryKey("a/b_baseband.wav"),
                DecodeQualityTest.audioDirectoryKey("a_b_baseband.wav"));
    }

    @Test
    void rotatingPlaylistMapsEveryChildFrequency() throws Exception
    {
        String xml = """
                <playlist>
                  <channel name="Rotating" system="System" site="Site">
                    <decode_configuration type="decodeConfigP25Phase1" modulation="CQPSK" configuredNAC="659"/>
                    <source_configuration type="sourceConfigTunerMultipleFrequency" preferred_tuner="Tuner A">
                      <frequency>851012500</frequency>
                      <frequency>852012500</frequency>
                    </source_configuration>
                  </channel>
                </playlist>
                """;
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));

        List<DecodeQualityTest.ChannelConfig> configs = DecodeQualityTest.parsePlaylist(document);

        assertEquals(2, configs.size());
        assertEquals("Rotating", DecodeQualityTest.findChannel(configs, 851012500).name());
        assertEquals("Rotating", DecodeQualityTest.findChannel(configs, 852012500).name());
    }

    @Test
    void codecResetBoundaryUsesConfiguredTransmissionGap()
    {
        assertEquals(false, DecodeQualityTest.isSegmentBoundary(1000, 1500, 500));
        assertEquals(true, DecodeQualityTest.isSegmentBoundary(1000, 1501, 500));
        assertEquals(false, DecodeQualityTest.isSegmentBoundary(0, 5000, 500));
        assertEquals(true, DecodeQualityTest.startsAudioSegment(true, 1000, 1100, 500));
        assertEquals(false, DecodeQualityTest.startsAudioSegment(false, 1000, 1100, 500));
    }

    @Test
    void zeroDisablesImbeQualityGate()
    {
        assertEquals(false, DecodeQualityTest.shouldGateImbeFrames(-1));
        assertEquals(false, DecodeQualityTest.shouldGateImbeFrames(0));
        assertEquals(true, DecodeQualityTest.shouldGateImbeFrames(1));
    }

    @Test
    void partialWaveReadDropsUnusedAndIncompleteFrames()
    {
        byte[] buffer = new byte[16];
        assertEquals(8, TestComplexWaveSource.frameAlignedBytes(buffer, 10, 4).length);
        assertEquals(16, TestComplexWaveSource.frameAlignedBytes(buffer, 16, 4).length);
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
