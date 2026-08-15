/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * *****************************************************************************
 */
package io.github.dsheirer.dsp.filter.equalizer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CMAEqualizerTest
{
    @Test
    void resetStartsAsCurrentSamplePassthrough()
    {
        CMAEqualizer equalizer = new CMAEqualizer(1.0f, 0.0f);
        float[] i = {1.0f, 2.0f, 3.0f};
        float[] q = {-1.0f, -2.0f, -3.0f};

        equalizer.equalize(i, q);

        assertArrayEquals(new float[]{1.0f, 2.0f, 3.0f}, i);
        assertArrayEquals(new float[]{-1.0f, -2.0f, -3.0f}, q);
    }
}
