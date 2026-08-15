/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.source.tuner.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrequencyErrorCorrectionManagerTest
{
    @Test
    void manualCorrectionRebasesSanityBaseline()
    {
        FrequencyErrorCorrectionManager manager = new FrequencyErrorCorrectionManager(null);

        manager.frequencyCorrectionChanged(24.5);

        assertEquals(24.5, manager.getBaselinePPM());
    }
}
