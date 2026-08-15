/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.module.decode.ctcss;

import io.github.dsheirer.channel.state.DecoderStateEvent;
import io.github.dsheirer.channel.state.State;
import io.github.dsheirer.identifier.Form;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CTCSSDecoderStateTest
{
    @Test
    void removesToneIdentifierWhenToneIsLost()
    {
        CTCSSDecoderState state = new CTCSSDecoderState();
        state.receive(new CTCSSMessage(CTCSSCode.TONE_XZ, 1));
        assertEquals(1, state.getIdentifierCollection().getIdentifiers(Form.TONE).size());

        state.receive(CTCSSMessage.toneLost(2));

        assertEquals(0, state.getIdentifierCollection().getIdentifiers(Form.TONE).size());
    }

    @Test
    void removesToneIdentifierAtCallEnd()
    {
        CTCSSDecoderState state = new CTCSSDecoderState();
        state.receive(new CTCSSMessage(CTCSSCode.TONE_XZ, 1));

        state.receiveDecoderStateEvent(new DecoderStateEvent(state, DecoderStateEvent.Event.END, State.CALL, 0));

        assertEquals(0, state.getIdentifierCollection().getIdentifiers(Form.TONE).size());
    }
}
