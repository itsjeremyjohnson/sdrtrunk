/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.dsp.filter.nbfm;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NBFMAudioFiltersTest
{
    @Test
    void clampsAfterOutputGain()
    {
        NBFMAudioFilters filters = new NBFMAudioFilters(8000);
        filters.setLowPassEnabled(false);
        filters.setDeemphasisEnabled(false);
        filters.setHissReductionEnabled(false);
        filters.setBassBoostEnabled(false);
        filters.setVoiceEnhanceEnabled(false);
        filters.setNoiseGateEnabled(false);
        filters.setInputGain(2.0f);

        assertEquals(1.0f, filters.process(0.75f));
        assertEquals(-1.0f, filters.process(-0.75f));
        assertTrue(filters.process(0.25f) < 1.0f);
    }

    @Test
    @ResourceLock(Resources.SYSTEM_OUT)
    void noiseGateDoesNotWriteToStandardOutput()
    {
        PrintStream original = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try
        {
            System.setOut(new PrintStream(output));
            NBFMAudioFilters filters = new NBFMAudioFilters(8000);
            filters.setNoiseGateEnabled(true);
            filters.process(new float[1000]);
        }
        finally
        {
            System.setOut(original);
        }

        assertEquals("", output.toString());
    }
}
