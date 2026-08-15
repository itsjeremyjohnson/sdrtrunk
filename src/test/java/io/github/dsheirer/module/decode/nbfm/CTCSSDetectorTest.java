/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.module.decode.nbfm;

import io.github.dsheirer.module.decode.ctcss.CTCSSCode;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CTCSSDetectorTest
{
    @Test
    void neighboringLowToneIsRejectedInsteadOfRelabeledAsTarget()
    {
        CTCSSDetector detector = new CTCSSDetector(EnumSet.of(CTCSSCode.TONE_WZ), 8000.0f);
        AtomicReference<CTCSSCode> detected = new AtomicReference<>();
        AtomicReference<CTCSSCode> rejected = new AtomicReference<>();
        detector.setListener(new CTCSSDetector.CTCSSDetectorListener()
        {
            @Override public void ctcssDetected(CTCSSCode code) { detected.set(code); }
            @Override public void ctcssRejected(CTCSSCode code) { rejected.set(code); }
            @Override public void ctcssLost() {}
        });
        float[] samples = new float[8000];

        for(int x = 0; x < samples.length; x++)
        {
            samples[x] = (float)Math.sin(2.0 * Math.PI * 67.0 * x / 8000.0);
        }

        detector.process(samples);

        assertNull(detected.get());
        assertEquals(CTCSSCode.TONE_XZ, rejected.get());
    }
}
