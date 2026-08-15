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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HeartbeatPreferenceEditorTest
{
    @Test
    void parsesTypedHeartbeatValuesWithinEachSpinnerRange()
    {
        assertEquals(291, HeartbeatPreferenceEditor.parseIntegerEditorValue(" 291 ", 0, 65535));
        assertEquals(255, HeartbeatPreferenceEditor.parseIntegerEditorValue("255", 0, 255));
        assertEquals(30, HeartbeatPreferenceEditor.parseIntegerEditorValue("30", 10, 3600));
    }

    @Test
    void rejectsInvalidTypedHeartbeatValues()
    {
        assertNull(HeartbeatPreferenceEditor.parseIntegerEditorValue(null, 0, 255));
        assertNull(HeartbeatPreferenceEditor.parseIntegerEditorValue("site", 0, 255));
        assertNull(HeartbeatPreferenceEditor.parseIntegerEditorValue("256", 0, 255));
        assertNull(HeartbeatPreferenceEditor.parseIntegerEditorValue("9", 10, 3600));
    }
}
