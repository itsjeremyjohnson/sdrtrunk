/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * ****************************************************************************
 */

package io.github.dsheirer.record;

import io.github.dsheirer.alias.AliasList;
import io.github.dsheirer.alias.id.priority.Priority;
import io.github.dsheirer.audio.AudioSegment;
import io.github.dsheirer.dsp.oscillator.ScalarRealOscillator;
import io.github.dsheirer.identifier.configuration.ChannelNameConfigurationIdentifier;
import io.github.dsheirer.identifier.configuration.FrequencyConfigurationIdentifier;
import io.github.dsheirer.identifier.configuration.SystemConfigurationIdentifier;
import io.github.dsheirer.message.TimeslotMessage;
import io.github.dsheirer.module.decode.dmr.identifier.DMRTalkgroup;
import io.github.dsheirer.module.decode.nbfm.NBFMTalkgroup;
import io.github.dsheirer.module.decode.p25.identifier.talkgroup.APCO25Talkgroup;
import io.github.dsheirer.preference.UserPreferences;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveAudioRecordingManagerTest
{
    @TempDir
    Path mTempDir;

    private UserPreferences mUserPreferences;
    private Path mOriginalRecordingDirectory;

    @BeforeEach
    void setup()
    {
        mUserPreferences = new UserPreferences();
        mOriginalRecordingDirectory = mUserPreferences.getDirectoryPreference().getDirectoryRecording();
        mUserPreferences.getDirectoryPreference().setDirectoryRecording(mTempDir);
        mUserPreferences.getCallManagementPreference().setDuplicatePlaybackSuppressionEnabled(true);
    }

    @AfterEach
    void cleanup()
    {
        if(mOriginalRecordingDirectory != null)
        {
            mUserPreferences.getDirectoryPreference().setDirectoryRecording(mOriginalRecordingDirectory);
        }
    }

    @Test
    void recordsOneMp3PerTalkgroupForSession() throws IOException
    {
        LiveAudioRecordingManager manager = new LiveAudioRecordingManager(mUserPreferences);

        manager.startRecording();
        receive(manager, talkgroupSegment(100));
        receive(manager, talkgroupSegment(100));
        receive(manager, talkgroupSegment(200));
        manager.processAudioSegments();
        manager.stopRecording();

        List<Path> recordings = recordings();

        assertEquals(2, recordings.size());
        assertTrue(recordings.stream().anyMatch(path -> path.getFileName().toString().contains("TO-100")));
        assertTrue(recordings.stream().anyMatch(path -> path.getFileName().toString().contains("TO-200")));
        assertTrue(recordings.stream().allMatch(path -> {
            try
            {
                return Files.size(path) > 0;
            }
            catch(IOException ioe)
            {
                return false;
            }
        }));
    }

    @Test
    void sameDisplayTalkgroupAcrossProtocolsUsesDistinctFilenames() throws IOException
    {
        LiveAudioRecordingManager manager = new LiveAudioRecordingManager(mUserPreferences);

        manager.startRecording();
        receive(manager, talkgroupSegment(100));
        AudioSegment dmr = baseSegment();
        dmr.addIdentifier(new DMRTalkgroup(100));
        dmr.completeProperty().set(true);
        receive(manager, dmr);
        manager.processAudioSegments();
        manager.stopRecording();

        List<Path> recordings = recordings();
        assertEquals(2, recordings.size());
        assertTrue(recordings.stream().anyMatch(path -> path.getFileName().toString().contains("APCO-25")));
        assertTrue(recordings.stream().anyMatch(path -> path.getFileName().toString().contains("DMR")));
    }

    @Test
    void skipsEncryptedDuplicateAndDoNotMonitorSegments() throws IOException
    {
        LiveAudioRecordingManager manager = new LiveAudioRecordingManager(mUserPreferences);

        AudioSegment duplicate = talkgroupSegment(101);
        duplicate.setDuplicate(true);

        AudioSegment encrypted = talkgroupSegment(102);
        encrypted.encryptedProperty().set(true);

        AudioSegment doNotMonitor = talkgroupSegment(103);
        doNotMonitor.monitorPriorityProperty().set(Priority.DO_NOT_MONITOR);

        manager.startRecording();
        receive(manager, talkgroupSegment(100));
        receive(manager, duplicate);
        receive(manager, encrypted);
        receive(manager, doNotMonitor);
        manager.processAudioSegments();
        manager.stopRecording();

        List<Path> recordings = recordings();

        assertEquals(1, recordings.size());
        assertTrue(recordings.get(0).getFileName().toString().contains("TO-100"));
    }

    @Test
    void recordsConventionalAudioByChannelWhenNoTalkgroup() throws IOException
    {
        LiveAudioRecordingManager manager = new LiveAudioRecordingManager(mUserPreferences);

        manager.startRecording();
        receive(manager, conventionalSegment());
        manager.processAudioSegments();
        manager.stopRecording();

        List<Path> recordings = recordings();

        assertEquals(1, recordings.size());
        assertTrue(recordings.get(0).getFileName().toString().contains("CHANNEL-NFM-Test"));
    }

    @Test
    void conventionalAnalogTalkgroupsRemainDistinctByChannel() throws IOException
    {
        LiveAudioRecordingManager manager = new LiveAudioRecordingManager(mUserPreferences);
        AudioSegment first = baseSegment("NFM One", 154_920_000L);
        first.addIdentifier(new NBFMTalkgroup(1));
        first.completeProperty().set(true);
        AudioSegment second = baseSegment("NFM Two", 155_640_000L);
        second.addIdentifier(new NBFMTalkgroup(1));
        second.completeProperty().set(true);

        manager.startRecording();
        receive(manager, first);
        receive(manager, second);
        manager.processAudioSegments();
        manager.stopRecording();

        List<Path> recordings = recordings();
        assertEquals(2, recordings.size());
        assertTrue(recordings.stream().anyMatch(path -> path.getFileName().toString().contains("CHANNEL-NFM-One")));
        assertTrue(recordings.stream().anyMatch(path -> path.getFileName().toString().contains("CHANNEL-NFM-Two")));
    }

    private static void receive(LiveAudioRecordingManager manager, AudioSegment audioSegment)
    {
        audioSegment.incrementConsumerCount();
        manager.receive(audioSegment);
    }

    private static AudioSegment talkgroupSegment(int talkgroup)
    {
        AudioSegment audioSegment = baseSegment();
        audioSegment.addIdentifier(APCO25Talkgroup.create(talkgroup));
        audioSegment.completeProperty().set(true);
        return audioSegment;
    }

    private static AudioSegment conventionalSegment()
    {
        AudioSegment audioSegment = baseSegment();
        audioSegment.completeProperty().set(true);
        return audioSegment;
    }

    private static AudioSegment baseSegment()
    {
        return baseSegment("NFM Test", 154_920_000L);
    }

    private static AudioSegment baseSegment(String channelName, long frequency)
    {
        AudioSegment audioSegment = new AudioSegment(new AliasList("test"), TimeslotMessage.TIMESLOT_0);
        audioSegment.addIdentifier(SystemConfigurationIdentifier.create("System A"));
        audioSegment.addIdentifier(ChannelNameConfigurationIdentifier.create(channelName));
        audioSegment.addIdentifier(FrequencyConfigurationIdentifier.create(frequency));

        ScalarRealOscillator oscillator = new ScalarRealOscillator(1000, 8000);

        for(int x = 0; x < 5; x++)
        {
            audioSegment.addAudio(oscillator.generate(500));
        }

        return audioSegment;
    }

    private List<Path> recordings() throws IOException
    {
        try(Stream<Path> pathStream = Files.walk(mTempDir))
        {
            return pathStream.filter(path -> path.getFileName().toString().endsWith(".mp3"))
                .sorted(Comparator.comparing(Path::toString))
                .toList();
        }
    }
}
