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
package io.github.dsheirer.module.decode.p25.phase1.message.ldu;

import io.github.dsheirer.bits.BinaryMessage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IMBEFrameDiagnosticTest
{
    @Test
    void preservesBitSetLittleEndianByteOrder()
    {
        BinaryMessage message = IMBEFrameDiagnostic.fromLittleEndianBytes(new byte[]{0x01, (byte)0x80});

        assertTrue(message.get(0));
        assertFalse(message.get(7));
        assertFalse(message.get(8));
        assertTrue(message.get(15));
    }
}
