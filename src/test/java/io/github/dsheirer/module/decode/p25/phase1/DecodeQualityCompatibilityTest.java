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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFileFormat;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.InputSource;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void basebandDiscoveryRecognizesAndOrdersRolloverFiles()
    {
        assertTrue(DecodeQualityTest.isBasebandFileName("call_baseband.wav"));
        assertTrue(DecodeQualityTest.isBasebandFileName("call_baseband_2.wav"));
        assertTrue(DecodeQualityTest.isBasebandFileName("call_baseband_10.wav"));
        assertFalse(DecodeQualityTest.isBasebandFileName("call_baseband_1.wav"));
        assertFalse(DecodeQualityTest.isBasebandFileName("call.wav"));

        List<String> names = new java.util.ArrayList<>(List.of("call_baseband_10.wav", "call_baseband_2.wav",
                "call_baseband.wav"));
        names.sort(DecodeQualityTest::compareBasebandFileNames);
        assertEquals(List.of("call_baseband.wav", "call_baseband_2.wav", "call_baseband_10.wav"), names);
    }

    @Test
    void rolloverFilenameMetadataStripsNumberedBasebandSuffix()
    {
        long frequency = 851012500L;
        assertEquals("System_Site_Channel", DecodeQualityTest.extractChannelMetadata(
                "capture_851012500_System_Site_Channel_17_baseband_2.wav", null, frequency));
        assertEquals("System_Site_Channel", DecodeQualityTest.extractChannelMetadata(
                "capture_851012500_System_Site_Channel_17_baseband_10.wav", null, frequency));
    }

    @Test
    void segmentExtractorWritesFileRelativeSigned16BitPcm(@TempDir Path tempDir) throws Exception
    {
        Path source = tempDir.resolve("source.wav");
        Path output = tempDir.resolve("segment.wav");
        writeTestComplexWav(source, 1_000, 1_000);

        SegmentExtractor.extractSegment(source.toFile(), output, 900, 1_000);

        try(AudioInputStream stream = AudioSystem.getAudioInputStream(output.toFile()))
        {
            AudioFormat format = stream.getFormat();
            assertEquals(AudioFormat.Encoding.PCM_SIGNED, format.getEncoding());
            assertEquals(2, format.getChannels());
            assertEquals(16, format.getSampleSizeInBits());
            assertEquals(100, stream.getFrameLength());
            byte[] firstFrame = stream.readNBytes(4);
            short firstI = (short)((firstFrame[0] & 0xFF) | (firstFrame[1] << 8));
            assertEquals(900, firstI);
        }
        assertDoesNotThrow(() -> {
            try(TestComplexWaveSource ignored = new TestComplexWaveSource(output.toFile())) { }
        });
    }

    @Test
    void transmissionAnalyzerUsesFileRelativeReplayTimestamps(@TempDir Path tempDir) throws Exception
    {
        Path sourcePath = tempDir.resolve("timestamps.wav");
        writeTestComplexWav(sourcePath, 1_000, 300);
        TransmissionAnalyzer analyzer = new TransmissionAnalyzer(sourcePath.toFile());
        List<Long> timestamps = new java.util.ArrayList<>();

        try(TestComplexWaveSource source = analyzer.createFileRelativeSource())
        {
            source.setListener(buffer -> buffer.iterator().forEachRemaining(samples -> timestamps.add(samples.timestamp())));
            source.next(100);
            source.next(100);
        }

        assertEquals(List.of(0L, 100L), timestamps);
    }

    @Test
    void missedTransmissionResultsRetainTheirOwnSourceFile()
    {
        MissedTransmissionAnalyzer analyzer = new MissedTransmissionAnalyzer();
        File first = new File("first.wav");
        File second = new File("second.wav");
        Transmission transmission = new Transmission(1, 0, 200, 1.0f, 1.0f, 1.0f, 0.0f, true);
        TransmissionDecodeResult firstResult = new TransmissionDecodeResult(transmission, 0, 0, 0, 0,
                false, false, false, false);
        TransmissionDecodeResult secondResult = new TransmissionDecodeResult(transmission, 0, 0, 0, 0,
                false, false, false, false);

        analyzer.recordResult(first, firstResult);
        analyzer.recordResult(second, secondResult);

        assertEquals(first, analyzer.sourceFileFor(firstResult));
        assertEquals(second, analyzer.sourceFileFor(secondResult));
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
        assertEquals(DecodeConfigP25Phase1.MAX_BCH_ERRORS_DEFAULT, configs.getFirst().maxBchErrors());
    }

    @Test
    void scoringUsesPlaylistBchThresholdUnlessOverridden() throws Exception
    {
        String xml = """
                <playlist>
                  <channel name="Configured" system="System" site="Site">
                    <decode_configuration type="decodeConfigP25Phase1" modulation="C4FM" maxBchErrors="7"/>
                    <source_configuration type="sourceConfigTuner" frequency="155000000"/>
                  </channel>
                </playlist>
                """;
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));
        DecodeQualityTest.ChannelConfig config = DecodeQualityTest.parsePlaylist(document).getFirst();

        assertEquals(7, DecodeQualityTest.effectiveMaxBchErrors(-1, config));
        assertEquals(3, DecodeQualityTest.effectiveMaxBchErrors(3, config));
        assertEquals(DecodeConfigP25Phase1.MAX_BCH_ERRORS_DEFAULT,
                DecodeQualityTest.effectiveMaxBchErrors(-1, null));
    }

    @Test
    void scoringUsesPlaylistImbeEncryptionAndV2TuningUnlessOverridden() throws Exception
    {
        String xml = """
                <playlist>
                  <channel name="Configured" system="System" site="Site">
                    <decode_configuration type="decodeConfigP25Phase1" modulation="C4FM_V2"
                      maxImbeErrors="7" ignoreEncryptionState="true"
                      cmaAcquisitionMu="0.006" cmaTrackingMu="0.001" cmaGearShiftMs="200"
                      gardnerBandwidth="0.02" afcAlpha="0.03" adaptiveThresholds="true"
                      dfeEnabled="true" dfeMu="0.009"/>
                    <source_configuration type="sourceConfigTuner" frequency="155000000"/>
                  </channel>
                </playlist>
                """;
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));
        DecodeQualityTest.ChannelConfig config = DecodeQualityTest.parsePlaylist(document).getFirst();
        DecodeQualityTest.DecoderTuning configured = DecodeQualityTest.effectiveDecoderTuning(config, -1,
                Float.NaN, Float.NaN, -1);
        DecodeQualityTest.DecoderTuning overridden = DecodeQualityTest.effectiveDecoderTuning(config, 3,
                0.004f, 0.002f, 100);

        assertEquals(7, configured.maxImbeErrors());
        assertTrue(configured.ignoreEncryptionState());
        assertEquals(0.006f, configured.cmaAcquisitionMu());
        assertEquals(0.001f, configured.cmaTrackingMu());
        assertEquals(200, configured.cmaGearShiftMs());
        assertEquals(0.02f, configured.gardnerBandwidth());
        assertEquals(0.03f, configured.afcAlpha());
        assertTrue(configured.adaptiveThresholds());
        assertTrue(configured.dfeEnabled());
        assertEquals(0.009f, configured.dfeMu());
        assertEquals(3, overridden.maxImbeErrors());
        assertEquals(0.004f, overridden.cmaAcquisitionMu());
        assertEquals(0.002f, overridden.cmaTrackingMu());
        assertEquals(100, overridden.cmaGearShiftMs());
    }

    @Test
    void scoringEncryptionBypassStaysClearAndEstablished()
    {
        DecodeQualityTest.ScoringEncryptionState state = new DecodeQualityTest.ScoringEncryptionState(2, true);

        assertTrue(state.isEstablished());
        assertTrue(state.shouldDecodeAudio());
        state.updateFromHdu(true);
        state.updateFromLdu2(true);
        state.updateFromLdu2(true);
        assertTrue(state.shouldDecodeAudio());
        state.reset();
        assertTrue(state.isEstablished());
        assertTrue(state.shouldDecodeAudio());
    }

    @Test
    void scoringAppliesV2TuningToProductionDecoders()
    {
        DecodeQualityTest.DecoderTuning tuning = new DecodeQualityTest.DecoderTuning(0, false, 0.006f,
                0.001f, 200, 0.02f, 0.03f, true, true, 0.009f);
        P25P1DecoderLSMv2 lsm = new P25P1DecoderLSMv2();
        DecodeQualityTest.configureLsmV2Decoder(lsm, tuning);
        assertEquals(0.006f, lsm.getEqualizer().getAcquisitionMu());
        assertEquals(0.001f, lsm.getEqualizer().getTrackingMu());
        assertEquals(5000, lsm.getEqualizer().getGearShiftSamples());

        P25P1DecoderC4FMv2 c4fm = new P25P1DecoderC4FMv2();
        double defaultGardnerAlpha = c4fm.getDemodulator().getGardnerAlpha();
        DecodeQualityTest.configureC4fmV2Decoder(c4fm, tuning);
        assertNotEquals(defaultGardnerAlpha, c4fm.getDemodulator().getGardnerAlpha());
        assertEquals(0.03f, c4fm.getDemodulator().getAfcAlpha());
        assertTrue(c4fm.getDemodulator().isAdaptiveThresholdsEnabled());
        assertTrue(c4fm.getDemodulator().isDfeEnabled());
        assertEquals(0.009f, c4fm.getDemodulator().getDfeMu());
    }

    @Test
    void fullScoringIgnoresFalseCqpskTerminators()
    {
        assertFalse(DecodeQualityTest.isCallEndingTerminator("CQPSK", P25P1DataUnitID.TERMINATOR_DATA_UNIT));
        assertFalse(DecodeQualityTest.isCallEndingTerminator("CQPSK_V2",
                P25P1DataUnitID.TERMINATOR_DATA_UNIT_LINK_CONTROL));
        assertTrue(DecodeQualityTest.isCallEndingTerminator("C4FM", P25P1DataUnitID.TERMINATOR_DATA_UNIT));
        assertTrue(DecodeQualityTest.isCallEndingTerminator("C4FM_V2",
                P25P1DataUnitID.TERMINATOR_DATA_UNIT_LINK_CONTROL));
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
    void duplicateFrequencyPreservesUnderscoresInFilenameMetadata()
    {
        List<DecodeQualityTest.ChannelConfig> channels = List.of(
                new DecodeQualityTest.ChannelConfig("Dispatch_One", "County_System", "North_Site", 155000000,
                        "C4FM", 1, "N/A"),
                new DecodeQualityTest.ChannelConfig("Dispatch_Two", "County_System", "South_Site", 155000000,
                        "CQPSK", 2, "N/A"));

        DecodeQualityTest.ChannelConfig match = DecodeQualityTest.findChannel(channels, 155000000,
                "20260815_120000_155000000_County_System_South_Site_Dispatch_Two_17_baseband.wav");

        assertEquals("Dispatch_Two", match.name());
    }

    @Test
    void duplicateFrequencyPreservesUnderscoresInActivityParentMetadata()
    {
        List<DecodeQualityTest.ChannelConfig> channels = List.of(
                new DecodeQualityTest.ChannelConfig("Dispatch_One", "County_System", "North_Site", 155000000,
                        "C4FM", 1, "N/A"),
                new DecodeQualityTest.ChannelConfig("Dispatch_Two", "County_System", "South_Site", 155000000,
                        "CQPSK", 2, "N/A"));
        File activityRecording = new File("County_System_South_Site_Dispatch_Two_17",
                "20260815_120000_155000000_12345678_baseband.wav");

        DecodeQualityTest.ChannelConfig match = DecodeQualityTest.findChannel(channels, 155000000,
                activityRecording);

        assertEquals("Dispatch_Two", match.name());
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
    void scorerConcealmentAppliesGainAfterRawFrameFade()
    {
        float[] raw = {1.0f, 0.1f};

        float[] concealed = DecodeQualityTest.concealScoringFrame(raw, 0.5f);

        assertEquals(0.95f, concealed[0]);
        assertEquals(0.25f, concealed[1]);
        assertEquals(1.0f, raw[0]);
    }

    @Test
    void scoringImbeDiagnosticsAreOwnedByRevisionIndependentHarness()
    {
        DecodeQualityIMBEFrameDiagnostic.FrameErrors errors =
                DecodeQualityIMBEFrameDiagnostic.analyzeFrame(new byte[18]);

        assertTrue(errors.totalErrors() >= 0);
        assertEquals(7, errors.codewordErrors().length);
    }

    @Test
    void fullScoringAlwaysGatesUncorrectableCodewords()
    {
        DecodeQualityIMBEFrameDiagnostic.FrameErrors uncorrectable =
                new DecodeQualityIMBEFrameDiagnostic.FrameErrors(
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

    private static void writeTestComplexWav(Path path, int sampleRate, int frames) throws Exception
    {
        byte[] pcm = new byte[frames * 4];
        for(int frame = 0; frame < frames; frame++)
        {
            short i = (short)frame;
            short q = (short)-frame;
            int offset = frame * 4;
            pcm[offset] = (byte)i;
            pcm[offset + 1] = (byte)(i >>> 8);
            pcm[offset + 2] = (byte)q;
            pcm[offset + 3] = (byte)(q >>> 8);
        }

        AudioFormat format = new AudioFormat(sampleRate, 16, 2, true, false);
        try(AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(pcm), format, frames))
        {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, path.toFile());
        }
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
