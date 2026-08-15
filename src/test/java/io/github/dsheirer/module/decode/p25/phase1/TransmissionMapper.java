/*
 * *****************************************************************************
 * Copyright (C) 2014-2025 Dennis Sheirer
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
package io.github.dsheirer.module.decode.p25.phase1;

import io.github.dsheirer.sample.complex.ComplexSamples;
import io.github.dsheirer.source.wave.ComplexWaveSource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Maps transmission boundaries in a baseband recording using signal energy analysis.
 * Detects when RF signal is present (transmission in progress) vs. silence (no transmission).
 */
public class TransmissionMapper
{
    // Energy detection parameters (matching P25P1DecoderLSMv2)
    private static final float ENERGY_EMA_FACTOR = 0.002f;
    private static final float ENERGY_SILENCE_RATIO = 0.15f;
    private static final float PEAK_DECAY = 0.99999f;
    private static final long NOISE_FLOOR_LEARNING_MS = 100;
    private static final float SIGNAL_RISE_RATIO = 4.0f;
    private static final long SUSTAINED_SIGNAL_RISE_MS = 10;

    // Transmission detection thresholds
    private static final long MIN_TRANSMISSION_MS = 180;   // Minimum 1 LDU duration
    private static final long MIN_GAP_MS = 500;            // Gap required to split transmissions
    private static final long BUFFER_MS = 100;             // Buffer for slice extraction
    private static final long PREAMBLE_MS = 100;           // First 100ms is the preamble window

    // Energy tracking state
    private float mEnergyAverage = 0f;
    private float mPeakEnergy = 0f;
    private long mSampleCount = 0;
    private double mSampleRate = 0;
    private double mNoiseFloorSum;
    private long mNoiseFloorSampleCount;
    private float mNoiseFloor;
    private long mSignalRiseStartSample = -1;

    // Signal period tracking
    private Long mSignalStartSample = null;
    private float mPeriodPeakEnergy = 0f;
    private double mPeriodSumEnergy = 0;
    private int mPeriodSampleCount = 0;
    // Preamble and variance tracking
    private double mPreambleSumEnergy = 0;
    private int mPreambleSampleCount = 0;
    private final RunningVariance mPeriodVariance = new RunningVariance();

    // Detected transmissions
    private final List<SignalPeriod> mSignalPeriods = new ArrayList<>();

    /**
     * Maps all transmissions in a baseband WAV file.
     *
     * @param basebandWav path to the baseband WAV file
     * @return list of detected transmissions
     */
    public List<Transmission> mapTransmissions(Path basebandWav) throws IOException
    {
        return mapTransmissions(basebandWav.toFile());
    }

    /**
     * Maps all transmissions in a baseband WAV file.
     *
     * @param basebandFile the baseband WAV file
     * @return list of detected transmissions
     */
    public List<Transmission> mapTransmissions(File basebandFile) throws IOException
    {
        resetState();

        try(ComplexWaveSource source = new ComplexWaveSource(basebandFile, false))
        {
            source.setListener(buffer -> {
                Iterator<ComplexSamples> it = buffer.iterator();
                while(it.hasNext())
                {
                    processSamples(it.next());
                }
            });
            source.start();
            mSampleRate = source.getSampleRate();

            // Process entire file
            try
            {
                while(true)
                {
                    source.next(2048, true);
                }
            }
            catch(Exception e)
            {
                // End of file
            }
        }

        // Close any open signal period
        if(mSignalStartSample != null)
        {
            closeSignalPeriod(mSampleCount, false); // incomplete at end of file
        }

        // Convert signal periods to transmissions
        return convertToTransmissions();
    }

