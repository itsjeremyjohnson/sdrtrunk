/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.audio.broadcast.zello;

import io.github.dsheirer.alias.AliasModel;
import io.github.dsheirer.audio.broadcast.BroadcastState;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
    void lateWorkStopErrorDoesNotClearSupersedingStream() throws Exception
    {
        ZelloBroadcaster broadcaster = new ZelloBroadcaster(new ZelloConfiguration(), null, null, new AliasModel());
        WebSocket webSocket = webSocket(new AtomicInteger());
        setConnectionState(broadcaster, webSocket);
        setStreamActive(broadcaster, true);
        atomicLong(broadcaster, "mCurrentStreamId").set(22);
        pendingCommands(broadcaster).put(7, "stop_stream(id=11)");

        listener(broadcaster).onText(webSocket, "{\"error\":\"invalid stream id\",\"seq\":7}", true);

        assertTrue(atomicBoolean(broadcaster, "mStreamActive").get());
        assertEquals(22, atomicLong(broadcaster, "mCurrentStreamId").get());
        broadcaster.stop();
    }

    @Test
    void lateConsumerStopErrorDoesNotClearSupersedingStream() throws Exception
    {
        ZelloConsumerBroadcaster broadcaster = new ZelloConsumerBroadcaster(new ZelloConsumerConfiguration(), null,
            null, new AliasModel());
        WebSocket webSocket = webSocket(new AtomicInteger());
        setConnectionState(broadcaster, webSocket);
        setStreamActive(broadcaster, true);
        atomicLong(broadcaster, "mCurrentStreamId").set(44);
        pendingCommands(broadcaster).put(8, "stop_stream(id=33)");

        listener(broadcaster).onText(webSocket, "{\"error\":\"failed to stop stream\",\"seq\":8}", true);

        assertTrue(atomicBoolean(broadcaster, "mStreamActive").get());
        assertEquals(44, atomicLong(broadcaster, "mCurrentStreamId").get());
        broadcaster.stop();
    }

    @Test
    void workStartAcknowledgementTimeoutRecoversConnection() throws Exception
    {
        ZelloBroadcaster broadcaster = new ZelloBroadcaster(new ZelloConfiguration(), null, null, new AliasModel());
        AtomicInteger closes = new AtomicInteger();
        setConnectionState(broadcaster, webSocket(closes));
        setStreamActive(broadcaster, true);
        atomicInteger(broadcaster, "mSessionEpoch").set(3);

        broadcaster.handleStartAcknowledgementTimeout(3);

        assertEquals(BroadcastState.TEMPORARY_BROADCAST_ERROR, broadcaster.getBroadcastState());
        assertFalse(atomicBoolean(broadcaster, "mStreamActive").get());
        assertFalse(atomicBoolean(broadcaster, "mConnected").get());
        assertEquals(1, closes.get());
        broadcaster.stop();
    }

    @Test
    void consumerStartAcknowledgementTimeoutRecoversConnection() throws Exception
    {
        ZelloConsumerBroadcaster broadcaster = new ZelloConsumerBroadcaster(new ZelloConsumerConfiguration(), null,
            null, new AliasModel());
        AtomicInteger closes = new AtomicInteger();
        setConnectionState(broadcaster, webSocket(closes));
        setStreamActive(broadcaster, true);
        atomicInteger(broadcaster, "mSessionEpoch").set(4);

        broadcaster.handleStartAcknowledgementTimeout(4);

        assertEquals(BroadcastState.TEMPORARY_BROADCAST_ERROR, broadcaster.getBroadcastState());
        assertFalse(atomicBoolean(broadcaster, "mStreamActive").get());
        assertFalse(atomicBoolean(broadcaster, "mConnected").get());
        assertEquals(1, closes.get());
        broadcaster.stop();
    }

    @Test
    void workChannelNotReadyErrorTriggersTransientReconnect() throws Exception
    {
        ZelloBroadcaster broadcaster = new ZelloBroadcaster(new ZelloConfiguration(), null, null, new AliasModel());
        AtomicInteger closes = new AtomicInteger();
        WebSocket webSocket = webSocket(closes);
        setConnectionState(broadcaster, webSocket);

        listener(broadcaster).onText(webSocket, "{\"error\":\"channel is not ready\"}", true);

        assertEquals(BroadcastState.TEMPORARY_BROADCAST_ERROR, broadcaster.getBroadcastState());
        assertFalse(atomicBoolean(broadcaster, "mConnected").get());
        assertEquals(1, closes.get());
        broadcaster.stop();
    }

    @Test
    void consumerNotConnectedErrorTriggersTransientReconnect() throws Exception
    {
        ZelloConsumerBroadcaster broadcaster = new ZelloConsumerBroadcaster(new ZelloConsumerConfiguration(), null,
            null, new AliasModel());
        AtomicInteger closes = new AtomicInteger();
        WebSocket webSocket = webSocket(closes);
        setConnectionState(broadcaster, webSocket);

        listener(broadcaster).onText(webSocket, "{\"error\":\"not connected\"}", true);

        assertEquals(BroadcastState.TEMPORARY_BROADCAST_ERROR, broadcaster.getBroadcastState());
        assertFalse(atomicBoolean(broadcaster, "mConnected").get());
        assertEquals(1, closes.get());
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

    @Test
    void acceptsWorkLogonResponseImmediatelyAfterOpen() throws Exception
    {
        ZelloBroadcaster broadcaster = new ZelloBroadcaster(new ZelloConfiguration(), null, null, new AliasModel());
        WebSocket webSocket = webSocket(new AtomicInteger());
        WebSocket.Listener listener = listener(broadcaster);

        listener.onOpen(webSocket);
        listener.onText(webSocket, "{\"success\":true}", true);

        assertTrue(atomicBoolean(broadcaster, "mConnected").get());
        broadcaster.stop();
    }

    @Test
    void acceptsConsumerLogonResponseImmediatelyAfterOpen() throws Exception
    {
        ZelloConsumerBroadcaster broadcaster = new ZelloConsumerBroadcaster(new ZelloConsumerConfiguration(), null,
            null, new AliasModel());
        WebSocket webSocket = webSocket(new AtomicInteger());
        WebSocket.Listener listener = listener(broadcaster);

        listener.onOpen(webSocket);
        listener.onText(webSocket, "{\"success\":true}", true);

        assertTrue(atomicBoolean(broadcaster, "mConnected").get());
        broadcaster.stop();
    }

    @Test
    void stopCancelsInactiveEncoderTasksInBothVariants() throws Exception
    {
        ZelloBroadcaster work = new ZelloBroadcaster(new ZelloConfiguration(), null, null, new AliasModel());
        ZelloConsumerBroadcaster consumer = new ZelloConsumerBroadcaster(new ZelloConsumerConfiguration(), null,
            null, new AliasModel());
        AtomicBoolean workCancelled = installFuture(work, "mEncoderFuture");
        AtomicBoolean consumerCancelled = installFuture(consumer, "mEncoderFuture");

        work.stop();
        consumer.stop();

        assertTrue(workCancelled.get());
        assertTrue(consumerCancelled.get());
    }

    @Test
    void credentialErrorsCloseSocketsAndRemainConfigurationErrors() throws Exception
    {
        ZelloBroadcaster work = new ZelloBroadcaster(new ZelloConfiguration(), null, null, new AliasModel());
        ZelloConsumerBroadcaster consumer = new ZelloConsumerBroadcaster(new ZelloConsumerConfiguration(), null,
            null, new AliasModel());
        AtomicInteger workCloses = new AtomicInteger();
        AtomicInteger consumerCloses = new AtomicInteger();
        WebSocket workSocket = webSocket(workCloses);
        WebSocket consumerSocket = webSocket(consumerCloses);
        setConnectionState(work, workSocket);
        setConnectionState(consumer, consumerSocket);
        AtomicBoolean workTimeoutCancelled = installFuture(work, "mConnectionTimeoutFuture");
        AtomicBoolean consumerTimeoutCancelled = installFuture(consumer, "mConnectionTimeoutFuture");
        AtomicBoolean workReconnectCancelled = installFuture(work, "mReconnectFuture");
        AtomicBoolean consumerReconnectCancelled = installFuture(consumer, "mReconnectFuture");

        listener(work).onText(workSocket, "{\"error\":\"invalid credentials\"}", true);
        listener(consumer).onText(consumerSocket, "{\"error\":\"invalid credentials\"}", true);

        assertEquals(BroadcastState.CONFIGURATION_ERROR, work.getBroadcastState());
        assertEquals(BroadcastState.CONFIGURATION_ERROR, consumer.getBroadcastState());
        assertEquals(1, workCloses.get());
        assertEquals(1, consumerCloses.get());
        assertTrue(workTimeoutCancelled.get());
        assertTrue(consumerTimeoutCancelled.get());
        assertTrue(workReconnectCancelled.get());
        assertTrue(consumerReconnectCancelled.get());
        work.stop();
        consumer.stop();
    }

    @Test
    void kickedErrorsClearPendingStreamStateInBothVariants() throws Exception
    {
        ZelloBroadcaster work = new ZelloBroadcaster(new ZelloConfiguration(), null, null, new AliasModel());
        ZelloConsumerBroadcaster consumer = new ZelloConsumerBroadcaster(new ZelloConsumerConfiguration(), null,
            null, new AliasModel());
        WebSocket workSocket = webSocket(new AtomicInteger());
        WebSocket consumerSocket = webSocket(new AtomicInteger());
        setConnectionState(work, workSocket);
        setConnectionState(consumer, consumerSocket);
        setStreamActive(work, true);
        setStreamActive(consumer, true);
        atomicBoolean(work, "mStopPendingAcknowledgement").set(true);
        atomicBoolean(consumer, "mStopPendingAcknowledgement").set(true);
        atomicLong(work, "mCurrentStreamId").set(10);
        atomicLong(consumer, "mCurrentStreamId").set(20);
        AtomicBoolean workWatchdogCancelled = installFuture(work, "mStartAcknowledgementWatchdogFuture");
        AtomicBoolean consumerWatchdogCancelled = installFuture(consumer, "mStartAcknowledgementWatchdogFuture");

        listener(work).onText(workSocket, "{\"command\":\"on_error\",\"error\":\"kicked\"}", true);
        listener(consumer).onText(consumerSocket, "{\"command\":\"on_error\",\"error\":\"kicked\"}", true);

        assertFalse(atomicBoolean(work, "mStreamActive").get());
        assertFalse(atomicBoolean(consumer, "mStreamActive").get());
        assertFalse(atomicBoolean(work, "mStopPendingAcknowledgement").get());
        assertFalse(atomicBoolean(consumer, "mStopPendingAcknowledgement").get());
        assertEquals(-1, atomicLong(work, "mCurrentStreamId").get());
        assertEquals(-1, atomicLong(consumer, "mCurrentStreamId").get());
        assertTrue(workWatchdogCancelled.get());
        assertTrue(consumerWatchdogCancelled.get());
        work.stop();
        consumer.stop();
    }

    private static AtomicBoolean installFuture(Object broadcaster, String name) throws Exception
    {
        AtomicBoolean cancelled = new AtomicBoolean();
        ScheduledFuture<?> future = (ScheduledFuture<?>)Proxy.newProxyInstance(ScheduledFuture.class.getClassLoader(),
            new Class[]{ScheduledFuture.class}, (proxy, method, args) ->
            {
                if(method.getName().equals("cancel"))
                {
                    cancelled.set(true);
                    return true;
                }
                if(method.getReturnType() == boolean.class)
                {
                    return false;
                }
                if(method.getReturnType() == long.class)
                {
                    return 0L;
                }
                if(method.getReturnType() == int.class)
                {
                    return 0;
                }
                return null;
            });
        Field field = broadcaster.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(broadcaster, future);
        return cancelled;
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

    private static AtomicLong atomicLong(Object broadcaster, String name) throws Exception
    {
        Field field = broadcaster.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (AtomicLong)field.get(broadcaster);
    }

    private static AtomicInteger atomicInteger(Object broadcaster, String name) throws Exception
    {
        Field field = broadcaster.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (AtomicInteger)field.get(broadcaster);
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<Integer,String> pendingCommands(Object broadcaster) throws Exception
    {
        Field field = broadcaster.getClass().getDeclaredField("mPendingCommands");
        field.setAccessible(true);
        return (ConcurrentHashMap<Integer,String>)field.get(broadcaster);
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
