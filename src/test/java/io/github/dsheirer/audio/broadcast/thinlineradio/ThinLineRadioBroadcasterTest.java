/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.audio.broadcast.thinlineradio;

import io.github.dsheirer.alias.AliasModel;
import io.github.dsheirer.audio.broadcast.AudioRecording;
import io.github.dsheirer.audio.broadcast.BroadcastState;
import io.github.dsheirer.identifier.IdentifierCollection;
import io.github.dsheirer.identifier.MutableIdentifierCollection;
import io.github.dsheirer.module.decode.p25.identifier.talkgroup.APCO25Talkgroup;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThinLineRadioBroadcasterTest
{
    @Test
    void releasesExpiredRecordingAfterPolling()
    {
        ThinLineRadioConfiguration configuration = new ThinLineRadioConfiguration();
        configuration.setMaximumRecordingAge(100);
        ThinLineRadioBroadcaster broadcaster = broadcaster(configuration);
        AudioRecording recording = recording(System.currentTimeMillis() - 101, 1000);

        broadcaster.receive(recording);
        broadcaster.processRecordingQueue();

        assertFalse(recording.hasPendingReplays());
        assertEquals(0, broadcaster.getAudioQueueSize());
        assertEquals(1, broadcaster.getAgedOffAudioCount());
    }

    @Test
    void retainsTransientUploadFailureWithinMaximumAge()
    {
        ThinLineRadioConfiguration configuration = new ThinLineRadioConfiguration();
        configuration.setMaximumRecordingAge(10_000);
        ThinLineRadioBroadcaster broadcaster = broadcaster(configuration);
        AudioRecording recording = recording(System.currentTimeMillis(), 1000);

        broadcaster.retryOrAgeOff(recording);

        assertTrue(recording.hasPendingReplays());
        assertEquals(1, broadcaster.getAudioQueueSize());
    }

    @Test
    void releasesZeroLengthRecordingAfterPollingWhileDisconnected() throws Exception
    {
        ThinLineRadioConfiguration configuration = new ThinLineRadioConfiguration();
        configuration.setMaximumRecordingAge(10_000);
        ThinLineRadioBroadcaster broadcaster = new ThinLineRadioBroadcaster(configuration, null, null, new AliasModel());
        broadcaster.setBroadcastState(BroadcastState.ERROR);
        Field lastAttempt = ThinLineRadioBroadcaster.class.getDeclaredField("mLastConnectionAttempt");
        lastAttempt.setAccessible(true);
        lastAttempt.setLong(broadcaster, System.currentTimeMillis());
        AudioRecording recording = recording(System.currentTimeMillis(), 0);

        broadcaster.receive(recording);
        broadcaster.processRecordingQueue();

        assertFalse(recording.hasPendingReplays());
        assertEquals(0, broadcaster.getAudioQueueSize());
        assertEquals(1, broadcaster.getAudioErrorCount());
    }

    @Test
    void missingAliasListDoesNotStopPerRecordingQueueProcessing()
    {
        ThinLineRadioConfiguration configuration = new ThinLineRadioConfiguration();
        configuration.setMaximumRecordingAge(10_000);
        ThinLineRadioBroadcaster broadcaster = broadcaster(configuration);
        MutableIdentifierCollection identifiers = new MutableIdentifierCollection();
        identifiers.update(APCO25Talkgroup.create(100));
        AudioRecording missingAliasList = recording(Path.of("test_1_call.mp3"), identifiers,
            System.currentTimeMillis(), 1000);
        AudioRecording followingRecording = recording(System.currentTimeMillis(), 0);

        broadcaster.receive(missingAliasList);
        broadcaster.receive(followingRecording);
        broadcaster.processRecordingQueue();

        assertFalse(missingAliasList.hasPendingReplays());
        assertFalse(followingRecording.hasPendingReplays());
        assertEquals(0, broadcaster.getAudioQueueSize());
        assertEquals(2, broadcaster.getAudioErrorCount());
    }

    @Test
    void disposalReleasesInFlightRecording() throws Exception
    {
        ThinLineRadioBroadcaster broadcaster = broadcaster(new ThinLineRadioConfiguration());
        AudioRecording recording = recording(System.currentTimeMillis(), 1000);
        Field field = ThinLineRadioBroadcaster.class.getDeclaredField("mInFlightRecording");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<AudioRecording> inFlight = (AtomicReference<AudioRecording>)field.get(broadcaster);
        inFlight.set(recording);

        broadcaster.dispose();

        assertFalse(recording.hasPendingReplays());
    }

    @Test
    void stopCancelsAndRequeuesInFlightRecording() throws Exception
    {
        ThinLineRadioConfiguration configuration = new ThinLineRadioConfiguration();
        configuration.setHost("http://localhost/upload");
        configuration.setMaximumRecordingAge(10_000);
        TestThinLineBroadcaster broadcaster = new TestThinLineBroadcaster(configuration);
        broadcaster.setBroadcastState(BroadcastState.CONNECTED);
        Path path = Files.createTempFile("test_1_", "call.mp3");
        Files.write(path, new byte[]{1});
        AudioRecording recording = recording(path, new IdentifierCollection(), System.currentTimeMillis(), 1000);

        try
        {
            broadcaster.receive(recording);
            broadcaster.processRecordingQueue();
            broadcaster.stop();

            assertTrue(recording.hasPendingReplays());
            assertEquals(1, broadcaster.getAudioQueueSize());
        }
        finally
        {
            broadcaster.dispose();
            Files.deleteIfExists(path);
        }
    }

    @Test
    void uploadsOneRecordingAtATimeAndAdvancesFromCompletion() throws Exception
    {
        ThinLineRadioConfiguration configuration = new ThinLineRadioConfiguration();
        configuration.setHost("http://localhost/upload");
        configuration.setMaximumRecordingAge(10_000);
        TestThinLineBroadcaster broadcaster = new TestThinLineBroadcaster(configuration);
        broadcaster.setBroadcastState(BroadcastState.CONNECTED);
        setAtomicBoolean(broadcaster, "mRunning", true);
        Path firstPath = Files.createTempFile("test_1_", "call.mp3");
        Path secondPath = Files.createTempFile("test_2_", "call.mp3");
        Files.write(firstPath, new byte[]{1});
        Files.write(secondPath, new byte[]{2});
        AudioRecording first = recording(firstPath, new IdentifierCollection(), System.currentTimeMillis(), 1000);
        AudioRecording second = recording(secondPath, new IdentifierCollection(), System.currentTimeMillis(), 1000);

        try
        {
            broadcaster.receive(first);
            broadcaster.receive(second);
            broadcaster.processRecordingQueue();

            assertEquals(1, broadcaster.sendCount.get());
            assertEquals(1, broadcaster.getAudioQueueSize());

            broadcaster.completeNext("Call imported successfully.");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while(broadcaster.sendCount.get() < 2 && System.nanoTime() < deadline)
            {
                Thread.onSpinWait();
            }

            assertEquals(2, broadcaster.sendCount.get());
            broadcaster.completeNext("Call imported successfully.");
            assertFalse(first.hasPendingReplays());
            assertFalse(second.hasPendingReplays());
        }
        finally
        {
            broadcaster.dispose();
            Files.deleteIfExists(firstPath);
            Files.deleteIfExists(secondPath);
        }
    }

    private static void setAtomicBoolean(Object target, String name, boolean value) throws Exception
    {
        Field field = ThinLineRadioBroadcaster.class.getDeclaredField(name);
        field.setAccessible(true);
        ((AtomicBoolean)field.get(target)).set(value);
    }

    private static class TestThinLineBroadcaster extends ThinLineRadioBroadcaster
    {
        private final AtomicInteger sendCount = new AtomicInteger();
        private final List<CompletableFuture<HttpResponse<String>>> responses = new ArrayList<>();

        private TestThinLineBroadcaster(ThinLineRadioConfiguration configuration)
        {
            super(configuration, null, null, new AliasModel());
        }

        @Override
        CompletableFuture<HttpResponse<String>> send(HttpRequest request)
        {
            sendCount.incrementAndGet();
            CompletableFuture<HttpResponse<String>> response = new CompletableFuture<>();
            responses.add(response);
            return response;
        }

        private void completeNext(String body)
        {
            CompletableFuture<HttpResponse<String>> response = responses.removeFirst();
            @SuppressWarnings("unchecked")
            HttpResponse<String> httpResponse = (HttpResponse<String>)Proxy.newProxyInstance(
                HttpResponse.class.getClassLoader(), new Class[]{HttpResponse.class}, (proxy, method, args) ->
                {
                    if(method.getName().equals("statusCode"))
                    {
                        return 200;
                    }
                    if(method.getName().equals("body"))
                    {
                        return body;
                    }
                    return null;
                });
            response.complete(httpResponse);
        }
    }

    private static ThinLineRadioBroadcaster broadcaster(ThinLineRadioConfiguration configuration)
    {
        ThinLineRadioBroadcaster broadcaster = new ThinLineRadioBroadcaster(configuration, null, null, new AliasModel());
        broadcaster.setBroadcastState(BroadcastState.CONNECTED);
        return broadcaster;
    }

    private static AudioRecording recording(long start, long length)
    {
        return recording(Path.of("test.mp3"), new IdentifierCollection(), start, length);
    }

    private static AudioRecording recording(Path path, IdentifierCollection identifiers, long start, long length)
    {
        AudioRecording recording = new AudioRecording(path, List.of(), identifiers, start, length);
        recording.addPendingReplay();
        return recording;
    }
}
