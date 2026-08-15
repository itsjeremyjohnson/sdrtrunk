/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.sample.complex;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoiseBlankerTest
{
    @Test
    void adaptsToSustainedStrongCarrier()
    {
        float[] i = new float[1000];
        float[] q = new float[1000];
        Arrays.fill(i, 1.0f);

        NoiseBlanker blanker = new NoiseBlanker();
        blanker.process(i, q);

        assertTrue(i[0] == 0.0f, "Initial impulse should be blanked");
        assertEquals(1.0f, i[i.length - 1], "Sustained carrier should eventually pass");
    }
}
