/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.module.decode.nbfm;

import io.github.dsheirer.module.decode.config.ChannelToneFilter;
import io.github.dsheirer.module.decode.dcs.DCSCode;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
