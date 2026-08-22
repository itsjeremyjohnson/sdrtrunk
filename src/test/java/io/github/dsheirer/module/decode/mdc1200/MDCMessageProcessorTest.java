/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * ****************************************************************************
 */
package io.github.dsheirer.module.decode.mdc1200;

import io.github.dsheirer.bits.CorrectedBinaryMessage;
import io.github.dsheirer.message.IMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Soft-sync noise candidates that fail CRC must not reach shared message history.
 */
class MDCMessageProcessorTest
{
    @Test
    void crcInvalidCandidateIsNotBroadcast()
    {
        MDCMessageProcessor processor = new MDCMessageProcessor();
        List<IMessage> seen = new ArrayList<>();
        processor.addMessageListener(seen::add);

        //304 bits covers both deinterleave windows (offset 40 and 192). All-zero
        //payload cannot pass CRC-16 after FEC, so the processor must drop it.
        processor.receive(new CorrectedBinaryMessage(304));

        assertTrue(seen.isEmpty(), "CRC-fail frames must not be broadcast");
    }
}
