/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.gui.playlist.radioreference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
