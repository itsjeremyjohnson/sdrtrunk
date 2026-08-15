/*
 * *****************************************************************************
 * Copyright (C) 2014-2024 Dennis Sheirer
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

package io.github.dsheirer.audio.broadcast;

import io.github.dsheirer.alias.Alias;
import io.github.dsheirer.alias.AliasList;
import io.github.dsheirer.alias.id.broadcast.BroadcastChannel;
import io.github.dsheirer.audio.AudioSegment;
import io.github.dsheirer.identifier.Identifier;
import io.github.dsheirer.identifier.Identifier;
import io.github.dsheirer.identifier.IdentifierCollection;
import io.github.dsheirer.identifier.MutableIdentifierCollection;
import io.github.dsheirer.identifier.Role;
import io.github.dsheirer.identifier.patch.PatchGroup;
import io.github.dsheirer.identifier.patch.PatchGroupIdentifier;
import io.github.dsheirer.preference.UserPreferences;
import io.github.dsheirer.record.AudioSegmentRecorder;
import io.github.dsheirer.record.RecordFormat;
import io.github.dsheirer.sample.Listener;
import io.github.dsheirer.util.ThreadPool;
import io.github.dsheirer.util.TimeStamp;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Audio streaming manager monitors audio segments through completion and creates temporary streaming recordings on
 * disk and enqueues the temporary recording for streaming.
 */
public class AudioStreamingManager implements Listener<AudioSegment>
{
    private final static Logger mLog = LoggerFactory.getLogger(AudioStreamingManager.class);
    private LinkedTransferQueue<AudioSegment> mNewAudioSegments = new LinkedTransferQueue<>();
    private List<AudioSegment> mAudioSegments = new ArrayList<>();
    private Listener<AudioRecording> mAudioRecordingListener;
    private BroadcastFormat mBroadcastFormat;
    private UserPreferences mUserPreferences;
    private ScheduledFuture<?> mAudioSegmentProcessorFuture;
    private int mNextRecordingNumber = 1;
    private BroadcastModel mBroadcastModel;

    // Real-time streaming state: each broadcaster tracks its own next buffer so late-ready destinations can catch up.
    private java.util.Map<AudioSegment, java.util.Map<IRealTimeAudioBroadcaster, Integer>> mRealTimeStreams =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Constructs an instance
     * @param listener to receive completed audio recordings
     * @param broadcastFormat for temporary recordings
     * @param userPreferences to manage recording directories
     */
    public AudioStreamingManager(Listener<AudioRecording> listener, BroadcastFormat broadcastFormat, UserPreferences userPreferences)
    {
        mAudioRecordingListener = listener;
        mBroadcastFormat = broadcastFormat;
        mUserPreferences = userPreferences;

        // If the listener is a BroadcastModel, store it for real-time broadcaster lookups
        if(listener instanceof BroadcastModel bm)
        {
            mBroadcastModel = bm;
        }
    }

    /**
     * Sets the broadcast model to enable real-time audio routing to Zello and other
     * real-time broadcasters.
     * @param broadcastModel the broadcast model
     */
    public void setBroadcastModel(BroadcastModel broadcastModel)
    {
        mBroadcastModel = broadcastModel;
    }

    /**
     * Primary receive method
     */
    @Override
    public void receive(AudioSegment audioSegment)
    {
        mNewAudioSegments.add(audioSegment);
    }

