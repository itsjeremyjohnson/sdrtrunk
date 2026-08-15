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
}
