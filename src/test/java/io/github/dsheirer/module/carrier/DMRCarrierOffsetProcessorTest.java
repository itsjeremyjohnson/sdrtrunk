/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.module.carrier;

import io.github.dsheirer.sample.complex.ComplexSamples;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DMRCarrierOffsetProcessorTest
{
    @Test
    void throttlesAfterUnsuccessfulCarrierAttempt()
    {
        DMRCarrierOffsetProcessor processor = new DMRCarrierOffsetProcessor();
        float[] i = new float[128];
        float[] q = new float[128];

        assertTrue(processor.process(new ComplexSamples(i, q, 1001)));
        assertFalse(processor.process(new ComplexSamples(i, q, 1002)));
    }
}
