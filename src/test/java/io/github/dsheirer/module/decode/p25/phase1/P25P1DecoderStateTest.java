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

import io.github.dsheirer.channel.state.DecoderStateEvent;
import io.github.dsheirer.controller.channel.Channel;
import io.github.dsheirer.message.SyncLossMessage;
import io.github.dsheirer.protocol.Protocol;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class P25P1DecoderStateTest
{
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
        Channel channel = new Channel("Holdover Test");
        channel.setDecodeConfiguration(new DecodeConfigP25Phase1());
        P25P1DecoderState state = new P25P1DecoderState(channel);
        state.setDecoderStateListener(events::add);
        return state;
    }

    private void setLastValidLduTimestamp(P25P1DecoderState state, long timestamp) throws Exception
    {
        Field field = P25P1DecoderState.class.getDeclaredField("mLastValidLDUTimestamp");
        field.setAccessible(true);
        field.setLong(state, timestamp);
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
