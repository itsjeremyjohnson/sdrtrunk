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
package io.github.dsheirer.module.decode.p25.audio;

import io.github.dsheirer.alias.AliasList;
import io.github.dsheirer.preference.UserPreferences;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class P25P1AudioModuleTest
{
    @Test
    void restoresEncryptedStateAfterConfiguredConfirmationCount()
    {
        P25P1AudioModule module = new P25P1AudioModule(new UserPreferences(), new AliasList("test"));
        module.updateEncryptionState(false);
        assertFalse(module.isEncryptedCall());

        for(int x = 0; x < module.getEncryptionConfirmationThreshold(); x++)
        {
            module.updateEncryptionState(true);
        }

        assertTrue(module.isEncryptedCall());
    }
}
