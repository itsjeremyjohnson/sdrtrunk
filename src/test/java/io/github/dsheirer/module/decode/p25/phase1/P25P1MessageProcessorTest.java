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
package io.github.dsheirer.module.decode.p25.phase1;

import io.github.dsheirer.bits.CorrectedBinaryMessage;
import io.github.dsheirer.message.IMessage;
import io.github.dsheirer.module.decode.p25.phase1.message.lc.LinkControlWord;
import io.github.dsheirer.module.decode.p25.phase1.message.lc.standard.LCGroupVoiceChannelUser;
import io.github.dsheirer.module.decode.p25.phase1.message.lc.standard.LCSourceIDExtension;
import io.github.dsheirer.module.decode.p25.phase1.message.ldu.LDU1Message;
import io.github.dsheirer.module.decode.p25.phase1.message.tdu.TDULCMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class P25P1MessageProcessorTest
{
    @Test
    void attachesTdulcSourceExtensionBeforeFlushingHeldLdu1()
    {
        CorrectedBinaryMessage voiceLcwBits = new CorrectedBinaryMessage(72);
        voiceLcwBits.set(31);
        LCGroupVoiceChannelUser voiceLcw = new LCGroupVoiceChannelUser(voiceLcwBits, 1L, false);
        LCSourceIDExtension extension =
                new LCSourceIDExtension(new CorrectedBinaryMessage(72), 2L, true);
        LDU1Message ldu1 = new TestLDU1Message(voiceLcw);
        TDULCMessage tdulc = new TestTDULCMessage(extension);
        List<IMessage> messages = new ArrayList<>();
        P25P1MessageProcessor processor = new P25P1MessageProcessor();
        processor.setMessageListener(messages::add);

        processor.receive(ldu1);
        assertTrue(messages.isEmpty());
        processor.receive(tdulc);

        assertEquals(2, messages.size());
        assertSame(ldu1, messages.get(0));
        assertSame(tdulc, messages.get(1));
        assertTrue(voiceLcw.isFullyExtended());
    }

    private static class TestLDU1Message extends LDU1Message
    {
        private final LinkControlWord mLinkControlWord;

        TestLDU1Message(LinkControlWord linkControlWord)
        {
            super(new CorrectedBinaryMessage(P25P1DataUnitID.LOGICAL_LINK_DATA_UNIT_1.getMessageLength()), 0x293, 1L);
            mLinkControlWord = linkControlWord;
        }

        @Override
        public LinkControlWord getLinkControlWord()
        {
            return mLinkControlWord;
        }
    }

    private static class TestTDULCMessage extends TDULCMessage
    {
        private final LinkControlWord mLinkControlWord;

        TestTDULCMessage(LinkControlWord linkControlWord)
        {
            super(new CorrectedBinaryMessage(P25P1DataUnitID.TERMINATOR_DATA_UNIT_LINK_CONTROL.getMessageLength()),
                    0x293, 2L);
            mLinkControlWord = linkControlWord;
        }

        @Override
        public LinkControlWord getLinkControlWord()
        {
            return mLinkControlWord;
        }
    }
}
