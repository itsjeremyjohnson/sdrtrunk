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

import io.github.dsheirer.audio.AudioSegment;
import java.io.File;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void duplicateFrequencyUsesFilenameChannelMetadata()
    {
        List<DecodeQualityTest.ChannelConfig> channels = List.of(
                new DecodeQualityTest.ChannelConfig("Channel One", "System", "North", 155000000,
                        "C4FM", 1, "N/A"),
                new DecodeQualityTest.ChannelConfig("Channel Two", "System", "South", 155000000,
                        "CQPSK", 2, "N/A"));

        DecodeQualityTest.ChannelConfig match = DecodeQualityTest.findChannel(channels, 155000000,
                "20260815_120000_155000000_System_South_Channel-Two_1_baseband.wav");

        assertEquals("Channel Two", match.name());
        assertEquals("CQPSK", match.modulation());
        assertEquals(2, match.nac());
    }

    @Test
    void duplicateFrequencyUsesActivityRecordingParentMetadata()
    {
        List<DecodeQualityTest.ChannelConfig> channels = List.of(
                new DecodeQualityTest.ChannelConfig("Channel One", "System", "North", 155000000,
                        "C4FM", 1, "N/A"),
                new DecodeQualityTest.ChannelConfig("Channel Two", "System", "South", 155000000,
                        "CQPSK", 2, "N/A"));
        File activityRecording = new File("System_South_Channel-Two_1",
                "20260815_120000_155000000_12345678_baseband.wav");

        DecodeQualityTest.ChannelConfig match = DecodeQualityTest.findChannel(channels, 155000000,
                activityRecording);

        assertEquals("Channel Two", match.name());
        assertEquals("CQPSK", match.modulation());
        assertEquals(2, match.nac());
    }

    @Test
    void scoringJsonUsesLocaleIndependentDecimals()
    {
        Locale original = Locale.getDefault();
        try
        {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("1.25", DecodeQualityTest.formatJson("%.2f", 1.25));
        }
        finally
        {
            Locale.setDefault(original);
        }
    }

    @Test
    void continuousReplayUsesSampleTimeForPacing()
    {
        assertEquals(50_000_000L, PipelineReplayTest.replayDurationNanos(2500, 50_000.0));
    }

    @Test
    void continuousReplayMeasuresAudioAddedToExistingSegment()
    {
        AudioSegment segment = new AudioSegment(null, 0);
        segment.addAudio(new float[800]);
        long durationBefore = PipelineReplayTest.totalAudioDurationMillis(List.of(segment));

        segment.addAudio(new float[160]);

        assertEquals(20, PipelineReplayTest.totalAudioDurationMillis(List.of(segment)) - durationBefore);
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
    void fullScoringSuppressesOnlyInactiveSignalDuidCorrections()
    {
        assertEquals(true, DecodeQualityTest.shouldSuppressCorrectedLdu(true, false));
        assertEquals(false, DecodeQualityTest.shouldSuppressCorrectedLdu(true, true));
        assertEquals(false, DecodeQualityTest.shouldSuppressCorrectedLdu(false, false));
    }

    @Test
    void zeroDisablesImbeQualityGate()
    {
        assertEquals(false, DecodeQualityTest.shouldGateImbeFrames(-1));
        assertEquals(false, DecodeQualityTest.shouldGateImbeFrames(0));
        assertEquals(true, DecodeQualityTest.shouldGateImbeFrames(1));
    }

    @Test
    void mp3WriteFailuresPropagate()
    {
        assertThrows(IllegalStateException.class,
                () -> DecodeQualityTest.writeMP3(List.of(), Path.of("build")));
    }

    @Test
    void scoringDecodeFailuresPropagate()
    {
        File missing = new File("build/nonexistent-decode-quality-sample.wav");
        assertThrows(IllegalStateException.class,
                () -> DecodeQualityTest.runDecode(missing, "C4FM", -1, false, 5, 0, 0, 0));
        TestJmbeCodecLoader codec = new TestJmbeCodecLoader(null);
        assertThrows(IllegalStateException.class,
                () -> DecodeQualityTest.decodeAudio(missing, "C4FM", -1, codec, Path.of("build"),
                        5, 0, 500, 0.01f, 200, 0, 0, 0));
    }

    @Test
    void fireDepartmentChannelRecognitionUsesFdTokenBoundaries()
    {
        assertEquals(true, DecodeQualityTest.isFireDepartmentChannel("FD Dispatch"));
        assertEquals(true, DecodeQualityTest.isFireDepartmentChannel("FD"));
        assertEquals(true, DecodeQualityTest.isFireDepartmentChannel("Londonderry-FD"));
        assertEquals(true, DecodeQualityTest.isFireDepartmentChannel("County Fire"));
        assertEquals(false, DecodeQualityTest.isFireDepartmentChannel("FDR Dispatch"));
    }

    @Test
    void audioRequiresEstablishedClearEncryptionState()
    {
        assertEquals(false, DecodeQualityTest.shouldDecodeAudio(false, false));
        assertEquals(false, DecodeQualityTest.shouldDecodeAudio(false, true));
        assertEquals(false, DecodeQualityTest.shouldDecodeAudio(true, true));
        assertEquals(true, DecodeQualityTest.shouldDecodeAudio(true, false));
    }

    @Test
    void fullScoringRequiresConsecutiveEncryptedLdu2Confirmation()
    {
        DecodeQualityTest.ScoringEncryptionState state = new DecodeQualityTest.ScoringEncryptionState(2);
        state.updateFromHdu(false);

        state.updateFromLdu2(true);
        assertEquals(true, state.shouldDecodeAudio());

        state.updateFromLdu2(true);
        assertEquals(false, state.shouldDecodeAudio());

        state.updateFromLdu2(false);
        assertEquals(true, state.shouldDecodeAudio());
    }

    @Test
    void historicalCorrectionFlagsDefaultToFalse()
    {
        assertEquals(false, DecodeQualityTest.optionalBooleanFlag(new HistoricalLdu(), "isDuidCorrected"));
        assertEquals(false, DecodeQualityTest.optionalBooleanFlag(new HistoricalLdu(),
                "isDuidCorrectedDuringActiveSignal"));
        assertEquals(true, DecodeQualityTest.optionalBooleanFlag(new CurrentLduFlags(), "isDuidCorrected"));
    }

    @Test
    void fullScoringAlwaysGatesUncorrectableCodewords()
    {
        io.github.dsheirer.module.decode.p25.phase1.message.ldu.IMBEFrameDiagnostic.FrameErrors uncorrectable =
                new io.github.dsheirer.module.decode.p25.phase1.message.ldu.IMBEFrameDiagnostic.FrameErrors(
                        new int[]{4, 0, 0, 0, 0, 0, 0}, 4, 1, true);

        assertEquals(true, DecodeQualityTest.shouldGateScoringFrame(uncorrectable, 4, false));
        assertEquals(true, DecodeQualityTest.shouldGateScoringFrame(uncorrectable, 10, true));
    }

    @Test
    void unresolvedLdusReplayWhenClearStateIsEstablished()
    {
        List<Integer> cached = new java.util.ArrayList<>();
        List<Integer> decoded = new java.util.ArrayList<>();
        DecodeQualityTest.cacheWhileEncryptionUnknown(cached, 1, 4);
        DecodeQualityTest.cacheWhileEncryptionUnknown(cached, 2, 4);

        DecodeQualityTest.replayCached(cached, decoded::add);
        decoded.add(3);

        assertEquals(List.of(1, 2, 3), decoded);
        assertEquals(List.of(), cached);
    }

    @Test
    void unresolvedLduCacheUsesProductionBound()
    {
        List<Integer> cached = new java.util.ArrayList<>();
        for(int value = 1; value <= 4; value++)
        {
            DecodeQualityTest.cacheWhileEncryptionUnknown(cached, value, 4);
        }

        assertEquals(List.of(), cached);
    }

    @Test
    void fullScoringWaitsForEncryptionStateBeforeDecoding()
    {
        DecodeQualityTest.ScoringEncryptionState state = new DecodeQualityTest.ScoringEncryptionState(2);

        state.updateFromLdu2(true);
        assertEquals(false, state.shouldDecodeAudio());

        state.updateFromLdu2(false);
        assertEquals(true, state.shouldDecodeAudio());
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

    public static class HistoricalLdu
    {
    }

    public static class CurrentLduFlags
    {
        public boolean isDuidCorrected()
        {
            return true;
        }
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