    /**
     * Process a batch of I/Q samples for energy detection.
     */
    private void processSamples(ComplexSamples samples)
    {
        float[] i = samples.i();
        float[] q = samples.q();

        for(int idx = 0; idx < i.length; idx++)
        {
            float energy = (i[idx] * i[idx]) + (q[idx] * q[idx]);
            mEnergyAverage += (energy - mEnergyAverage) * ENERGY_EMA_FACTOR;

            if(mEnergyAverage > mPeakEnergy)
            {
                mPeakEnergy = mEnergyAverage;
            }
            else
            {
                mPeakEnergy *= PEAK_DECAY;
            }

            if(mNoiseFloorSampleCount < noiseFloorLearningSamples())
            {
                mNoiseFloorSum += mEnergyAverage;
                mNoiseFloorSampleCount++;
                mNoiseFloor = (float)(mNoiseFloorSum / mNoiseFloorSampleCount);
                mSampleCount++;
                continue;
            }

            float silenceThreshold = mPeakEnergy * ENERGY_SILENCE_RATIO;
            boolean aboveNoiseFloor = mEnergyAverage >= Math.max(mNoiseFloor * SIGNAL_RISE_RATIO, Float.MIN_NORMAL);
            boolean isSignal = mSignalStartSample != null ?
                    mEnergyAverage >= silenceThreshold : hasSustainedSignalRise(aboveNoiseFloor);

            if(isSignal)
            {
                if(mSignalStartSample == null)
                {
                    startSignalPeriod(mSignalRiseStartSample);
                }
                addSignalEnergy(mEnergyAverage);
            }
            else if(mSignalStartSample != null)
            {
                closeSignalPeriod(mSampleCount, true);
            }
            else if(mSignalRiseStartSample < 0)
            {
                // Continue adapting to idle receiver noise without allowing it to open a transmission.
                mNoiseFloor += (mEnergyAverage - mNoiseFloor) * ENERGY_EMA_FACTOR;
            }

            mSampleCount++;
        }
    }

    private void resetState()
    {
        mEnergyAverage = 0f;
        mPeakEnergy = 0f;
        mSampleCount = 0;
        mNoiseFloorSum = 0;
        mNoiseFloorSampleCount = 0;
        mNoiseFloor = 0;
        mSignalRiseStartSample = -1;
        mSignalStartSample = null;
        mSignalPeriods.clear();
    }

    private long noiseFloorLearningSamples()
    {
        return Math.max(1, (long)(mSampleRate * NOISE_FLOOR_LEARNING_MS / 1000.0));
    }

    private boolean hasSustainedSignalRise(boolean aboveNoiseFloor)
    {
        if(!aboveNoiseFloor)
        {
            mSignalRiseStartSample = -1;
            return false;
        }

        if(mSignalRiseStartSample < 0)
        {
            mSignalRiseStartSample = mSampleCount;
        }

        long requiredSamples = Math.max(1, (long)(mSampleRate * SUSTAINED_SIGNAL_RISE_MS / 1000.0));
        return mSampleCount - mSignalRiseStartSample + 1 >= requiredSamples;
    }

    private void startSignalPeriod(long startSample)
    {
        mSignalStartSample = startSample;
        mSignalRiseStartSample = -1;
        mPeriodPeakEnergy = 0f;
        mPeriodSumEnergy = 0;
        mPeriodSampleCount = 0;
        mPreambleSumEnergy = 0;
        mPreambleSampleCount = 0;
        mPeriodVariance.reset();
    }

    private void addSignalEnergy(float energy)
    {
        mPeriodPeakEnergy = Math.max(mPeriodPeakEnergy, energy);
        mPeriodSumEnergy += energy;
        mPeriodSampleCount++;
        mPeriodVariance.add(energy);

        long elapsedMs = samplesToMs(mSampleCount - mSignalStartSample);
        if(elapsedMs < PREAMBLE_MS)
        {
            mPreambleSumEnergy += energy;
            mPreambleSampleCount++;
        }
    }

    List<Transmission> mapSamplesForTest(ComplexSamples samples, double sampleRate)
    {
        resetState();
        mSampleRate = sampleRate;
        processSamples(samples);
        if(mSignalStartSample != null)
        {
            closeSignalPeriod(mSampleCount, false);
        }
        return convertToTransmissions();
    }

