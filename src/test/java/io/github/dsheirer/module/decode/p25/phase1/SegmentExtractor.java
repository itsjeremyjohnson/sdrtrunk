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
package io.github.dsheirer.module.decode.p25.phase1;

import io.github.dsheirer.sample.complex.ComplexSamples;
import io.github.dsheirer.source.wave.ComplexWaveSource;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;

/**
 * Extracts a time segment from a baseband WAV file for isolated testing.
 *
 * Usage: java SegmentExtractor <input.wav> <output.wav> <startMs> <endMs>
 */
public class SegmentExtractor
{
    public static void main(String[] args)
    {
        if(args.length < 4)
        {
            System.out.println("Usage: SegmentExtractor <input.wav> <output.wav> <startMs> <endMs>");
            return;
        }

        try
        {
            long startMs = Long.parseLong(args[2]);
            long endMs = Long.parseLong(args[3]);
            if(startMs < 0 || endMs <= startMs)
            {
                System.out.println("ERROR: Invalid time range: startMs=" + startMs + ", endMs=" + endMs);
                return;
            }

            File inputFile = new File(args[0]);
            if(!inputFile.exists())
            {
                System.out.println("ERROR: Input file not found: " + args[0]);
                return;
            }

            extractSegment(inputFile, Paths.get(args[1]), startMs, endMs);
            System.out.println("Segment extracted successfully: " + args[1]);
        }
        catch(NumberFormatException e)
        {
            System.out.println("ERROR: Invalid time value: " + e.getMessage());
        }
        catch(IOException e)
        {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Streams the source and retains only samples overlapping the requested interval.
     */
    public static void extractSegment(File source, Path output, long startMs, long endMs) throws IOException
    {
        if(startMs < 0 || endMs <= startMs)
        {
            throw new IllegalArgumentException("Invalid time range: startMs=" + startMs + ", endMs=" + endMs);
        }

        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        double sampleRate;
        long[] currentSample = {0};

        try(ComplexWaveSource waveSource = new ComplexWaveSource(source, false))
        {
            waveSource.start();
            sampleRate = waveSource.getSampleRate();
            long startSample = (long)(startMs * sampleRate / 1000.0);
            long endSample = (long)(endMs * sampleRate / 1000.0);

            waveSource.setListener(buffer -> {
                Iterator<ComplexSamples> iterator = buffer.iterator();
                while(iterator.hasNext())
                {
                    ComplexSamples samples = iterator.next();
                    long bufferStart = currentSample[0];
                    long bufferEnd = bufferStart + samples.i().length;
                    int from = (int)Math.max(0, startSample - bufferStart);
                    int to = (int)Math.min(samples.i().length, endSample - bufferStart);

                    for(int index = from; index < to; index++)
                    {
                        writeSigned16(pcm, samples.i()[index]);
                        writeSigned16(pcm, samples.q()[index]);
                    }

                    currentSample[0] = bufferEnd;
                }
            });

            try
            {
                while(currentSample[0] < endSample)
                {
                    waveSource.next(2048, true);
                }
            }
            catch(Exception e)
            {
                // End of file.
            }
        }

        writeComplexWav(output, pcm.toByteArray(), sampleRate);
    }

    private static void writeSigned16(ByteArrayOutputStream output, float sample)
    {
        float clamped = Math.max(-1.0f, Math.min(1.0f, sample));
        short value = (short)Math.round(clamped * (clamped < 0 ? 32768.0f : 32767.0f));
        output.write(value & 0xFF);
        output.write((value >>> 8) & 0xFF);
    }

    private static void writeComplexWav(Path output, byte[] pcm, double sampleRate) throws IOException
    {
        int channels = 2;
        int bytesPerSample = 2;
        int blockAlign = channels * bytesPerSample;
        int byteRate = (int)sampleRate * blockAlign;

        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        header.putInt(36 + pcm.length);
        header.put("WAVE".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        header.put("fmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        header.putInt(16);
        header.putShort((short)1); // Signed PCM
        header.putShort((short)channels);
        header.putInt((int)sampleRate);
        header.putInt(byteRate);
        header.putShort((short)blockAlign);
        header.putShort((short)16);
        header.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        header.putInt(pcm.length);
        header.flip();

        try(FileChannel channel = FileChannel.open(output, StandardOpenOption.WRITE, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING))
        {
            while(header.hasRemaining())
            {
                channel.write(header);
            }
            ByteBuffer data = ByteBuffer.wrap(pcm);
            while(data.hasRemaining())
            {
                channel.write(data);
            }
        }
    }

    public static void extractSegmentWithPadding(File source, Path output, long centerMs, long paddingMs)
            throws IOException
    {
        extractSegment(source, output, Math.max(0, centerMs - paddingMs), centerMs + paddingMs);
    }
}
