package io.github.dsheirer.module.decode.p25.phase1;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.github.dsheirer.alias.AliasFactory;
import io.github.dsheirer.alias.AliasModel;
import io.github.dsheirer.alias.action.beep.BeepAction;
import io.github.dsheirer.alias.id.talkgroup.Talkgroup;
import io.github.dsheirer.controller.channel.Channel;
import io.github.dsheirer.controller.channel.map.ChannelMapModel;
import io.github.dsheirer.module.Module;
import io.github.dsheirer.module.decode.DecoderFactory;
import io.github.dsheirer.dsp.squelch.CTCSSFrequency;
import io.github.dsheirer.module.decode.config.DecodeConfiguration;
import io.github.dsheirer.module.decode.nbfm.DecodeConfigNBFM;
import io.github.dsheirer.preference.UserPreferences;
import io.github.dsheirer.protocol.Protocol;
import io.github.dsheirer.record.RecorderType;
import io.github.dsheirer.record.config.RecordConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that decode configuration round-trips through XML serialization without losing
 * unknown properties or enum values. This prevents config data loss when switching between
 * code branches (e.g. branch with C4FM_V2 → master → branch).
 */
public class ConfigRoundTripTest
{
    private XmlMapper createMapper()
    {
        JacksonXmlModule xmlModule = new JacksonXmlModule();
        xmlModule.setDefaultUseWrapper(false);
        XmlMapper mapper = new XmlMapper(xmlModule);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    @Test
    void testKnownModulationRoundTrip() throws Exception
    {
        XmlMapper mapper = createMapper();

        String xml = """
            <decode_configuration type="decodeConfigP25Phase1" modulation="CQPSK_V2"
                configuredNAC="659" />
            """;

        DecodeConfiguration config = mapper.readValue(xml, DecodeConfiguration.class);
        assertInstanceOf(DecodeConfigP25Phase1.class, config);

        DecodeConfigP25Phase1 p1 = (DecodeConfigP25Phase1)config;
        assertEquals(Modulation.CQPSK_V2, p1.getModulation());
        assertEquals("CQPSK_V2", p1.getModulationString());
        assertEquals(659, p1.getConfiguredNAC());

        // Round-trip
        String output = mapper.writeValueAsString(config);
        assertTrue(output.contains("CQPSK_V2"), "Modulation CQPSK_V2 should survive round-trip");
        assertTrue(output.contains("659"), "NAC 659 should survive round-trip");
    }

    @Test
    void testUnknownModulationPreserved() throws Exception
    {
        XmlMapper mapper = createMapper();

        // Simulate a future modulation value not defined in current enum
        String xml = """
            <decode_configuration type="decodeConfigP25Phase1" modulation="FUTURE_MOD_V3"
                configuredNAC="100" />
            """;

        DecodeConfiguration config = mapper.readValue(xml, DecodeConfiguration.class);
        DecodeConfigP25Phase1 p1 = (DecodeConfigP25Phase1)config;

        // Should fall back to C4FM for runtime
        assertEquals(Modulation.C4FM, p1.getModulation());
        // But preserve the raw string for re-serialization
        assertEquals("FUTURE_MOD_V3", p1.getModulationString());

        // Round-trip should preserve the unknown modulation string
        String output = mapper.writeValueAsString(config);
        assertTrue(output.contains("FUTURE_MOD_V3"),
            "Unknown modulation value must be preserved on round-trip, got: " + output);
    }

    @Test
    void testUnknownPropertiesPreserved() throws Exception
    {
        XmlMapper mapper = createMapper();

        String xml = """
            <decode_configuration type="decodeConfigP25Phase1" modulation="C4FM" />
            """;

        DecodeConfiguration config = mapper.readValue(xml, DecodeConfiguration.class);
        DecodeConfigP25Phase1 p1 = (DecodeConfigP25Phase1)config;

        // Simulate unknown properties that would come from a future branch
        p1.setUnknownProperty("futureField1", "hello");
        p1.setUnknownProperty("futureField2", "42");

        String output = mapper.writeValueAsString(config);
        assertTrue(output.contains("futureField1"), "Unknown property should survive round-trip");
        assertTrue(output.contains("hello"), "Unknown property value should survive round-trip");
        assertTrue(output.contains("futureField2"), "Second unknown property should survive");

        // Verify the unknown properties can be read back
        DecodeConfiguration reloaded = mapper.readValue(output, DecodeConfiguration.class);
        DecodeConfigP25Phase1 p1r = (DecodeConfigP25Phase1)reloaded;
        assertNotNull(p1r.getUnknownProperties());
        assertEquals("hello", p1r.getUnknownProperties().get("futureField1"));
    }

    @Test
    void unknownRecorderTypeIsOmittedAtRuntimeAndPreservedOnSave() throws Exception
    {
        String xml = """
            <record_configuration>
                <recorder>FUTURE_RECORDER</recorder>
                <recorder>BASEBAND</recorder>
            </record_configuration>
            """;

        RecordConfiguration config = createMapper().readValue(xml, RecordConfiguration.class);
        assertEquals(1, config.getRecorders().size());
        assertEquals(RecorderType.BASEBAND, config.getRecorders().getFirst());

        //Simulate saving the recorder editor, which replaces the supported selections.
        config.clearRecorders();
        config.addRecorder(RecorderType.DEMODULATED_BIT_STREAM);

        String output = createMapper().writeValueAsString(config);
        assertTrue(output.contains("FUTURE_RECORDER"));
        assertTrue(output.contains("DEMODULATED_BIT_STREAM"));
        assertFalse(output.contains("BASEBAND"));
    }

    @Test
    void channelCopyPreservesActivityRecordingSettings()
    {
        Channel original = new Channel("test");
        RecordConfiguration recordConfiguration = new RecordConfiguration();
        recordConfiguration.setRecorderValues(java.util.List.of("FUTURE_RECORDER", "BASEBAND"));
        recordConfiguration.setActivityTriggeredRecording(true);
        recordConfiguration.setActivitySquelchThreshold(-55.0f);
        original.setRecordConfiguration(recordConfiguration);

        RecordConfiguration copy = original.copyOf().getRecordConfiguration();
        assertEquals(java.util.List.of("FUTURE_RECORDER", "BASEBAND"), copy.getRecorderValues());
        assertTrue(copy.isActivityTriggeredRecording());
        assertEquals(-55.0f, copy.getActivitySquelchThreshold());
    }

    @Test
    void channelCopyPreservesUnknownConfigurationProperties()
    {
        Channel original = new Channel("test");
        original.setUnknownProperty("futureChannel", "channel-value");
        original.getAuxDecodeConfiguration().setUnknownProperty("futureAux", "aux-value");
        original.getDecodeConfiguration().setUnknownProperty("futureDecode", "decode-value");
        original.getEventLogConfiguration().setUnknownProperty("futureLog", "log-value");
        original.getRecordConfiguration().setUnknownProperty("futureRecord", "record-value");
        original.getSourceConfiguration().setUnknownProperty("futureSource", "source-value");

        Channel copy = original.copyOf();

        assertEquals("channel-value", copy.getUnknownProperties().get("futureChannel"));
        assertEquals("aux-value", copy.getAuxDecodeConfiguration().getUnknownProperties().get("futureAux"));
        assertEquals("decode-value", copy.getDecodeConfiguration().getUnknownProperties().get("futureDecode"));
        assertEquals("log-value", copy.getEventLogConfiguration().getUnknownProperties().get("futureLog"));
        assertEquals("record-value", copy.getRecordConfiguration().getUnknownProperties().get("futureRecord"));
        assertEquals("source-value", copy.getSourceConfiguration().getUnknownProperties().get("futureSource"));
    }

    @Test
    void aliasFactoryCopiesPreserveUnknownProperties()
    {
        Talkgroup originalID = new Talkgroup(Protocol.APCO25, 1234);
        originalID.setUnknownProperty("futureIdentifier", "identifier-value");
        Talkgroup copiedID = (Talkgroup)AliasFactory.copyOf(originalID);
        assertEquals("identifier-value", copiedID.getUnknownProperties().get("futureIdentifier"));
        assertNotSame(originalID.getUnknownProperties(), copiedID.getUnknownProperties());

        BeepAction originalAction = new BeepAction();
        originalAction.setUnknownProperty("futureAction", "action-value");
        BeepAction copiedAction = (BeepAction)AliasFactory.copyOf(originalAction);
        assertEquals("action-value", copiedAction.getUnknownProperties().get("futureAction"));
        assertNotSame(originalAction.getUnknownProperties(), copiedAction.getUnknownProperties());
    }

    @Test
    void decoderFactoryCopyPreservesNbfmCtcss()
    {
        DecodeConfigNBFM original = new DecodeConfigNBFM();
        original.setCTCSSFrequency(CTCSSFrequency.TONE_114_8);

        DecodeConfigNBFM copy = (DecodeConfigNBFM)DecoderFactory.copy(original);
        assertEquals(CTCSSFrequency.TONE_114_8, copy.getCTCSSFrequency());
    }

    @Test
    void absentCmaOverridesAndNacRemainUnset()
    {
        DecodeConfigP25Phase1 config = new DecodeConfigP25Phase1();
        assertEquals(0.0f, config.getCmaAcquisitionMu());
        assertEquals(0.0f, config.getCmaTrackingMu());
        assertEquals(0, config.getCmaGearShiftMs());
        assertEquals(DecodeConfigP25Phase1.NAC_AUTODETECT, config.getConfiguredNAC());
        assertFalse(config.hasConfiguredNAC());
    }

    @Test
    void zeroNacCanBeExplicitlyConfigured()
    {
        DecodeConfigP25Phase1 config = new DecodeConfigP25Phase1();
        config.setConfiguredNAC(0);
        assertTrue(config.hasConfiguredNAC());
        assertEquals(0, config.getConfiguredNAC());

        NACTracker tracker = new NACTracker();
        assertEquals(DecodeConfigP25Phase1.NAC_AUTODETECT, tracker.getTrackedNAC());
        tracker.setConfiguredNAC(0);
        assertTrue(tracker.hasConfiguredNAC());
        assertEquals(0, tracker.getTrackedNAC());
    }

    @Test
    void decoderFactoryAppliesBchLimitDuringAutomaticNacTracking()
    {
        Channel channel = new Channel("test");
        DecodeConfigP25Phase1 configuration = new DecodeConfigP25Phase1();
        configuration.setModulation(Modulation.C4FM_V2);
        configuration.setMaxBchErrors(5);
        assertFalse(configuration.hasConfiguredNAC());
        channel.setDecodeConfiguration(configuration);

        java.util.List<Module> modules = DecoderFactory.getModules(new ChannelMapModel(), channel, new AliasModel(),
                new UserPreferences(), null, null);
        P25P1DecoderC4FMv2 decoder = modules.stream()
                .filter(P25P1DecoderC4FMv2.class::isInstance)
                .map(P25P1DecoderC4FMv2.class::cast)
                .findFirst()
                .orElseThrow();

        assertEquals(5, decoder.getMessageFramer().getMaxBchErrors());
    }

    @Test
    void decoderFactoryCopyPreservesP25Settings()
    {
        DecodeConfigP25Phase1 original = new DecodeConfigP25Phase1();
        original.setModulationString("FUTURE_MOD_V3");
        original.setConfiguredNAC(659);
        original.setAudioHoldoverMs(750);
        original.setIgnoreEncryptionState(true);
        original.setPipelineDiagnostics(true);
        original.setMaxImbeErrors(3);
        original.setMaxBchErrors(7);
        original.setCmaAcquisitionMu(0.003f);
        original.setCmaTrackingMu(0.0001f);
        original.setCmaGearShiftMs(250);
        original.setGardnerBandwidth(0.02f);
        original.setAfcAlpha(0.004f);
        original.setAdaptiveThresholds(true);
        original.setDfeEnabled(true);
        original.setDfeMu(0.01f);

        DecodeConfigP25Phase1 copy = (DecodeConfigP25Phase1)DecoderFactory.copy(original);
        assertEquals("FUTURE_MOD_V3", copy.getModulationString());
        assertEquals(659, copy.getConfiguredNAC());
        assertEquals(750, copy.getAudioHoldoverMs());
        assertTrue(copy.isIgnoreEncryptionState());
        assertTrue(copy.isPipelineDiagnostics());
        assertEquals(3, copy.getMaxImbeErrors());
        assertEquals(7, copy.getMaxBchErrors());
        assertEquals(0.003f, copy.getCmaAcquisitionMu());
        assertEquals(0.0001f, copy.getCmaTrackingMu());
        assertEquals(250, copy.getCmaGearShiftMs());
        assertEquals(0.02f, copy.getGardnerBandwidth());
        assertEquals(0.004f, copy.getAfcAlpha());
        assertTrue(copy.isAdaptiveThresholds());
        assertTrue(copy.isDfeEnabled());
        assertEquals(0.01f, copy.getDfeMu());
    }

    @Test
    void testSetModulationClearsRaw() throws Exception
    {
        DecodeConfigP25Phase1 p1 = new DecodeConfigP25Phase1();
        p1.setModulationString("FUTURE_MOD_V3");
        assertEquals("FUTURE_MOD_V3", p1.getModulationString());

        // When user explicitly sets a known modulation, raw should update
        p1.setModulation(Modulation.CQPSK_V2);
        assertEquals(Modulation.CQPSK_V2, p1.getModulation());
        assertEquals("CQPSK_V2", p1.getModulationString());
    }
}
