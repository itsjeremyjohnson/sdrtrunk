/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
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

class NetworkStreamPreferenceEditorTest
{
    @Test
    void parsesTypedPortValues()
    {
        assertEquals(9501, NetworkStreamPreferenceEditor.parsePort("9501"));
        assertEquals(1024, NetworkStreamPreferenceEditor.parsePort(" 1024 "));
        assertEquals(65535, NetworkStreamPreferenceEditor.parsePort("65535"));
    }

    @Test
    void rejectsInvalidTypedPortValues()
    {
        assertNull(NetworkStreamPreferenceEditor.parsePort(null));
        assertNull(NetworkStreamPreferenceEditor.parsePort(""));
        assertNull(NetworkStreamPreferenceEditor.parsePort("not-a-port"));
        assertNull(NetworkStreamPreferenceEditor.parsePort("1023"));
        assertNull(NetworkStreamPreferenceEditor.parsePort("65536"));
    }
}
