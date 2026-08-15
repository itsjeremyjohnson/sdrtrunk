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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private static WebSocket webSocket(AtomicInteger closes)
    {
        return (WebSocket)Proxy.newProxyInstance(WebSocket.class.getClassLoader(), new Class[]{WebSocket.class},
            (proxy, method, args) -> {
                if(method.getName().equals("sendClose"))
                {
                    closes.incrementAndGet();
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
