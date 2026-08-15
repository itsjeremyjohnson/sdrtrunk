/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.sample.complex;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IQImbalanceCorrectorTest
{
    @Test
    void removesPhaseProjectionAndNormalizesResidualPower()
    {
        int length = 200_000;
        float[] i = new float[length];
        float[] q = new float[length];

        for(int x = 0; x < length; x++)
        {
            double angle = 2.0 * Math.PI * x / 64.0;
            i[x] = (float)Math.cos(angle);
            q[x] = (float)(2.0 * (Math.sin(angle) * Math.cos(Math.PI / 6.0) +
                Math.cos(angle) * Math.sin(Math.PI / 6.0)));
        }

        new IQImbalanceCorrector().correct(i, q);
        double iPower = 0;
        double qPower = 0;
        double cross = 0;

        for(int x = length - 20_000; x < length; x++)
        {
            iPower += i[x] * i[x];
            qPower += q[x] * q[x];
            cross += i[x] * q[x];
        }

        assertEquals(0.0, cross / iPower, 0.01);
        assertEquals(1.0, qPower / iPower, 0.02);
    }
}
