/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.module.decode;

import io.github.dsheirer.module.decode.config.ChannelToneFilter;
import io.github.dsheirer.module.decode.dmr.DecodeConfigDMR;
import io.github.dsheirer.module.decode.dmr.channel.TimeslotFrequency;
import io.github.dsheirer.module.decode.nbfm.DecodeConfigNBFM;
import io.github.dsheirer.module.decode.p25.phase1.DecodeConfigP25Phase1;
import io.github.dsheirer.module.decode.p25.phase1.Modulation;
import io.github.dsheirer.module.decode.p25.phase2.DecodeConfigP25Phase2;
import io.github.dsheirer.module.decode.p25.phase2.enumeration.ScrambleParameters;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecoderFactoryCopyTest
{
    @Test
    void copiesNbfmSettingsWithoutSharingToneFilters()
    {
        DecodeConfigNBFM original = new DecodeConfigNBFM();
        original.setAudioHangtimeMs(250);
        original.setToneFilterEnabled(true);
        original.setToneFilters(List.of(new ChannelToneFilter(ChannelToneFilter.ToneType.DCS, "N023", "Dispatch")));
        original.setSquelchTailRemovalEnabled(false);
        original.setSquelchTailRemovalMs(175);
        original.setSquelchHeadRemovalMs(25);
        original.setDeemphasisEnabled(true);
        original.setLowPassCutoff(2400.0);
        original.setBassBoostEnabled(true);
        original.setNoiseGateEnabled(true);
        original.setAgcEnabled(true);
        original.setHissReductionDb(-3.0f);

        DecodeConfigNBFM copy = (DecodeConfigNBFM)DecoderFactory.copy(original);

        assertEquals(250, copy.getAudioHangtimeMs());
        assertTrue(copy.isToneFilterEnabled());
        assertNotSame(original.getToneFilters(), copy.getToneFilters());
        assertNotSame(original.getToneFilters().getFirst(), copy.getToneFilters().getFirst());
        assertEquals("Dispatch", copy.getToneFilters().getFirst().getLabel());
        assertFalse(copy.isSquelchTailRemovalEnabled());
        assertEquals(175, copy.getSquelchTailRemovalMs());
        assertEquals(25, copy.getSquelchHeadRemovalMs());
        assertTrue(copy.isDeemphasisEnabled());
        assertEquals(2400.0, copy.getLowPassCutoff());
        assertTrue(copy.isBassBoostEnabled());
        assertTrue(copy.isNoiseGateEnabled());
        assertTrue(copy.isAgcEnabled());
        assertEquals(-3.0f, copy.getHissReductionDb());
    }

    @Test
    void copiesDmrSettingsWithoutSharingTimeslotMap()
    {
        DecodeConfigDMR original = new DecodeConfigDMR();
        original.setIgnoreDataCalls(false);
        original.setIgnoreUnaliasedTalkgroups(true);
        original.setIgnoreCRCChecksums(true);
        original.setUseCompressedTalkgroups(true);
        original.setTrafficChannelPoolSize(7);
        original.setAudioHangtimeMs(300);
        TimeslotFrequency timeslot = new TimeslotFrequency();
        timeslot.setNumber(3);
        timeslot.setDownlinkFrequency(451_000_000L);
        original.addTimeslotFrequency(timeslot);

        DecodeConfigDMR copy = (DecodeConfigDMR)DecoderFactory.copy(original);

        assertFalse(copy.getIgnoreDataCalls());
        assertTrue(copy.getIgnoreUnaliasedTalkgroups());
        assertTrue(copy.getIgnoreCRCChecksums());
        assertTrue(copy.isUseCompressedTalkgroups());
        assertEquals(7, copy.getTrafficChannelPoolSize());
        assertEquals(300, copy.getAudioHangtimeMs());
        assertNotSame(original.getTimeslotMap(), copy.getTimeslotMap());
        assertNotSame(original.getTimeslotMap().getFirst(), copy.getTimeslotMap().getFirst());
        assertEquals(451_000_000L, copy.getTimeslotMap().getFirst().getDownlinkFrequency());
    }

    @Test
    void copiesP25InheritedAndPhaseSpecificSettings()
    {
        DecodeConfigP25Phase1 phase1 = new DecodeConfigP25Phase1();
        phase1.setIgnoreDataCalls(true);
        phase1.setIgnoreUnaliasedTalkgroups(true);
        phase1.setTrafficChannelPoolSize(8);
        phase1.setAllowedNACs(List.of(0x123, 0x456));
        phase1.setNacFilterEnabled(true);
        phase1.setTalkgroup(321);
        phase1.setGraphicEQEnabled(true);
        phase1.setGraphicEQBandGains(new double[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        phase1.setAudioHangtimeMs(400);
        phase1.setModulation(Modulation.CQPSK);

        DecodeConfigP25Phase1 phase1Copy = (DecodeConfigP25Phase1)DecoderFactory.copy(phase1);
        assertTrue(phase1Copy.getIgnoreUnaliasedTalkgroups());
        assertEquals(List.of(0x123, 0x456), phase1Copy.getAllowedNACs());
        assertNotSame(phase1.getAllowedNACs(), phase1Copy.getAllowedNACs());
        assertTrue(phase1Copy.isNacFilterEnabled());
        assertEquals(321, phase1Copy.getTalkgroup());
        assertTrue(phase1Copy.isGraphicEQEnabled());
        assertArrayEquals(phase1.getGraphicEQBandGains(), phase1Copy.getGraphicEQBandGains());
        assertNotSame(phase1.getGraphicEQBandGains(), phase1Copy.getGraphicEQBandGains());
        assertEquals(400, phase1Copy.getAudioHangtimeMs());
        assertEquals(Modulation.CQPSK, phase1Copy.getModulation());

        DecodeConfigP25Phase2 phase2 = new DecodeConfigP25Phase2();
        phase2.setIgnoreUnaliasedTalkgroups(true);
        phase2.setAutoDetectScrambleParameters(false);
        phase2.setScrambleParameters(new ScrambleParameters(1, 2, 3));
        DecodeConfigP25Phase2 phase2Copy = (DecodeConfigP25Phase2)DecoderFactory.copy(phase2);
        assertTrue(phase2Copy.getIgnoreUnaliasedTalkgroups());
        assertFalse(phase2Copy.isAutoDetectScrambleParameters());
        assertNotSame(phase2.getScrambleParameters(), phase2Copy.getScrambleParameters());
        assertEquals(3, phase2Copy.getScrambleParameters().getNAC());
    }
}
