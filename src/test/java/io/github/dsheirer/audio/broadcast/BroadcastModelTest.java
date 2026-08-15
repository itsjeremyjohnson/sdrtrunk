/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.audio.broadcast;

import io.github.dsheirer.audio.broadcast.broadcastify.BroadcastifyCallConfiguration;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BroadcastModelTest
{
    @Test
    void reconnectPreservesBroadcasterAndQueuedRecording() throws InterruptedException
    {
        BroadcastifyCallConfiguration configuration = new BroadcastifyCallConfiguration();
        configuration.setEnabled(true);
        ConfiguredBroadcast configuredBroadcast = new ConfiguredBroadcast(configuration);
        AudioRecording recording = new AudioRecording(Path.of("queued.mp3"), List.of(), null, 0, 0);
        recording.addPendingReplay();
        TestBroadcaster broadcaster = new TestBroadcaster(configuration, recording);
        configuredBroadcast.setAudioBroadcaster(broadcaster);
        BroadcastModel model = new BroadcastModel(null, null, null);
        model.getConfiguredBroadcasts().add(configuredBroadcast);

        model.process(new BroadcastEvent(configuration, BroadcastEvent.Event.CONFIGURATION_RECONNECT));

        assertTrue(broadcaster.awaitRestart());
        assertSame(broadcaster, configuredBroadcast.getAudioBroadcaster());
        assertEquals(1, broadcaster.getAudioQueueSize());
        assertTrue(recording.hasPendingReplays());
        assertEquals(0, broadcaster.getDisposeCount());
    }

    private static class TestBroadcaster extends AbstractAudioBroadcaster<BroadcastifyCallConfiguration>
    {
        private final AudioRecording mRecording;
        private final CountDownLatch mRestarted = new CountDownLatch(1);
        private int mDisposeCount;

        TestBroadcaster(BroadcastifyCallConfiguration configuration, AudioRecording recording)
        {
            super(configuration);
            mRecording = recording;
        }

        @Override
        public void start()
        {
            mRestarted.countDown();
        }

        @Override
        public void stop()
        {
        }

        @Override
        public void dispose()
        {
            mDisposeCount++;
            mRecording.removePendingReplay();
        }

        @Override
        public int getAudioQueueSize()
        {
            return mRecording.hasPendingReplays() ? 1 : 0;
        }

        @Override
        public void receive(AudioRecording recording)
        {
        }

        boolean awaitRestart() throws InterruptedException
        {
            return mRestarted.await(2, TimeUnit.SECONDS);
        }

        int getDisposeCount()
        {
            return mDisposeCount;
        }
    }
}
