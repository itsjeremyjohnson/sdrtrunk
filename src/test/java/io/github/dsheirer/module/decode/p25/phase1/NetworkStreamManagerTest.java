/*
 * *****************************************************************************
 * Copyright (C) 2026 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * *****************************************************************************
 */
package io.github.dsheirer.module.decode.p25.phase1;

import io.github.dsheirer.audio.squelch.SquelchState;
import io.github.dsheirer.audio.squelch.SquelchStateEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkStreamManagerTest
{
    @Test
    void rejectsIdenticalEventAndRawPorts()
    {
        assertThrows(IllegalArgumentException.class, () -> NetworkStreamManager.getInstance(9500, 9500));
    }

    @Test
    void retriesNetworkStreamBindingAfterStartupFailure() throws IOException
    {
        int rawPort;
        try(ServerSocket available = new ServerSocket(0))
        {
            rawPort = available.getLocalPort();
        }

        try(ServerSocket occupied = new ServerSocket(0))
        {
            assertNull(NetworkStreamManager.getInstance(occupied.getLocalPort(), rawPort));
            int eventPort = occupied.getLocalPort();
            occupied.close();
            assertNotNull(NetworkStreamManager.getInstance(eventPort, rawPort));
        }
    }

    @Test
    void treatsPhaseOneSquelchCloseAsVoiceIdCallBoundary()
    {
        assertTrue(P25P1DecoderState.isCallBoundary(new SquelchStateEvent(SquelchState.SQUELCH, 0)));
        assertFalse(P25P1DecoderState.isCallBoundary(new SquelchStateEvent(SquelchState.UNSQUELCH, 0)));
        assertFalse(P25P1DecoderState.isCallBoundary(new SquelchStateEvent(SquelchState.SQUELCH, 1)));
    }

    @Test
    void disconnectsNetworkClientsWhenTheirQueuesOverflow() throws IOException
    {
        NetworkStreamManager.ClientWriter writer =
            new NetworkStreamManager.ClientWriter(new TestSocket(), 1, false);

        assertTrue(writer.offer("event"));
        assertFalse(writer.offer("next-event"));
        assertFalse(writer.isAlive());
    }

    @Test
    void disconnectsAudioClientsWhenTheirQueuesOverflow() throws IOException
    {
        ImbeStreamManager.ClientWriter imbeWriter =
            new ImbeStreamManager.ClientWriter(new TestSocket(), 1, false);
        PcmStreamManager.ClientWriter pcmWriter =
            new PcmStreamManager.ClientWriter(new TestSocket(), 1, false);

        imbeWriter.offer("frame");
        pcmWriter.offer("pcm");
        assertFalse(imbeWriter.offer("call_end"));
        assertFalse(pcmWriter.offer("call_end"));
        assertFalse(imbeWriter.isAlive());
        assertFalse(pcmWriter.isAlive());
    }

    @Test
    void replaysActiveImbeCallStartBeforeLiveFrames() throws IOException
    {
        ImbeStreamManager manager = new ImbeStreamManager();
        manager.broadcastCallStart("call-1", "call_start");

        ImbeStreamManager.ClientWriter writer =
            new ImbeStreamManager.ClientWriter(new TestSocket(), 4, false);
        manager.addClient(writer);
        manager.broadcast("frame");

        assertEquals("call_start", writer.poll());
        assertEquals("frame", writer.poll());

        manager.broadcastCallEnd("call-1", "call_end");
        ImbeStreamManager.ClientWriter nextWriter =
            new ImbeStreamManager.ClientWriter(new TestSocket(), 4, false);
        manager.addClient(nextWriter);
        assertNull(nextWriter.poll());
    }

    @Test
    void replaysActivePcmCallStartBeforeLiveAudio() throws IOException
    {
        PcmStreamManager manager = new PcmStreamManager();
        manager.broadcastCallStart("call-1", "system", "site", "1001", "1234", "timestamp");

        PcmStreamManager.ClientWriter writer =
            new PcmStreamManager.ClientWriter(new TestSocket(), 4, false);
        manager.addClient(writer);
        manager.broadcast("pcm");

        assertTrue(writer.poll().contains("\"type\":\"call_start\""));
        assertEquals("pcm", writer.poll());

        manager.broadcastCallEnd("call-1", "system", "site", "1001", 1);
        PcmStreamManager.ClientWriter nextWriter =
            new PcmStreamManager.ClientWriter(new TestSocket(), 4, false);
        manager.addClient(nextWriter);
        assertNull(nextWriter.poll());
    }

    @Test
    void retriesAudioStreamBindingAfterStartupFailure() throws IOException
    {
        try(ServerSocket occupiedImbe = new ServerSocket(0))
        {
            int port = occupiedImbe.getLocalPort();
            assertNull(ImbeStreamManager.getInstance(port));
            occupiedImbe.close();
            assertNotNull(ImbeStreamManager.getInstance(port));
        }

        try(ServerSocket occupiedPcm = new ServerSocket(0))
        {
            int port = occupiedPcm.getLocalPort();
            assertNull(PcmStreamManager.getInstance(port));
            occupiedPcm.close();
            assertNotNull(PcmStreamManager.getInstance(port));
        }
    }

    private static class TestSocket extends Socket
    {
        private final OutputStream mOutputStream = new ByteArrayOutputStream();

        @Override
        public OutputStream getOutputStream()
        {
            return mOutputStream;
        }
    }
}
