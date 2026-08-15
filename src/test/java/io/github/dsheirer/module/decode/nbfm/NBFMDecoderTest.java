package io.github.dsheirer.module.decode.nbfm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NBFMDecoderTest
{
    @Test
    void ctcssDetectorOnlyLearnsWhileNoiseSquelchIsOpen()
    {
        assertFalse(NBFMDecoder.shouldProcessCTCSS(true));
        assertTrue(NBFMDecoder.shouldProcessCTCSS(false));
    }

    @Test
    void squelchOverrideBypassesConfiguredCtcssGate()
    {
        assertFalse(NBFMDecoder.shouldPassCTCSS(true, false, false));
        assertTrue(NBFMDecoder.shouldPassCTCSS(true, false, true));
        assertTrue(NBFMDecoder.shouldPassCTCSS(true, true, false));
        assertTrue(NBFMDecoder.shouldPassCTCSS(false, false, false));
    }
}
