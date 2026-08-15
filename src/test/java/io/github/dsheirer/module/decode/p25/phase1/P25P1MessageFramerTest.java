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

import io.github.dsheirer.dsp.symbol.Dibit;
import io.github.dsheirer.module.decode.p25.phase1.message.P25P1Message;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class P25P1MessageFramerTest
{
    @Test
    void preservesErrorFreeTerminatorAfterVoiceFrame()
    {
        P25P1MessageFramer framer = new P25P1MessageFramer();
        framer.setPreviousDataUnitID(P25P1DataUnitID.LOGICAL_LINK_DATA_UNIT_1);
        framer.setMaxConsecutiveDuidCorrections(3);

        framer.nidDetected(0x293, P25P1DataUnitID.TERMINATOR_DATA_UNIT, 0);

        assertEquals(0, framer.getDuidCorrectionCount());
    }

    @Test
    void correctsUncertainTerminatorDuringVoiceFrame()
    {
        P25P1MessageFramer framer = new P25P1MessageFramer();
        framer.setPreviousDataUnitID(P25P1DataUnitID.LOGICAL_LINK_DATA_UNIT_1);
        framer.setMaxConsecutiveDuidCorrections(3);

        framer.nidDetected(0x293, P25P1DataUnitID.TERMINATOR_DATA_UNIT, 8);

        assertEquals(1, framer.getDuidCorrectionCount());
    }

    @Test
    void correctionFlagBelongsToTheAssemblerCreatedForThatNid()
    {
        List<P25P1Message> messages = new ArrayList<>();
        P25P1MessageFramer framer = createRunningFramer(messages);
        framer.nidDetected(0x293, P25P1DataUnitID.LOGICAL_LINK_DATA_UNIT_1, 0);
        feed(framer, 100);
        framer.setPreviousDataUnitID(P25P1DataUnitID.LOGICAL_LINK_DATA_UNIT_1);

        framer.nidDetected(0x293, P25P1DataUnitID.TERMINATOR_DATA_UNIT, 8);

        P25P1Message previous = messages.getLast();
        assertFalse(previous.isDuidCorrected());

        feed(framer, 1);
        assertNotNull(framer.getMessageAssembler());
        assertTrue(framer.getMessageAssembler().isDuidCorrected());

        feed(framer, 100);
        framer.nidDetected(0x293, P25P1DataUnitID.LOGICAL_LINK_DATA_UNIT_1, 0);
        P25P1Message corrected = messages.getLast();
        assertTrue(corrected.isDuidCorrected());
    }

    @Test
    void doesNotFlywheelWhenCarrierIsAbsent()
    {
        List<P25P1Message> messages = new ArrayList<>();
        P25P1MessageFramer framer = createRunningFramer(messages);
        framer.setEnergyProvider(new TestEnergyProvider(false));
        framer.nidDetected(0x293, P25P1DataUnitID.LOGICAL_LINK_DATA_UNIT_1, 0);
        feedUntilMessage(framer, messages, 1);
        feed(framer, 5000);

        assertEquals(0, framer.getFlywheelAttemptCount());
    }

    @Test
    void flywheelMessagesAreMarkedWhenCarrierIsPresent()
    {
        assertFlywheelMessageIsCreated(0x293);
    }

    @Test
    void flywheelSupportsZeroNac()
    {
        assertFlywheelMessageIsCreated(0x000);
    }

    private void assertFlywheelMessageIsCreated(int nac)
    {
        List<P25P1Message> messages = new ArrayList<>();
        P25P1MessageFramer framer = createRunningFramer(messages);
        framer.setEnergyProvider(new TestEnergyProvider(true));
        framer.nidDetected(nac, P25P1DataUnitID.LOGICAL_LINK_DATA_UNIT_1, 0);
        feedUntilMessage(framer, messages, 1);
        int initialCount = messages.size();

        for(int x = 0; x < 5000 && framer.getFlywheelAttemptCount() == 0; x++)
        {
            framer.process(Dibit.D00_PLUS_1);
        }
        feedUntilMessage(framer, messages, initialCount + 1);

        assertEquals(1, framer.getFlywheelAttemptCount());
        assertTrue((messages.getLast()).isDuidCorrected());
    }

    private P25P1MessageFramer createRunningFramer(List<P25P1Message> messages)
    {
        P25P1MessageFramer framer = new P25P1MessageFramer();
        framer.setListener(message ->
        {
            if(message instanceof P25P1Message p25Message)
            {
                messages.add(p25Message);
            }
        });
        framer.start();
        return framer;
    }

    private void feedUntilMessage(P25P1MessageFramer framer, List<P25P1Message> messages, int count)
    {
        for(int x = 0; x < 5000 && messages.size() < count; x++)
        {
            framer.process(Dibit.D00_PLUS_1);
        }
        assertEquals(count, messages.size());
    }

    private void feed(P25P1MessageFramer framer, int count)
    {
        for(int x = 0; x < count; x++)
        {
            framer.process(Dibit.D00_PLUS_1);
        }
    }

    private record TestEnergyProvider(boolean signalPresent) implements ISignalEnergyProvider
    {
        @Override
        public boolean isSignalPresent()
        {
            return signalPresent;
        }

        @Override
        public float getSignalEnergyLevel()
        {
            return signalPresent ? 1.0f : 0.0f;
        }
    }
}
