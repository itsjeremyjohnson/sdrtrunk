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
import io.github.dsheirer.channel.state.DecoderStateEvent;
import io.github.dsheirer.channel.state.State;
import io.github.dsheirer.controller.channel.Channel;
import io.github.dsheirer.message.SyncLossMessage;
import io.github.dsheirer.module.decode.p25.phase1.message.P25P1Message;
import io.github.dsheirer.module.decode.p25.phase1.message.UnknownP25Message;
import io.github.dsheirer.module.decode.p25.phase1.message.lc.LinkControlWord;
import io.github.dsheirer.module.decode.p25.phase1.message.lc.UnknownLinkControlWord;
import io.github.dsheirer.module.decode.p25.phase1.message.ldu.LDU1Message;
import io.github.dsheirer.protocol.Protocol;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class P25P1DecoderStateTest
{
    @Test
    void encryptedStateRequiresConsecutiveConfirmation()
    {
        P25P1DecoderState state = createState(new ArrayList<>());
        int threshold = state.getEncryptionConfirmationThreshold();

        for(int x = 1; x < threshold; x++)
        {
            assertFalse(state.updateEncryptionState(true));
        }

        assertTrue(state.updateEncryptionState(true));
        assertFalse(state.updateEncryptionState(false));
        state.stop();
    }

    @Test
    void invalidMetadataFallbackPreservesEncryptionState() throws Exception
    {
        List<DecoderStateEvent> events = new ArrayList<>();
        P25P1DecoderState state = createState(events);
        confirmEncryptedCall(state);
        UnknownP25Message invalidMetadataLdu = new UnknownP25Message(new CorrectedBinaryMessage(1), 0,
                System.currentTimeMillis(), P25P1DataUnitID.LOGICAL_LINK_DATA_UNIT_1);

        invokeProcessLdu(state, invalidMetadataLdu);

        assertEquals(1, events.size());
        assertEquals(State.ENCRYPTED, events.getFirst().getState());
        state.stop();
    }

    @Test
    void validLdu1ContinuationPreservesEncryptionState() throws Exception
    {
        List<DecoderStateEvent> events = new ArrayList<>();
        P25P1DecoderState state = createState(events);
        confirmEncryptedCall(state);

        invokeProcessLdu(state, new ValidLdu1Message(System.currentTimeMillis()));

        assertEquals(1, events.size());
        assertEquals(State.ENCRYPTED, events.getFirst().getState());
        state.stop();
    }

    @Test
    void activeSignalDuidCorrectionAdvancesContinuityWithoutMetadata() throws Exception
    {
        List<DecoderStateEvent> events = new ArrayList<>();
        P25P1DecoderState state = createState(events);
        long timestamp = System.currentTimeMillis();
        ValidLdu1Message correctedLdu = new ValidLdu1Message(timestamp);
        correctedLdu.setDuidCorrected(true);
        correctedLdu.setDuidCorrectedDuringActiveSignal(true);

        invokeProcessLdu(state, correctedLdu);

        assertEquals(1, events.size());
        assertEquals(State.CALL, events.getFirst().getState());
        assertEquals(timestamp, getLastValidLduTimestamp(state));
        state.stop();
    }

    @Test
    void inactiveSignalDuidCorrectionDoesNotAdvanceContinuity() throws Exception
    {
        List<DecoderStateEvent> events = new ArrayList<>();
        P25P1DecoderState state = createState(events);
        UnknownP25Message correctedLdu = new UnknownP25Message(new CorrectedBinaryMessage(1), 0,
                System.currentTimeMillis(), P25P1DataUnitID.LOGICAL_LINK_DATA_UNIT_1);
        correctedLdu.setDuidCorrected(true);

        invokeProcessLdu(state, correctedLdu);

        assertEquals(0, events.size());
        assertEquals(0, getLastValidLduTimestamp(state));
        state.stop();
    }

    @Test
    void holdoverContinuationsPreserveEncryptionState() throws Exception
    {
        List<DecoderStateEvent> events = new ArrayList<>();
        P25P1DecoderState state = createState(events);
        state.setSignalEnergyProvider(new ConstantEnergyProvider());
        state.setHoldoverMs(1000);
        confirmEncryptedCall(state);
        long timestamp = System.currentTimeMillis();
        setLastValidLduTimestamp(state, timestamp);

        invokeCheckAndApplyHoldover(state, timestamp + 1);
        state.receive(new SyncLossMessage(timestamp + 1, 9600, Protocol.APCO25));
        invokePeriodicHoldoverCheck(state);

        assertEquals(3, events.size());
        assertTrue(events.stream().allMatch(event -> event.getState() == State.ENCRYPTED));
        state.stop();
    }

    @Test
    void clearCallFallbackRemainsClear() throws Exception
    {
        List<DecoderStateEvent> events = new ArrayList<>();
        P25P1DecoderState state = createState(events);
        UnknownP25Message invalidMetadataLdu = new UnknownP25Message(new CorrectedBinaryMessage(1), 0,
                System.currentTimeMillis(), P25P1DataUnitID.LOGICAL_LINK_DATA_UNIT_1);

        invokeProcessLdu(state, invalidMetadataLdu);

        assertEquals(1, events.size());
        assertEquals(State.CALL, events.getFirst().getState());
        state.stop();
    }

    @Test
    void requestResetClearsEncryptionConfirmation()
    {
        P25P1DecoderState state = createState(new ArrayList<>());
        confirmEncryptedCall(state);

        state.receiveDecoderStateEvent(new DecoderStateEvent(this, DecoderStateEvent.Event.REQUEST_RESET, State.IDLE));

        assertFalse(state.updateEncryptionState(true));
        state.stop();
    }

    @Test
    void requestResetClearsHoldoverTimestamp() throws Exception
    {
        P25P1DecoderState state = createState(new ArrayList<>());
        setLastValidLduTimestamp(state, System.currentTimeMillis());

        state.receiveDecoderStateEvent(new DecoderStateEvent(this, DecoderStateEvent.Event.REQUEST_RESET, State.IDLE));

        assertEquals(0, getLastValidLduTimestamp(state));
        state.stop();
    }

    @Test
    void zeroHoldoverDisablesSyncLossAndPeriodicContinuations() throws Exception
    {
        List<DecoderStateEvent> events = new ArrayList<>();
        P25P1DecoderState state = createState(events);
        state.setSignalEnergyProvider(new ConstantEnergyProvider());
        state.setHoldoverMs(0);
        setLastValidLduTimestamp(state, System.currentTimeMillis());

        state.receive(new SyncLossMessage(System.currentTimeMillis(), 9600, Protocol.APCO25));
        invokePeriodicHoldoverCheck(state);

        assertEquals(0, events.size());
        state.stop();
    }

    @Test
    void configuredHoldoverDurationHasNoExtraGrace() throws Exception
    {
        List<DecoderStateEvent> events = new ArrayList<>();
        P25P1DecoderState state = createState(events);
        state.setSignalEnergyProvider(new ConstantEnergyProvider());
        state.setHoldoverMs(100);
        long timestamp = System.currentTimeMillis() - 300;
        setLastValidLduTimestamp(state, timestamp);

        state.receive(new SyncLossMessage(System.currentTimeMillis(), 9600, Protocol.APCO25));
        invokePeriodicHoldoverCheck(state);

        assertEquals(0, events.size());
        state.stop();
    }

    @Test
    void invalidTdulcDoesNotClearCallHoldover() throws Exception
    {
        P25P1DecoderState state = createState(new ArrayList<>());
        long timestamp = System.currentTimeMillis();
        setLastValidLduTimestamp(state, timestamp);

        state.processTDULC(null, null);

        assertEquals(timestamp, getLastValidLduTimestamp(state));
        state.stop();
    }

    @Test
    void ignoredCqpskTduPreservesEncryptedCall() throws Exception
    {
        List<DecoderStateEvent> events = new ArrayList<>();
        P25P1DecoderState state = createState(events, Modulation.CQPSK);
        confirmEncryptedCall(state);
        UnknownP25Message tdu = new UnknownP25Message(new CorrectedBinaryMessage(1), 0,
                System.currentTimeMillis(), P25P1DataUnitID.TERMINATOR_DATA_UNIT);

        invokeProcessTdu(state, tdu);

        assertFalse(state.isTDUCallEnd());
        assertTrue(state.updateEncryptionState(true));
        assertEquals(State.ACTIVE, events.getLast().getState());
        state.stop();
    }

    @Test
    void endedCallIsNotReopenedAfterCarrierCheck() throws Exception
    {
        List<DecoderStateEvent> events = new ArrayList<>();
        P25P1DecoderState state = createState(events);
        BlockingEnergyProvider energyProvider = new BlockingEnergyProvider();
        state.setSignalEnergyProvider(energyProvider);
        state.setHoldoverMs(1000);
        setLastValidLduTimestamp(state, System.currentTimeMillis());

        Thread check = Thread.ofPlatform().start(() -> invokePeriodicHoldoverCheckUnchecked(state));
        assertTrue(energyProvider.awaitCheck());
        setLastValidLduTimestamp(state, 0);
        energyProvider.release();
        check.join();

        assertEquals(0, events.size());
        state.stop();
    }

    private P25P1DecoderState createState(List<DecoderStateEvent> events)
    {
        return createState(events, Modulation.C4FM);
    }

    private P25P1DecoderState createState(List<DecoderStateEvent> events, Modulation modulation)
    {
        Channel channel = new Channel("Holdover Test");
        DecodeConfigP25Phase1 configuration = new DecodeConfigP25Phase1();
        configuration.setModulation(modulation);
        channel.setDecodeConfiguration(configuration);
        P25P1DecoderState state = new P25P1DecoderState(channel);
        state.setDecoderStateListener(events::add);
        return state;
    }

    private void confirmEncryptedCall(P25P1DecoderState state)
    {
        for(int x = 0; x < state.getEncryptionConfirmationThreshold(); x++)
        {
            state.updateEncryptionState(true);
        }
    }

    private void invokeProcessLdu(P25P1DecoderState state, P25P1Message message) throws Exception
    {
        Method method = P25P1DecoderState.class.getDeclaredMethod("processLDU", P25P1Message.class);
        method.setAccessible(true);
        method.invoke(state, message);
    }

    private void invokeProcessTdu(P25P1DecoderState state, P25P1Message message) throws Exception
    {
        Method method = P25P1DecoderState.class.getDeclaredMethod("processTDU", P25P1Message.class);
        method.setAccessible(true);
        method.invoke(state, message);
    }

    private void setLastValidLduTimestamp(P25P1DecoderState state, long timestamp) throws Exception
    {
        Field field = P25P1DecoderState.class.getDeclaredField("mLastValidLDUTimestamp");
        field.setAccessible(true);
        field.setLong(state, timestamp);
    }

    private long getLastValidLduTimestamp(P25P1DecoderState state) throws Exception
    {
        Field field = P25P1DecoderState.class.getDeclaredField("mLastValidLDUTimestamp");
        field.setAccessible(true);
        return field.getLong(state);
    }

    private void invokeCheckAndApplyHoldover(P25P1DecoderState state, long timestamp) throws Exception
    {
        Method method = P25P1DecoderState.class.getDeclaredMethod("checkAndApplyHoldover", long.class);
        method.setAccessible(true);
        method.invoke(state, timestamp);
    }

    private void invokePeriodicHoldoverCheck(P25P1DecoderState state) throws Exception
    {
        Method method = P25P1DecoderState.class.getDeclaredMethod("periodicHoldoverCheck");
        method.setAccessible(true);
        method.invoke(state);
    }

    private void invokePeriodicHoldoverCheckUnchecked(P25P1DecoderState state)
    {
        try
        {
            invokePeriodicHoldoverCheck(state);
        }
        catch(Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private static class ValidLdu1Message extends LDU1Message
    {
        private final LinkControlWord mLinkControlWord;

        ValidLdu1Message(long timestamp)
        {
            super(new CorrectedBinaryMessage(1568), 0, timestamp);
            CorrectedBinaryMessage linkControlMessage = new CorrectedBinaryMessage(72);
            linkControlMessage.load(2, 6, 9); //SOURCE_ID_EXTENSION does not independently broadcast call state
            mLinkControlWord = new UnknownLinkControlWord(linkControlMessage);
            mLinkControlWord.setValid(true);
        }

        @Override
        public LinkControlWord getLinkControlWord()
        {
            return mLinkControlWord;
        }
    }

    private static class ConstantEnergyProvider implements ISignalEnergyProvider
    {
        @Override
        public boolean isSignalPresent()
        {
            return true;
        }

        @Override
        public float getSignalEnergyLevel()
        {
            return 1.0f;
        }
    }

    private static class BlockingEnergyProvider extends ConstantEnergyProvider
    {
        private final CountDownLatch mChecked = new CountDownLatch(1);
        private final CountDownLatch mRelease = new CountDownLatch(1);

        @Override
        public boolean isSignalPresent()
        {
            mChecked.countDown();

            try
            {
                return mRelease.await(5, TimeUnit.SECONDS);
            }
            catch(InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        boolean awaitCheck() throws InterruptedException
        {
            return mChecked.await(5, TimeUnit.SECONDS);
        }

        void release()
        {
            mRelease.countDown();
        }
    }
}
