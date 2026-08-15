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
package io.github.dsheirer.audio.playback;

import io.github.dsheirer.alias.Alias;
import io.github.dsheirer.alias.id.priority.Priority;
import io.github.dsheirer.audio.AudioSegment;
import io.github.dsheirer.identifier.Identifier;
import io.github.dsheirer.identifier.IdentifierUpdateNotification;
import io.github.dsheirer.sample.Listener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Routes audio segments to specific Virtual Audio Cable (VAC) outputs based on alias configuration.
 * Monitors segments continuously and routes all audio buffers to the appropriate device.
 */
public class AudioSegmentRouter
{
    private static final Logger mLog = LoggerFactory.getLogger(AudioSegmentRouter.class);

    private Map<String, SourceDataLine> mAudioOutputLines = new ConcurrentHashMap<>();
    private Map<AudioSegment, SegmentRouter> mActiveSegments = new ConcurrentHashMap<>();
    private Map<AudioSegment, Listener<IdentifierUpdateNotification>> mPendingSegments = new ConcurrentHashMap<>();
    private Map<String, Long> mRecentlyActiveLines = new ConcurrentHashMap<>(); // Track lines that recently ended
    private static final long SILENCE_FEED_DURATION = 3000; // Feed silence for 3 seconds after segment ends
    private ScheduledExecutorService mExecutor;
    private volatile boolean mEnabled = true;
    private final BooleanSupplier mDuplicateSuppressionEnabled;

    public AudioSegmentRouter()
    {
        this(() -> false);
    }

