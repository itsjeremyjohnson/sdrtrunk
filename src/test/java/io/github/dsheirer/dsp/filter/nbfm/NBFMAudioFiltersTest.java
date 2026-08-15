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

class NBFMAudioFiltersTest
{
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
