/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.audio.broadcast.zello;

import io.github.dsheirer.alias.AliasModel;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZelloBroadcasterLifecycleTest
{
    @Test
    void retainsWorkAudioUntilStartStreamAcknowledgement() throws Exception
    {
        ZelloBroadcaster broadcaster = new ZelloBroadcaster(new ZelloConfiguration(), null, null, new AliasModel());
        setStreamActive(broadcaster, true);
        broadcaster.receiveRealTimeAudio(new float[480]);

        broadcaster.processAudioQueue();

        assertEquals(1, broadcaster.getAudioQueueSize());
        broadcaster.stop();
    }

    @Test
    void retainsConsumerAudioUntilStartStreamAcknowledgement() throws Exception
    {
        ZelloConsumerBroadcaster broadcaster = new ZelloConsumerBroadcaster(new ZelloConsumerConfiguration(), null,
            null, new AliasModel());
        setStreamActive(broadcaster, true);
        broadcaster.receiveRealTimeAudio(new float[480]);

        broadcaster.processAudioQueue();

        assertEquals(1, broadcaster.getAudioQueueSize());
        broadcaster.stop();
    }

    @Test
    void finalizesWorkCallWhenStartAcknowledgementArrivesAfterStop() throws Exception
    {
        ZelloConfiguration configuration = new ZelloConfiguration();
        configuration.setRelaxationTimeMs(0);
        ZelloBroadcaster broadcaster = new ZelloBroadcaster(configuration, null, null, new AliasModel());
        AtomicReference<String> lastText = new AtomicReference<>();
        setConnectionState(broadcaster, webSocket(new AtomicInteger(), lastText));
        setStreamActive(broadcaster, true);
        broadcaster.receiveRealTimeAudio(new float[10]);

        broadcaster.stopRealTimeStream();
        assertEquals(1, broadcaster.getAudioQueueSize());

        broadcaster.handleStartStreamAcknowledged(123);
        assertEquals(0, broadcaster.getAudioQueueSize());
        assertTrue(lastText.get().contains("stop_stream"));
        assertTrue(lastText.get().contains("123"));
        broadcaster.stop();
    }

    @Test
    void finalizesConsumerCallWhenStartAcknowledgementArrivesAfterStop() throws Exception
    {
        ZelloConsumerConfiguration configuration = new ZelloConsumerConfiguration();
        configuration.setRelaxationTimeMs(0);
        ZelloConsumerBroadcaster broadcaster = new ZelloConsumerBroadcaster(configuration, null, null, new AliasModel());
        AtomicReference<String> lastText = new AtomicReference<>();
        setConnectionState(broadcaster, webSocket(new AtomicInteger(), lastText));
        setStreamActive(broadcaster, true);
        broadcaster.receiveRealTimeAudio(new float[10]);

        broadcaster.stopRealTimeStream();
        assertEquals(1, broadcaster.getAudioQueueSize());

        broadcaster.handleStartStreamAcknowledged(456);
        assertEquals(0, broadcaster.getAudioQueueSize());
        assertTrue(lastText.get().contains("stop_stream"));
        assertTrue(lastText.get().contains("456"));
        broadcaster.stop();
    }

    @Test
    void rejectedWorkStartClearsPendingStopLatch() throws Exception
    {
        ZelloConfiguration configuration = new ZelloConfiguration();
        configuration.setRelaxationTimeMs(0);
        ZelloBroadcaster broadcaster = new ZelloBroadcaster(configuration, null, null, new AliasModel());
        setStreamActive(broadcaster, true);
        broadcaster.receiveRealTimeAudio(new float[10]);
        broadcaster.stopRealTimeStream();

        broadcaster.handleStartStreamRejected();

        assertFalse(atomicBoolean(broadcaster, "mStopPendingAcknowledgement").get());
        assertEquals(0, broadcaster.getAudioQueueSize());
        broadcaster.stop();
    }

    @Test
    void rejectedConsumerStartClearsPendingStopLatch() throws Exception
    {
        ZelloConsumerConfiguration configuration = new ZelloConsumerConfiguration();
        configuration.setRelaxationTimeMs(0);
        ZelloConsumerBroadcaster broadcaster = new ZelloConsumerBroadcaster(configuration, null, null,
            new AliasModel());
        setStreamActive(broadcaster, true);
        broadcaster.receiveRealTimeAudio(new float[10]);
        broadcaster.stopRealTimeStream();

        broadcaster.handleStartStreamRejected();

        assertFalse(atomicBoolean(broadcaster, "mStopPendingAcknowledgement").get());
        assertEquals(0, broadcaster.getAudioQueueSize());
        broadcaster.stop();
    }

    @Test
    void ignoresCloseFromSupersededWorkSocket() throws Exception
    {
        ZelloBroadcaster broadcaster = new ZelloBroadcaster(new ZelloConfiguration(), null, null, new AliasModel());
        WebSocket oldSocket = webSocket(new AtomicInteger());
        setConnectionState(broadcaster, webSocket(new AtomicInteger()));

        listener(broadcaster).onClose(oldSocket, WebSocket.NORMAL_CLOSURE, "old");

        assertTrue(atomicBoolean(broadcaster, "mConnected").get());
        broadcaster.stop();
    }

    @Test
    void ignoresCloseFromSupersededConsumerSocket() throws Exception
    {
        ZelloConsumerBroadcaster broadcaster = new ZelloConsumerBroadcaster(new ZelloConsumerConfiguration(), null,
            null, new AliasModel());
        WebSocket oldSocket = webSocket(new AtomicInteger());
        setConnectionState(broadcaster, webSocket(new AtomicInteger()));

        listener(broadcaster).onClose(oldSocket, WebSocket.NORMAL_CLOSURE, "old");

        assertTrue(atomicBoolean(broadcaster, "mConnected").get());
        broadcaster.stop();
    }

    @Test
    void closesWorkHandshakeCompletedAfterStop()
    {
        ZelloBroadcaster broadcaster = new ZelloBroadcaster(new ZelloConfiguration(), null, null, new AliasModel());
        broadcaster.stop();
        AtomicInteger closes = new AtomicInteger();

        broadcaster.handleWebSocketConnected(webSocket(closes));

        assertEquals(1, closes.get());
    }

    @Test
    void closesConsumerHandshakeCompletedAfterStop()
    {
        ZelloConsumerBroadcaster broadcaster = new ZelloConsumerBroadcaster(new ZelloConsumerConfiguration(), null,
            null, new AliasModel());
        broadcaster.stop();
        AtomicInteger closes = new AtomicInteger();

        broadcaster.handleWebSocketConnected(webSocket(closes));

        assertEquals(1, closes.get());
    }

    private static void setStreamActive(Object broadcaster, boolean active) throws Exception
    {
        Field field = broadcaster.getClass().getDeclaredField("mStreamActive");
        field.setAccessible(true);
        ((AtomicBoolean)field.get(broadcaster)).set(active);
    }

    private static void setConnectionState(Object broadcaster, WebSocket webSocket) throws Exception
    {
        Field socketField = broadcaster.getClass().getDeclaredField("mWebSocket");
        socketField.setAccessible(true);
        socketField.set(broadcaster, webSocket);
        atomicBoolean(broadcaster, "mConnected").set(true);
    }

    private static AtomicBoolean atomicBoolean(Object broadcaster, String name) throws Exception
    {
        Field field = broadcaster.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (AtomicBoolean)field.get(broadcaster);
    }

    private static WebSocket.Listener listener(Object broadcaster) throws Exception
    {
        Class<?> listenerClass = Class.forName(broadcaster.getClass().getName() + "$ZelloWebSocketListener");
        var constructor = listenerClass.getDeclaredConstructor(broadcaster.getClass());
        constructor.setAccessible(true);
        return (WebSocket.Listener)constructor.newInstance(broadcaster);
    }

    private static WebSocket webSocket(AtomicInteger closes)
    {
        return webSocket(closes, new AtomicReference<>());
    }

    private static WebSocket webSocket(AtomicInteger closes, AtomicReference<String> lastText)
    {
        return (WebSocket)Proxy.newProxyInstance(WebSocket.class.getClassLoader(), new Class[]{WebSocket.class},
            (proxy, method, args) -> {
                if(method.getName().equals("sendClose"))
                {
                    closes.incrementAndGet();
                }
                else if(method.getName().equals("sendText"))
                {
                    lastText.set((String)args[0]);
                }

                if(CompletableFuture.class.isAssignableFrom(method.getReturnType()))
                {
                    return CompletableFuture.completedFuture(proxy);
                }
                if(method.getReturnType() == boolean.class)
                {
                    return false;
                }
                if(method.getReturnType() == String.class)
                {
                    return "test-websocket";
                }
                return null;
            });
    }
}
