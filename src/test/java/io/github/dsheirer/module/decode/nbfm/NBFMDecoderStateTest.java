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
package io.github.dsheirer.module.decode.nbfm;

import io.github.dsheirer.channel.state.DecoderStateEvent;
import io.github.dsheirer.channel.state.State;
import io.github.dsheirer.dsp.squelch.CTCSSFrequency;
import io.github.dsheirer.identifier.Form;
import io.github.dsheirer.module.decode.event.IDecodeEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NBFMDecoderStateTest
{
    @Test
    void toneEventIncludesConfiguredTalkgroup()
    {
        DecodeConfigNBFM configuration = new DecodeConfigNBFM();
        configuration.setTalkgroup(1234);
        configuration.setCTCSSFrequency(CTCSSFrequency.TONE_100_0);
        NBFMDecoderState state = new NBFMDecoderState("NBFM Test", configuration);
        List<IDecodeEvent> events = new ArrayList<>();
        state.addDecodeEventListener(events::add);

        state.receiveDecoderStateEvent(new DecoderStateEvent(this, DecoderStateEvent.Event.DECODE, State.CALL));

        assertEquals(1, events.size());
        assertEquals(1234, events.getFirst().getIdentifierCollection().getIdentifiers(Form.TALKGROUP)
                .getFirst().getValue());
    }
}
