/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * *****************************************************************************
 */
package io.github.dsheirer.record.wave;

import io.github.dsheirer.sample.complex.ComplexSamples;
import io.github.dsheirer.source.SourceEvent;
import io.github.dsheirer.util.Dispatcher;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.sound.sampled.AudioFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityTriggeredWaveRecorderTest
{
    @TempDir
    Path mTempDirectory;

    @Test
    void preTriggerBufferRetainsAtMostConfiguredSampleDuration()
    {
        CircularSampleBuffer buffer = new CircularSampleBuffer(50000);
        for(int x = 0; x < 30; x++)
        {
            buffer.add(createInactiveSamples(2048));
        }

        assertEquals(49152, buffer.getSampleCount());
    }

    @Test
    void writesTriggerBufferOnce() throws Exception
    {
        int sampleCount = 256;
        ActivityTriggeredWaveRecorder recorder = createRecorder();
        recorder.start();
        recorder.receive(createActiveSamples(sampleCount));
        awaitRecordingCount(1);
        recorder.stop();

        byte[] wave = Files.readAllBytes(findRecordings().getFirst());
        int dataSize = ByteBuffer.wrap(wave, 40, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        assertEquals(sampleCount * 4, dataSize);
    }

    @Test
    void firstAboveThresholdBufferTriggersImmediately() throws Exception
    {
        ActivityTriggeredWaveRecorder recorder =
                new ActivityTriggeredWaveRecorder(25000.0f, "Test Channel", 155250000L, -70.0f, mTempDirectory);
        recorder.start();
        recorder.receive(createSamples(2048, 0.001f));
        awaitRecordingCount(1);
        recorder.stop();

        assertEquals(1, findRecordings().size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void stopWaitsForSlowInFlightRecorderBatch() throws Exception
    {
        ActivityTriggeredWaveRecorder recorder = createRecorder();
        recorder.start();
        Field field = ActivityTriggeredWaveRecorder.class.getDeclaredField("mBufferProcessor");
        field.setAccessible(true);
        Dispatcher<Object> dispatcher = (Dispatcher<Object>)field.get(recorder);
        CountDownLatch processingStarted = new CountDownLatch(1);
        CountDownLatch releaseProcessing = new CountDownLatch(1);
        dispatcher.setListener(ignored -> {
            processingStarted.countDown();
            try
            {
                releaseProcessing.await(5, TimeUnit.SECONDS);
            }
            catch(InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        });
        recorder.receive(createActiveSamples(64));
        assertTrue(processingStarted.await(2, TimeUnit.SECONDS));

        Thread stopThread = Thread.ofPlatform().start(recorder::stop);
        stopThread.join(2200);
        assertTrue(stopThread.isAlive());

        releaseProcessing.countDown();
        stopThread.join(2000);
        assertEquals(false, stopThread.isAlive());
    }

    @Test
    void createsUniqueFilesForRecordingsStartedInTheSameSecond() throws Exception
    {
        ActivityTriggeredWaveRecorder recorder = createRecorder();

        recorder.start();
        recorder.receive(createActiveSamples(64));
        awaitRecordingCount(1);
        recorder.stop();
        recorder.start();
        recorder.receive(createActiveSamples(64));
        awaitRecordingCount(2);
        recorder.stop();

        assertEquals(2, findRecordings().size());
    }

    @Test
    void usesUpdatedFrequencyAndClearsPreTriggerDataAfterRetune() throws Exception
    {
        ActivityTriggeredWaveRecorder recorder = createRecorder();
        recorder.start();
        recorder.receive(createInactiveSamples(64));
        recorder.getSourceEventListener().receive(SourceEvent.frequencyChange(null, 155300000L));
        recorder.receive(createActiveSamples(64));
        awaitRecordingCount(1);
        recorder.stop();

        Path recording = findRecordings().getFirst();
        assertEquals(true, recording.getFileName().toString().contains("155300000"));
        byte[] wave = Files.readAllBytes(recording);
        int dataSize = ByteBuffer.wrap(wave, 40, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        assertEquals(64 * 4, dataSize);
    }

    @Test
    void waveWriterRollsOverWavFileNames() throws Exception
    {
        Path first = mTempDirectory.resolve("rollover.wav");
        AudioFormat format = new AudioFormat(8000.0f, 16, 1, true, false);
        try(WaveWriter writer = new WaveWriter(format, first, 128))
        {
            writer.writeData(ByteBuffer.allocate(160));
        }

        assertEquals(true, Files.exists(first));
        assertEquals(true, Files.exists(mTempDirectory.resolve("rollover_2.wav")));
    }

    private ActivityTriggeredWaveRecorder createRecorder()
    {
        return new ActivityTriggeredWaveRecorder(25000.0f, "Test Channel", 155250000L, -99.0f, mTempDirectory);
    }

    private ComplexSamples createActiveSamples(int length)
    {
        float[] i = new float[length];
        float[] q = new float[length];
        java.util.Arrays.fill(i, 1.0f);
        return new ComplexSamples(i, q, System.currentTimeMillis());
    }

    private ComplexSamples createInactiveSamples(int length)
    {
        return new ComplexSamples(new float[length], new float[length], System.currentTimeMillis());
    }

    private ComplexSamples createSamples(int length, float amplitude)
    {
        float[] i = new float[length];
        java.util.Arrays.fill(i, amplitude);
        return new ComplexSamples(i, new float[length], System.currentTimeMillis());
    }

    private void awaitRecordingCount(int count) throws Exception
    {
        long deadline = System.currentTimeMillis() + 2000;
        while(findRecordings().size() < count && System.currentTimeMillis() < deadline)
        {
            Thread.sleep(10);
        }
        assertEquals(count, findRecordings().size());
    }

    private List<Path> findRecordings() throws IOException
    {
        try(var paths = Files.walk(mTempDirectory))
        {
            return paths.filter(path -> path.toString().endsWith(".wav")).toList();
        }
    }
}
