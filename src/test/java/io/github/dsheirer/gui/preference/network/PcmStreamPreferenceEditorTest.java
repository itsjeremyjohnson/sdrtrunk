/*
 * *****************************************************************************
 * Copyright (C) 2026 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * ****************************************************************************
 */
package io.github.dsheirer.gui.preference.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PcmStreamPreferenceEditorTest
{
    @Test
    void describesVoiceIdAsOncePerCall()
    {
        assertTrue(PcmStreamPreferenceEditor.VOICE_ID_FORMAT_DESCRIPTION.contains("once per call"));
    }

    @Test
    void describesP25PcmAsLduLevelBurstDelivery()
    {
        assertTrue(PcmStreamPreferenceEditor.PCM_DELIVERY_DESCRIPTION.contains("complete LDU"));
        assertTrue(PcmStreamPreferenceEditor.PCM_DELIVERY_DESCRIPTION.contains("burst"));
        assertFalse(PcmStreamPreferenceEditor.PCM_DELIVERY_DESCRIPTION.contains("within ~20 ms"));
    }

    @Test
    void playbackExampleUsesOneSequentialOutputStream()
    {
        assertTrue(PcmStreamPreferenceEditor.PYTHON_PLAYBACK_EXAMPLE.contains("with sd.OutputStream"));
        assertTrue(PcmStreamPreferenceEditor.PYTHON_PLAYBACK_EXAMPLE.contains("output.write"));
        assertFalse(PcmStreamPreferenceEditor.PYTHON_PLAYBACK_EXAMPLE.contains("sd.play("));
    }
}
