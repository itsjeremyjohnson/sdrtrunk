/*
 * *****************************************************************************
 * Copyright (C) 2026 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * *****************************************************************************
 */
package io.github.dsheirer.source.tuner.hydrasdr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HydraSdrTunerEditorTest
{
    @Test
    void fallsBackToCustomForUnsupportedPresetModes()
    {
        assertEquals(HydraSdrTunerController.GAIN_MODE_CUSTOM,
            HydraSdrTunerEditor.getSupportedGainMode(HydraSdrTunerController.GAIN_MODE_LINEARITY, false, true));
        assertEquals(HydraSdrTunerController.GAIN_MODE_CUSTOM,
            HydraSdrTunerEditor.getSupportedGainMode(HydraSdrTunerController.GAIN_MODE_SENSITIVITY, true, false));
    }

    @Test
    void clampsInitialFrequencyToReportedRange()
    {
        assertEquals(101_100_000,
            HydraSdrTunerController.getInitialFrequency(101_100_000, 1_000_000, 2_000_000_000));
        assertEquals(200_000_000,
            HydraSdrTunerController.getInitialFrequency(101_100_000, 200_000_000, 2_000_000_000));
        assertEquals(50_000_000,
            HydraSdrTunerController.getInitialFrequency(101_100_000, 1_000_000, 50_000_000));
    }

    @Test
    void clampsConfigurationFallbackToReportedRange()
    {
        assertEquals(200_000_000,
            HydraSdrTunerController.getConfigurationFallbackFrequency(200_000_000, 2_000_000_000));
        assertEquals(50_000_000,
            HydraSdrTunerController.getConfigurationFallbackFrequency(1_000_000, 50_000_000));
    }

    @Test
    void preservesSupportedModes()
    {
        assertEquals(HydraSdrTunerController.GAIN_MODE_LINEARITY,
            HydraSdrTunerEditor.getSupportedGainMode(HydraSdrTunerController.GAIN_MODE_LINEARITY, true, false));
        assertEquals(HydraSdrTunerController.GAIN_MODE_SENSITIVITY,
            HydraSdrTunerEditor.getSupportedGainMode(HydraSdrTunerController.GAIN_MODE_SENSITIVITY, false, true));
        assertEquals(HydraSdrTunerController.GAIN_MODE_CUSTOM,
            HydraSdrTunerEditor.getSupportedGainMode(HydraSdrTunerController.GAIN_MODE_CUSTOM, false, false));
    }
}
