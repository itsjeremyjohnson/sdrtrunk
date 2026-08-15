/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.audio.broadcast;

import io.github.dsheirer.audio.broadcast.zello.ZelloConfiguration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbstractAudioBroadcasterTest
{
    @Test
    void controlFlowStateChangesBeforeObservableNotification()
    {
        DeferredBroadcaster broadcaster = new DeferredBroadcaster();

        broadcaster.setBroadcastState(BroadcastState.INVALID_CREDENTIALS);

        assertEquals(BroadcastState.INVALID_CREDENTIALS, broadcaster.getBroadcastState());
        assertEquals(BroadcastState.READY, broadcaster.broadcastStateProperty().get());
        broadcaster.notifications.forEach(Runnable::run);
        assertEquals(BroadcastState.INVALID_CREDENTIALS, broadcaster.broadcastStateProperty().get());
    }

    private static class DeferredBroadcaster extends AbstractAudioBroadcaster<ZelloConfiguration>
    {
        private final List<Runnable> notifications = new ArrayList<>();

        private DeferredBroadcaster()
        {
            super(new ZelloConfiguration());
        }

        @Override
        protected void runOnJavaFxThreadIfAvailable(Runnable update)
        {
            notifications.add(update);
        }

        @Override public void start() {}
        @Override public void stop() {}
        @Override public void dispose() {}
        @Override public int getAudioQueueSize() { return 0; }
        @Override public void receive(AudioRecording recording) {}
    }
}