    /**
     * Starts the scheduled audio segment processor
     */
    public void start()
    {
        if(mAudioSegmentProcessorFuture == null)
        {
            mAudioSegmentProcessorFuture = ThreadPool.SCHEDULED.scheduleAtFixedRate(new AudioSegmentProcessor(),
                0, 250, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Stops the scheduled audio segment processor
     */
    public void stop()
    {
        if(mAudioSegmentProcessorFuture != null)
        {
            mAudioSegmentProcessorFuture.cancel(true);
            mAudioSegmentProcessorFuture = null;
        }

        for(AudioSegment audioSegment: mNewAudioSegments)
        {
            audioSegment.decrementConsumerCount();
        }

        mNewAudioSegments.clear();

        for(AudioSegment audioSegment: mAudioSegments)
        {
            stopRealTimeStreams(audioSegment);
            audioSegment.decrementConsumerCount();
        }

        mAudioSegments.clear();
        mRealTimeStreams.clear();
    }

    /**
     * Main processing method to process audio segments
     */
    private void processAudioSegments()
    {
        mNewAudioSegments.drainTo(mAudioSegments);

        Iterator<AudioSegment> it = mAudioSegments.iterator();
        AudioSegment audioSegment;
        while(it.hasNext())
        {
            audioSegment = it.next();

            if(audioSegment.isDuplicate() && mUserPreferences.getCallManagementPreference().isDuplicateStreamingSuppressionEnabled())
            {
                it.remove();
                stopRealTimeStreams(audioSegment);
                audioSegment.decrementConsumerCount();
            }
            else
            {
                // Forward new audio buffers to any active real-time broadcasters
                forwardRealTimeAudio(audioSegment);

                if(audioSegment.completeProperty().get())
                {
                    it.remove();

                if(mAudioRecordingListener != null && audioSegment.hasBroadcastChannels())
                {
                    IdentifierCollection identifiers =
                            new IdentifierCollection(audioSegment.getIdentifierCollection().getIdentifiers());

                    if(identifiers.getToIdentifier() instanceof PatchGroupIdentifier patchGroupIdentifier)
                    {
                        if(mUserPreferences.getCallManagementPreference()
                                .getPatchGroupStreamingOption() == PatchGroupStreamingOption.TALKGROUPS)
                        {
                            //Decompose the patch group into the individual (patched) talkgroups and process the audio
                            //segment for each patched talkgroup.
                            PatchGroup patchGroup = patchGroupIdentifier.getValue();

                            List<Identifier> ids = new ArrayList<>();
                            ids.addAll(patchGroup.getPatchedTalkgroupIdentifiers());
                            ids.addAll(patchGroup.getPatchedRadioIdentifiers());

                            //If there are no patched radios/talkgroups, override user preference and stream as a patch group
                            if(ids.isEmpty() || audioSegment.getAliasList() == null)
                            {
                                processAudioSegment(audioSegment, identifiers, audioSegment.getBroadcastChannels());
                            }
                            else
                            {
                                AliasList aliasList = audioSegment.getAliasList();

                                for(Identifier identifier: ids)
                                {
                                    List<Alias> aliases = aliasList.getAliases(identifier);
                                    Set<BroadcastChannel> broadcastChannels = new HashSet<>();
                                    for(Alias alias: aliases)
                                    {
                                        broadcastChannels.addAll(alias.getBroadcastChannels());
                                    }

                                    if(!broadcastChannels.isEmpty())
                                    {
                                        MutableIdentifierCollection decomposedIdentifiers =
                                                new MutableIdentifierCollection(identifiers.getIdentifiers());
                                        //Remove patch group TO identifier & replace with the patched talkgroup/radio
                                        decomposedIdentifiers.remove(Role.TO);
                                        decomposedIdentifiers.update(identifier);
                                        processAudioSegment(audioSegment, decomposedIdentifiers, broadcastChannels);
                                    }
                                }
                            }
                        }
                        else
                        {
                            processAudioSegment(audioSegment, identifiers, audioSegment.getBroadcastChannels());
                        }
                    }
                    else
                    {
                        processAudioSegment(audioSegment, identifiers, audioSegment.getBroadcastChannels());
                    }
                }

                    audioSegment.decrementConsumerCount();
                    stopRealTimeStreams(audioSegment);
                }
            }
        }
    }

    /**
     * Starts or continues real-time audio forwarding for an audio segment.
     * Finds real-time broadcasters (like Zello) that match the segment's broadcast channels
     * and forwards new audio buffers to them incrementally.
     */
    private void forwardRealTimeAudio(AudioSegment audioSegment)
    {
        if(mBroadcastModel == null || !audioSegment.hasBroadcastChannels())
        {
            return;
        }

        Set<IRealTimeAudioBroadcaster> configuredBroadcasters = new HashSet<>();
        for(BroadcastChannel broadcastChannel : audioSegment.getBroadcastChannels())
        {
            AbstractAudioBroadcaster<?> broadcaster = mBroadcastModel.getBroadcaster(broadcastChannel.getChannelName());
            if(broadcaster instanceof IRealTimeAudioBroadcaster rtb)
            {
                configuredBroadcasters.add(rtb);
            }
        }

        configuredBroadcasters.forEach(rtb -> forwardRealTimeAudio(audioSegment, rtb));
    }

    /**
     * Starts a destination when it becomes ready and forwards every buffer that destination has not received yet.
     */
    void forwardRealTimeAudio(AudioSegment audioSegment, IRealTimeAudioBroadcaster broadcaster)
    {
        java.util.Map<IRealTimeAudioBroadcaster, Integer> rtBroadcasters =
                mRealTimeStreams.computeIfAbsent(audioSegment, ignored -> new java.util.concurrent.ConcurrentHashMap<>());

        if(!rtBroadcasters.containsKey(broadcaster) || !broadcaster.isRealTimeStreamActive())
        {
            if(!broadcaster.isRealTimeReady())
            {
                return;
            }

            IdentifierCollection identifiers = audioSegment.getIdentifierCollection() != null
                    ? new IdentifierCollection(audioSegment.getIdentifierCollection().getIdentifiers())
                    : null;
            broadcaster.startRealTimeStream(identifiers);

            if(!broadcaster.isRealTimeStreamActive())
            {
                return;
            }

            rtBroadcasters.putIfAbsent(broadcaster, 0);
        }

        int currentSize = audioSegment.getAudioBuffers().size();
        int lastIndex = rtBroadcasters.get(broadcaster);
        for(int i = lastIndex; i < currentSize; i++)
        {
            float[] buffer = audioSegment.getAudioBuffer(i);
            if(buffer != null)
            {
                broadcaster.receiveRealTimeAudio(buffer);
            }
        }

        rtBroadcasters.put(broadcaster, currentSize);
    }

    /**
     * Stops any active real-time streams for the given audio segment.
     */
    private void stopRealTimeStreams(AudioSegment audioSegment)
    {
        java.util.Map<IRealTimeAudioBroadcaster, Integer> rtBroadcasters = mRealTimeStreams.remove(audioSegment);

        if(rtBroadcasters != null)
        {
            for(IRealTimeAudioBroadcaster rtb : rtBroadcasters.keySet())
            {
                try
                {
                    rtb.stopRealTimeStream();
                }
                catch(Exception e)
                {
                    mLog.error("Error stopping real-time stream", e);
                }
            }
        }
    }

    /**
     * Processes an audio segment for streaming by creating a temporary MP3 recording and submitting the recording
     * to the specific broadcast channel(s).
     * @param audioSegment to process for streaming
     * @param identifierCollection to use for the streamed audio recording.
     * @param broadcastChannels to receive the audio recording
     */
    static Set<BroadcastChannel> getCompletedRecordingChannels(Set<BroadcastChannel> broadcastChannels,
                                                                Function<String,AbstractAudioBroadcaster<?>> resolver)
    {
        Set<BroadcastChannel> completedRecordingChannels = new HashSet<>();

        for(BroadcastChannel broadcastChannel : broadcastChannels)
        {
            AbstractAudioBroadcaster<?> broadcaster = resolver != null ?
                resolver.apply(broadcastChannel.getChannelName()) : null;

            if(!(broadcaster instanceof IRealTimeAudioBroadcaster))
            {
                completedRecordingChannels.add(broadcastChannel);
            }
        }

        return completedRecordingChannels;
    }

    private void processAudioSegment(AudioSegment audioSegment, IdentifierCollection identifierCollection,
                                     Set<BroadcastChannel> broadcastChannels)
    {
        Set<BroadcastChannel> completedRecordingChannels = getCompletedRecordingChannels(broadcastChannels,
            mBroadcastModel != null ? mBroadcastModel::getBroadcaster : null);

        if(completedRecordingChannels.isEmpty())
        {
            return;
        }

        Path path = getTemporaryRecordingPath();
        long length = 0;

        for(float[] audioBuffer: audioSegment.getAudioBuffers())
        {
            length += audioBuffer.length;
        }

        length /= 8; //Sample rate is 8000 samples per second, or 8 samples per millisecond.

        try
        {
            AudioSegmentRecorder.record(audioSegment, path, RecordFormat.MP3, mUserPreferences, identifierCollection);

            AudioRecording audioRecording = new AudioRecording(path, completedRecordingChannels, identifierCollection,
                    audioSegment.getStartTimestamp(), length);
            mAudioRecordingListener.receive(audioRecording);
        }
        catch(IOException ioe)
        {
            mLog.error("Error recording temporary stream MP3");
        }
    }

    /**
     * Creates a temporary streaming recording file path
     */
    private Path getTemporaryRecordingPath()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(BroadcastModel.TEMPORARY_STREAM_FILE_SUFFIX);

        //Check for integer overflow and readjust negative value to 0
        if(mNextRecordingNumber < 0)
        {
            mNextRecordingNumber = 1;
        }

        int recordingNumber = mNextRecordingNumber++;

        sb.append(recordingNumber).append("_");
        sb.append(TimeStamp.getLongTimeStamp("_"));
        sb.append(mBroadcastFormat.getFileExtension());

        Path temporaryRecordingPath = mUserPreferences.getDirectoryPreference().getDirectoryStreaming().resolve(sb.toString());

        return temporaryRecordingPath;
    }

    /**
     * Scheduled runnable to process audio segments.
     */
    public class AudioSegmentProcessor implements Runnable
    {
        @Override
        public void run()
        {
            try
            {
                processAudioSegments();
            }
            catch(Throwable t)
            {
                mLog.error("Error processing audio segments for streaming", t);
            }
        }
    }
}