    AudioSegmentRouter(BooleanSupplier duplicateSuppressionEnabled)
    {
        mDuplicateSuppressionEnabled = duplicateSuppressionEnabled;
        // Start background thread to route audio continuously
        mExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AudioSegmentRouter");
            t.setDaemon(true);
            return t;
        });

        // Process active segments every 20ms
        mExecutor.scheduleAtFixedRate(this::processActiveSegments, 0, 20, TimeUnit.MILLISECONDS);

        // Keep all audio lines fed with silence to prevent clicking
        mExecutor.scheduleAtFixedRate(this::feedSilenceToIdleLines, 0, 50, TimeUnit.MILLISECONDS);
    }

    /**
     * Identifies the preferred format supported by a mixer for routed audio.
     *
     * @param mixer to inspect
     * @return supported stereo or mono format, or null when neither is available
     */
    public static AudioFormat getSupportedOutputFormat(Mixer mixer)
    {
        AudioFormat stereoFormat = new AudioFormat(8000.0f, 16, 2, true, false);
        DataLine.Info stereoInfo = new DataLine.Info(SourceDataLine.class, stereoFormat);

        if(mixer.isLineSupported(stereoInfo))
        {
            return stereoFormat;
        }

        AudioFormat monoFormat = new AudioFormat(8000.0f, 16, 1, true, false);
        DataLine.Info monoInfo = new DataLine.Info(SourceDataLine.class, monoFormat);
        return mixer.isLineSupported(monoInfo) ? monoFormat : null;
    }

    /**
     * Writes silence to recently active audio lines to prevent clicking
     * Only feeds lines that ended within the last 1 second
     */
    private void feedSilenceToIdleLines()
    {
        if(!mEnabled || mRecentlyActiveLines.isEmpty())
        {
            return;
        }

        long now = System.currentTimeMillis();

        // Remove expired entries and feed silence to recent ones
        mRecentlyActiveLines.entrySet().removeIf(entry -> {
            String deviceName = entry.getKey();
            long endTime = entry.getValue();
            long elapsed = now - endTime;

            // Remove if silence period has elapsed
            if(elapsed > SILENCE_FEED_DURATION)
            {
                return true;
            }

            // Still within silence period - feed silence
            SourceDataLine line = mAudioOutputLines.get(deviceName);
            if(line != null && line.isOpen())
            {
                try
                {
                    float[] silence = new float[400]; // 50ms of silence
                    byte[] silenceBytes = convertFloatsToBytes(silence, line.getFormat().getChannels());
                    line.write(silenceBytes, 0, silenceBytes.length);
                }
                catch(Exception e)
                {
                    // Ignore - line might have been closed
                }
            }

            return false; // Keep in map
        });
    }

    /**
     * Registers an audio segment for routing if it has a custom device configured
     */
    public synchronized void route(AudioSegment audioSegment)
    {
        if(!mEnabled || audioSegment == null)
        {
            return;
        }

        if(audioSegment.isDoNotMonitor() ||
            (audioSegment.isDuplicate() && mDuplicateSuppressionEnabled.getAsBoolean()))
        {
            removePendingSegment(audioSegment);
            return;
        }

        // Get the alias for this audio segment
        Alias alias = getAlias(audioSegment);

        if(alias == null || !alias.hasAudioOutputDevice())
        {
            if(!audioSegment.isComplete())
            {
                mPendingSegments.computeIfAbsent(audioSegment, segment ->
                {
                    Listener<IdentifierUpdateNotification> listener = update -> route(segment);
                    segment.addIdentifierUpdateNotificationListener(listener);
                    return listener;
                });
            }

            return;
        }

        removePendingSegment(audioSegment);

        String deviceName = alias.getAudioOutputDevice();

        //Only suppress normal playback after confirming that the routed output is available.
        if(getOrCreateOutputLine(deviceName) == null)
        {
            mLog.warn("Cannot route - output line not available for: " + deviceName);
            return;
        }

        SegmentRouter router = new SegmentRouter(audioSegment, deviceName, alias.getName());
        audioSegment.incrementConsumerCount();

        if(mActiveSegments.putIfAbsent(audioSegment, router) != null)
        {
            audioSegment.decrementConsumerCount();
            return;
        }

        mLog.info("Starting VAC routing for alias [" + alias.getName() + "] to device [" + deviceName + "]");

        // Remove from recently active list if present - new audio is coming
        mRecentlyActiveLines.remove(deviceName);

        // Suppress main playback
        audioSegment.monitorPriorityProperty().set(Priority.DO_NOT_MONITOR);
    }

    private void removePendingSegment(AudioSegment audioSegment)
    {
        Listener<IdentifierUpdateNotification> listener = mPendingSegments.remove(audioSegment);

        if(listener != null)
        {
            audioSegment.removeIdentifierUpdateNotificationListener(listener);
        }
    }

    private void evictOutputLine(String deviceName, SourceDataLine outputLine)
    {
        mAudioOutputLines.remove(deviceName, outputLine);
        mRecentlyActiveLines.remove(deviceName);
        try
        {
            outputLine.stop();
            outputLine.close();
        }
        catch(Exception e)
        {
            mLog.debug("Error closing failed output line for: " + deviceName, e);
        }
    }

    /**
     * Processes all active segments and routes their audio buffers
     */
    void processActiveSegments()
    {
        for(AudioSegment audioSegment : mPendingSegments.keySet())
        {
            if(audioSegment.isComplete())
            {
                removePendingSegment(audioSegment);
            }
        }

        // Process each active segment
        mActiveSegments.entrySet().removeIf(entry -> {
            AudioSegment segment = entry.getKey();
            SegmentRouter router = entry.getValue();

            if(segment.isDuplicate() && mDuplicateSuppressionEnabled.getAsBoolean())
            {
                segment.decrementConsumerCount();
                return true;
            }

            // Route any new buffers
            router.routeNewBuffers();

            if(router.isAborted())
            {
                segment.decrementConsumerCount();
                return true;
            }

            // Remove if segment is complete and all buffers routed
            if(segment.isComplete() && router.isComplete())
            {
                // Mark this device as recently active so we keep feeding it silence
                mRecentlyActiveLines.put(router.deviceName, System.currentTimeMillis());
                mLog.debug("Completed routing for segment - routed " + router.getTotalBuffersRouted() + " buffers");
                segment.decrementConsumerCount();
                return true; // Remove from active list
            }

            return false; // Keep in active list
        });
    }

    /**
     * Routes audio from a single segment to its designated device
     */
    private class SegmentRouter
    {
        private final AudioSegment segment;
        final String deviceName; // Package-private so outer class can access
        private final String aliasName;
        private int lastRoutedBufferIndex = -1;
        private int totalBuffersRouted = 0;
        private long completionTime = 0;
        private boolean aborted;
        private static final long SILENCE_DURATION_MS = 800; // Write silence for 800ms after completion

        public SegmentRouter(AudioSegment segment, String deviceName, String aliasName)
        {
            this.segment = segment;
            this.deviceName = deviceName;
            this.aliasName = aliasName;
        }

        /**
         * Routes any new buffers that have arrived since last check
         */
        public void routeNewBuffers()
        {
            int currentBufferCount = segment.getAudioBufferCount();

            SourceDataLine outputLine = getOrCreateOutputLine(deviceName);

            if(outputLine == null)
            {
                mLog.warn("Cannot continue route - output line not available for: " + deviceName);
                aborted = true;
                return;
            }

            // If we've routed all buffers, write silence to keep line fed
            if(currentBufferCount <= lastRoutedBufferIndex + 1)
            {
                if(segment.isComplete() && lastRoutedBufferIndex >= segment.getAudioBufferCount() - 1)
                {
                    if(completionTime == 0)
                    {
                        completionTime = System.currentTimeMillis();

                        // Immediately write a large burst of silence to prevent clicking
                        try
                        {
                            float[] largeSilence = new float[1600]; // 200ms of silence in one go
                            byte[] silenceBytes = convertFloatsToBytes(largeSilence, outputLine.getFormat().getChannels());
                            outputLine.write(silenceBytes, 0, silenceBytes.length);
                        }
                        catch(Exception e)
                        {
                            mLog.debug("Error writing initial silence burst", e);
                            evictOutputLine(deviceName, outputLine);
                            aborted = true;
                            return;
                        }

                        mLog.debug("Marked completion time for segment with " + totalBuffersRouted + " buffers");
                    }

                    // Continue writing silence buffers during cooldown period
                    if((System.currentTimeMillis() - completionTime) < SILENCE_DURATION_MS)
                    {
                        try
                        {
                            float[] silence = new float[160]; // One buffer of silence (20ms)
                            byte[] silenceBytes = convertFloatsToBytes(silence, outputLine.getFormat().getChannels());
                            outputLine.write(silenceBytes, 0, silenceBytes.length);
                        }
                        catch(Exception e)
                        {
                            mLog.debug("Error writing cooldown silence", e);
                            evictOutputLine(deviceName, outputLine);
                            aborted = true;
                        }
                    }
                }
                return;
            }

            try
            {
                // Route all new buffers
                for(int i = lastRoutedBufferIndex + 1; i < currentBufferCount; i++)
                {
                    float[] audioBuffer = segment.getAudioBuffer(i);
                    if(audioBuffer != null)
                    {
                        byte[] bytes = convertFloatsToBytes(audioBuffer, outputLine.getFormat().getChannels());
                        outputLine.write(bytes, 0, bytes.length);
                        lastRoutedBufferIndex = i;
                        totalBuffersRouted++;
                    }
                }

                if(totalBuffersRouted % 10 == 0 && totalBuffersRouted > 0)
                {
                    mLog.debug("Routed " + totalBuffersRouted + " buffers for [" + aliasName + "]");
                }
            }
            catch(Exception e)
            {
                mLog.error("Error routing buffers for " + aliasName, e);
                evictOutputLine(deviceName, outputLine);
                aborted = true;
            }
        }

        public boolean isAborted()
        {
            return aborted;
        }

        public boolean isComplete()
        {
            // Not complete until we've routed all buffers AND waited for silence duration
            boolean allBuffersRouted = lastRoutedBufferIndex >= segment.getAudioBufferCount() - 1;

            if(!allBuffersRouted)
            {
                return false;
            }

            // If completion time is set, check if silence duration has elapsed
            if(completionTime > 0)
            {
                return (System.currentTimeMillis() - completionTime) >= SILENCE_DURATION_MS;
            }

            return false;
        }

        public int getTotalBuffersRouted()
        {
            return totalBuffersRouted;
        }
    }

    /**
     * Gets the primary alias for an audio segment - checks ALL identifiers
     */
    Alias getAlias(AudioSegment audioSegment)
    {
        if(audioSegment.getAliasList() == null)
        {
            return null;
        }

        List<Identifier> identifiers = audioSegment.getIdentifierCollection().getIdentifiers();

        if(identifiers != null && !identifiers.isEmpty())
        {
            // Check ALL identifiers to find one with an alias
            for(Identifier identifier : identifiers)
            {
                List<Alias> aliases = audioSegment.getAliasList().getAliases(identifier);

                if(aliases != null)
                {
                    for(Alias alias : aliases)
                    {
                        if(alias.hasAudioOutputDevice())
                        {
                            return alias;
                        }
                    }
                }
            }
        }

        return null;
    }

    static List<Mixer.Info> getMatchingMixerInfos(String deviceName, Mixer.Info[] mixerInfos)
    {
        List<Mixer.Info> exactMatches = new ArrayList<>();
        List<Mixer.Info> fallbackMatches = new ArrayList<>();

        for(Mixer.Info mixerInfo : mixerInfos)
        {
            String mixerName = mixerInfo.getName();
            String unwrappedName = mixerName;

            if(mixerName.startsWith("DirectSound Playback(") && mixerName.endsWith(")"))
            {
                unwrappedName = mixerName.substring(mixerName.indexOf('(') + 1, mixerName.lastIndexOf(')'));
            }

            if(mixerName.equals(deviceName) || unwrappedName.equals(deviceName))
            {
                exactMatches.add(mixerInfo);
            }
            else if(mixerName.contains(deviceName) || deviceName.contains(mixerName) ||
                unwrappedName.contains(deviceName) || deviceName.contains(unwrappedName))
            {
                fallbackMatches.add(mixerInfo);
            }
        }

        exactMatches.addAll(fallbackMatches);
        return exactMatches;
    }

    /**
     * Gets or creates a SourceDataLine for the specified device
     */
    synchronized SourceDataLine getOrCreateOutputLine(String deviceName)
    {
        // Check cache first
        SourceDataLine cached = mAudioOutputLines.get(deviceName);
        if(cached != null && cached.isOpen())
        {
            return cached;
        }

        // Create new line
        try
        {
            for(Mixer.Info mixerInfo : getMatchingMixerInfos(deviceName, AudioSystem.getMixerInfo()))
            {
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
                AudioFormat selectedFormat = getSupportedOutputFormat(mixer);

                if(selectedFormat != null)
                {
                    DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, selectedFormat);
                    SourceDataLine line = (SourceDataLine) mixer.getLine(lineInfo);
                    line.open(selectedFormat, 8192);
                    line.start();

                    mAudioOutputLines.put(deviceName, line);
                    mLog.info("Opened audio line for: " + deviceName);

                    return line;
                }
            }

            mLog.warn("Could not find device: " + deviceName);
        }
        catch(Exception e)
        {
            mLog.error("Error opening device: " + deviceName, e);
        }

        return null;
    }

    /**
     * Converts float samples to bytes
     */
    private byte[] convertFloatsToBytes(float[] samples, int channels)
    {
        ByteBuffer buffer;

        if(channels == 2)
        {
            buffer = ByteBuffer.allocate(samples.length * 4);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            for(float sample : samples)
            {
                short pcmValue = (short) (sample * 32767.0f);
                buffer.putShort(pcmValue);
                buffer.putShort(pcmValue);
            }
        }
        else
        {
            buffer = ByteBuffer.allocate(samples.length * 2);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            for(float sample : samples)
            {
                short pcmValue = (short) (sample * 32767.0f);
                buffer.putShort(pcmValue);
            }
        }

        return buffer.array();
    }

    public void setEnabled(boolean enabled)
    {
        mEnabled = enabled;
    }

    public synchronized void dispose()
    {
        mEnabled = false;

        if(mExecutor != null)
        {
            mExecutor.shutdown();
            try
            {
                if(!mExecutor.awaitTermination(5, TimeUnit.SECONDS))
                {
                    mLog.warn("AudioSegmentRouter executor did not terminate in time, forcing shutdown");
                    mExecutor.shutdownNow();
                }
            }
            catch(InterruptedException e)
            {
                mExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        for(AudioSegment audioSegment : mActiveSegments.keySet())
        {
            audioSegment.decrementConsumerCount();
        }

        mActiveSegments.clear();

        for(AudioSegment audioSegment : mPendingSegments.keySet())
        {
            removePendingSegment(audioSegment);
        }

        mRecentlyActiveLines.clear();

        for(SourceDataLine line : mAudioOutputLines.values())
        {
            try
            {
                if(line != null && line.isOpen())
                {
                    line.drain();
                    line.stop();
                    line.close();
                }
            }
            catch(Exception e)
            {
                mLog.error("Error closing line", e);
            }
        }

        mAudioOutputLines.clear();
    }

    public static String[] getAvailableDevices()
    {
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        return java.util.Arrays.stream(mixers)
            .map(Mixer.Info::getName)
            .toArray(String[]::new);
    }
}
