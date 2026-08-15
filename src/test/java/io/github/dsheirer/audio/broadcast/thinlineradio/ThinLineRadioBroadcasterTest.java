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
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    private static ThinLineRadioBroadcaster broadcaster(ThinLineRadioConfiguration configuration)
    {
        ThinLineRadioBroadcaster broadcaster = new ThinLineRadioBroadcaster(configuration, null, null, new AliasModel());
        broadcaster.setBroadcastState(BroadcastState.CONNECTED);
        return broadcaster;
    }

    private static AudioRecording recording(long start, long length)
    {
        AudioRecording recording = new AudioRecording(Path.of("test.mp3"), List.of(), new IdentifierCollection(), start,
            length);
        recording.addPendingReplay();
        return recording;
    }
}
