/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.dsp.filter.resample;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealResamplerTest
{
    @Test
    void lastBatchFlushesOnlyProducedSamples()
    {
        RealResampler resampler = new RealResampler(8000, 8000, 4192, 512);
        List<float[]> output = new ArrayList<>();
        resampler.setListener(output::add);

        resampler.resample(new float[100], true);

        assertFalse(output.isEmpty());
        assertTrue(output.getLast().length < 512);
    }
}