    /**
     * Close the current signal period and add to list.
     */
    private void closeSignalPeriod(long endSample, boolean isComplete)
    {
        if(mSignalStartSample == null) return;

        long startMs = samplesToMs(mSignalStartSample);
        long endMs = samplesToMs(endSample);
        float avgEnergy = mPeriodSampleCount > 0 ? (float)(mPeriodSumEnergy / mPeriodSampleCount) : 0;
        float preambleEnergy = mPreambleSampleCount > 0 ? (float)(mPreambleSumEnergy / mPreambleSampleCount) : avgEnergy;
        float variance = (float)mPeriodVariance.populationStandardDeviation();

        mSignalPeriods.add(new SignalPeriod(startMs, endMs, mPeriodPeakEnergy, avgEnergy, preambleEnergy, variance, isComplete));
        mSignalStartSample = null;
        mSignalRiseStartSample = -1;
    }

    /**
     * Convert sample count to milliseconds.
     */
    private long samplesToMs(long samples)
    {
        if(mSampleRate <= 0)
        {
            return 0;
        }
        return (long)((samples / mSampleRate) * 1000.0);
    }

    /**
     * Convert signal periods to transmission records, merging periods with small gaps.
     */
    private List<Transmission> convertToTransmissions()
    {
        List<Transmission> transmissions = new ArrayList<>();

        if(mSignalPeriods.isEmpty())
        {
            return transmissions;
        }

        // Merge signal periods that are close together
        List<SignalPeriod> merged = new ArrayList<>();
        SignalPeriod current = mSignalPeriods.get(0);

        for(int i = 1; i < mSignalPeriods.size(); i++)
        {
            SignalPeriod next = mSignalPeriods.get(i);
            long gap = next.startMs - current.endMs;

            if(gap < MIN_GAP_MS)
            {
                // Merge: extend current period
                // Keep preamble from first period (it's the actual transmission start)
                current = new SignalPeriod(
                    current.startMs,
                    next.endMs,
                    Math.max(current.peakEnergy, next.peakEnergy),
                    (current.avgEnergy + next.avgEnergy) / 2, // simple average
                    current.preambleEnergy, // keep original preamble
                    (current.energyVariance + next.energyVariance) / 2, // average variance
                    next.isComplete
                );
            }
            else
            {
                // Gap too large: save current and start new
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);

        // Filter out short periods and convert to transmissions
        int index = 1;
        for(SignalPeriod period : merged)
        {
            long duration = period.endMs - period.startMs;
            if(duration >= MIN_TRANSMISSION_MS)
            {
                transmissions.add(new Transmission(
                    index++,
                    period.startMs,
                    period.endMs,
                    period.peakEnergy,
                    period.avgEnergy,
                    period.preambleEnergy,
                    period.energyVariance,
                    period.isComplete
                ));
            }
        }

        return transmissions;
    }

    /**
     * Get the sample rate of the last processed file.
     */
    public double getSampleRate()
    {
        return mSampleRate;
    }

    /**
     * Get the total duration of the last processed file in milliseconds.
     */
    public long getTotalDurationMs()
    {
        return samplesToMs(mSampleCount);
    }

    static class RunningVariance
    {
        private long mCount;
        private double mMean;
        private double mM2;

        void add(double value)
        {
            mCount++;
            double delta = value - mMean;
            mMean += delta / mCount;
            double deltaFromNewMean = value - mMean;
            mM2 += delta * deltaFromNewMean;
        }

        double populationStandardDeviation()
        {
            return mCount > 1 ? Math.sqrt(mM2 / mCount) : 0;
        }

        void reset()
        {
            mCount = 0;
            mMean = 0;
            mM2 = 0;
        }
    }

    /**
     * Internal record for tracking signal periods during detection.
     */
    private record SignalPeriod(
        long startMs,
        long endMs,
        float peakEnergy,
        float avgEnergy,
        float preambleEnergy,
        float energyVariance,
        boolean isComplete
    ) {}
}
