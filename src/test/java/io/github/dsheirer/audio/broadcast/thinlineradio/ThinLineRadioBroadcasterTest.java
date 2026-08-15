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
