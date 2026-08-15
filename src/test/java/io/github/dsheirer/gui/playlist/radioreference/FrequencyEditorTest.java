/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.gui.playlist.radioreference;

import io.github.dsheirer.module.decode.ctcss.CTCSSCode;
import io.github.dsheirer.module.decode.nbfm.DecodeConfigNBFM;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrequencyEditorTest
{
    @Test
    void normalizesRadioReferenceDcsCodes()
    {
        assertEquals("N023", FrequencyEditor.normalizeDcsCode("D023N"));
        assertEquals("I023", FrequencyEditor.normalizeDcsCode("D023I"));
        assertEquals("N125", FrequencyEditor.normalizeDcsCode("125"));
        assertEquals("N023", FrequencyEditor.normalizeDcsCode("N023"));
    }

    @Test
    void rejectsUnsupportedImportedCtcssTone()
    {
        DecodeConfigNBFM config = new DecodeConfigNBFM();

        FrequencyEditor.configureToneFilter(config, "159.8 PL");

        assertFalse(config.isToneFilterEnabled());
        assertTrue(config.getToneFilters().isEmpty());
    }

    @Test
    void canonicalizesSupportedImportedCtcssTone()
    {
        DecodeConfigNBFM config = new DecodeConfigNBFM();

        FrequencyEditor.configureToneFilter(config, "156.7 PL");

        assertTrue(config.isToneFilterEnabled());
        assertEquals(CTCSSCode.TONE_5A, config.getToneFilters().getFirst().getCTCSSCode());
    }
}
