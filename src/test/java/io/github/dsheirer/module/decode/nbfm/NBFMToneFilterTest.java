/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.module.decode.nbfm;

import io.github.dsheirer.identifier.ctcss.CTCSSIdentifier;
import io.github.dsheirer.identifier.dcs.DCSIdentifier;
import io.github.dsheirer.dsp.squelch.SquelchTailRemover;
import io.github.dsheirer.module.decode.config.ChannelToneFilter;
import io.github.dsheirer.module.decode.ctcss.CTCSSCode;
import io.github.dsheirer.module.decode.dcs.DCSCode;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NBFMToneFilterTest
{
    @Test
    void enabledEmptyToneFilterKeepsMuteGateActive()
    {
        DecodeConfigNBFM configuration = new DecodeConfigNBFM();
        configuration.setToneFilterEnabled(true);

        assertTrue(configuration.hasToneFiltering());
    }

    @Test
    void mixedCtcssAndDcsFiltersEnableBothDetectorTypes()
    {
        DecodeConfigNBFM configuration = new DecodeConfigNBFM();
        configuration.setToneFilterEnabled(true);
        configuration.setToneFilters(List.of(
            new ChannelToneFilter(ChannelToneFilter.ToneType.CTCSS, "TONE_XZ", "ctcss"),
            new ChannelToneFilter(ChannelToneFilter.ToneType.DCS, "N023", "dcs")));

        NBFMDecoder decoder = new NBFMDecoder(configuration);

        assertTrue(decoder.hasConfiguredCTCSSFiltering());
        assertTrue(decoder.hasConfiguredDCSFiltering());
    }

    @Test
    void channelFilterDetectionsPublishAndRemoveToneIdentifiers()
    {
        NBFMDecoderState state = new NBFMDecoderState("test", new DecodeConfigNBFM());

        state.setDetectedCTCSS(CTCSSCode.TONE_XZ);
        assertTrue(state.getIdentifierCollection().getIdentifiers().stream().anyMatch(CTCSSIdentifier.class::isInstance));

        state.setDetectedDCS(DCSCode.N023);
        assertTrue(state.getIdentifierCollection().getIdentifiers().stream().anyMatch(DCSIdentifier.class::isInstance));
        state.setRejectedDCS(DCSCode.N025);
        assertFalse(state.getIdentifierCollection().getIdentifiers().stream().anyMatch(DCSIdentifier.class::isInstance));
        assertTrue(state.getIdentifierCollection().getIdentifiers().stream().anyMatch(CTCSSIdentifier.class::isInstance));

        state.setRejectedCTCSS(CTCSSCode.TONE_WZ);
        assertFalse(state.getIdentifierCollection().getIdentifiers().stream().anyMatch(CTCSSIdentifier.class::isInstance));

        state.setDetectedDCS(DCSCode.N023);
        state.setDCSLost();
        assertFalse(state.getIdentifierCollection().getIdentifiers().stream().anyMatch(DCSIdentifier.class::isInstance));
    }

    @Test
    void closingCombinedToneGateDiscardsBufferedSquelchTail() throws Exception
    {
        NBFMDecoder decoder = new NBFMDecoder(new DecodeConfigNBFM());
        SquelchTailRemover remover = new SquelchTailRemover(100, 0);
        AtomicInteger outputSamples = new AtomicInteger();
        remover.setOutputListener(audio -> outputSamples.addAndGet(audio.length));
        remover.squelchOpen();
        remover.process(new float[400]);
        setField(decoder, "mSquelchTailRemover", remover);
        setField(decoder, "mToneMatch", true);
        setField(decoder, "mCTCSSMatch", false);
        setField(decoder, "mDCSMatch", false);
        Method updateToneMatch = NBFMDecoder.class.getDeclaredMethod("updateToneMatch");
        updateToneMatch.setAccessible(true);

        updateToneMatch.invoke(decoder);
        remover.flush();

        assertEquals(0, outputSamples.get());
    }

    private static void setField(Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void nonTargetDcsDetectionsDoNotPreventToneLoss()
    {
        DCSDetector detector = new DCSDetector(Set.of(DCSCode.N023));
        AtomicInteger losses = new AtomicInteger();
        detector.setListener(new DCSDetector.DCSDetectorListener()
        {
            @Override
            public void dcsDetected(DCSCode code) {}

            @Override
            public void dcsRejected(DCSCode code) {}

            @Override
            public void dcsLost()
            {
                losses.incrementAndGet();
            }
        });

        for(int x = 0; x < 3; x++)
        {
            detector.handleDetection(DCSCode.N023);
        }

        for(int x = 0; x < 12; x++)
        {
            detector.handleDetection(x % 2 == 0 ? DCSCode.N025 : DCSCode.N026);
            detector.process(new float[1369]);
        }

        assertEquals(1, losses.get());
    }
}
