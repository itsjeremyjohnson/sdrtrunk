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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ActivityTriggeredWaveRecorderTest
{
    @TempDir
    Path mTempDirectory;

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
