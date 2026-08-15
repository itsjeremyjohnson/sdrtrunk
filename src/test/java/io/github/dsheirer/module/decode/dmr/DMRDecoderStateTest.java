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
package io.github.dsheirer.module.decode.dmr;

import io.github.dsheirer.bits.CorrectedBinaryMessage;
import io.github.dsheirer.module.decode.dmr.message.data.IDLEMessage;
import io.github.dsheirer.module.decode.dmr.message.data.UnknownDataMessage;
import io.github.dsheirer.module.decode.dmr.message.data.block.UnknownDataBlock;
import io.github.dsheirer.module.decode.dmr.message.data.csbk.UnknownCSBKMessage;
import io.github.dsheirer.module.decode.dmr.message.data.lc.full.UnknownFullLCMessage;
import io.github.dsheirer.module.decode.dmr.message.data.usb.USBData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DMRDecoderStateTest
{
    @Test
    void rawStreamIncludesControlAndLinkControlFamilies()
    {
        assertTrue(DMRDecoderState.isRawStreamMessageFamily(
            new UnknownCSBKMessage(null, new CorrectedBinaryMessage(99), null, null, 0, 1)));
        assertTrue(DMRDecoderState.isRawStreamMessageFamily(
            new UnknownFullLCMessage(new CorrectedBinaryMessage(80), 0, 1)));
    }

    @Test
    void rawStreamExcludesOtherDataFamilies()
    {
        assertFalse(DMRDecoderState.isRawStreamMessageFamily(
            new IDLEMessage(null, new CorrectedBinaryMessage(99), null, null, 0, 1)));
        assertFalse(DMRDecoderState.isRawStreamMessageFamily(
            new UnknownDataMessage(null, new CorrectedBinaryMessage(99), null, null, 0, 1)));
        assertFalse(DMRDecoderState.isRawStreamMessageFamily(
            new UnknownDataBlock(null, new CorrectedBinaryMessage(99), null, null, 0, 1)));
        assertFalse(DMRDecoderState.isRawStreamMessageFamily(
            new USBData(null, new CorrectedBinaryMessage(99), null, null, 0, 1)));
        assertFalse(DMRDecoderState.isRawStreamMessageFamily(null));
    }
}
