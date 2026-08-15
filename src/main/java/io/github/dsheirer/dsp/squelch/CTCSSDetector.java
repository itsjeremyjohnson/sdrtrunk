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
package io.github.dsheirer.dsp.squelch;

/**
 * CTCSS (PL) tone detector using the Goertzel algorithm. Detects the presence of a specific sub-audible tone
 * frequency in demodulated FM audio. Uses hysteresis to avoid rapid toggling of the detection state.
 *
 * The detector accumulates samples into blocks and evaluates each block for the presence of the target tone.
 * Block size is set to 250ms which provides sufficient frequency resolution (~4 Hz) to distinguish adjacent
 * CTCSS tones while the center bin's main lobe naturally covers the ±2% frequency tolerance.
 * A configurable number of consecutive positive/negative detections are required before the state changes.
 */
public class CTCSSDetector
{
    private static final int DEFAULT_HYSTERESIS_OPEN = 2;
    private static final int DEFAULT_HYSTERESIS_CLOSE = 2;
    private static final double DETECTION_THRESHOLD_DB = -10.0;

    private final double mTargetFrequency;
    private double mSampleRate;
    private int mBlockSize;
    private double mCoefficient;
    private double mLowerCoefficient;
    private double mUpperCoefficient;
    private double mLowerToleranceCoefficient;
    private double mUpperToleranceCoefficient;
    private double mLowerAdjacentLeakageRatio;
    private double mUpperAdjacentLeakageRatio;
    private double mS;
    private double mSPrev;
    private double mSPrev2;
    private double mLowerS;
    private double mLowerSPrev;
    private double mLowerSPrev2;
    private double mUpperS;
    private double mUpperSPrev;
    private double mUpperSPrev2;
    private double mLowerToleranceS;
    private double mLowerToleranceSPrev;
    private double mLowerToleranceSPrev2;
    private double mUpperToleranceS;
    private double mUpperToleranceSPrev;
    private double mUpperToleranceSPrev2;
    private int mSampleCount;
    private boolean mToneDetected;
    private int mHysteresisCount;
    private int mHysteresisOpenThreshold = DEFAULT_HYSTERESIS_OPEN;
    private int mHysteresisCloseThreshold = DEFAULT_HYSTERESIS_CLOSE;
    private Runnable mToneDetectedListener;
    private Runnable mToneLostListener;

    /**
     * Constructs an instance
     * @param targetFrequency the CTCSS tone frequency to detect in Hz
     */
    public CTCSSDetector(double targetFrequency)
    {
        mTargetFrequency = targetFrequency;
    }

    /**
     * Sets the sample rate and recalculates internal parameters.
     * Block size is set to 250ms which places the Goertzel first null at the adjacent CTCSS tone spacing (~4 Hz),
     * providing clean rejection of adjacent tones while the main lobe covers ±2% frequency tolerance.
     * @param sampleRate of the incoming demodulated audio stream
     */
    public void setSampleRate(double sampleRate)
    {
        mSampleRate = sampleRate;

        // Block size targets 250ms for sufficient frequency resolution to reject adjacent CTCSS tones.
        // At 250ms, the first null is at sampleRate/blockSize Hz offset from center, which for typical
        // sample rates gives ~4 Hz resolution matching the minimum CTCSS tone spacing.
        mBlockSize = (int)(sampleRate * 0.25);

        // Ensure block size gives at least 10 full cycles of the target frequency for accuracy
        int minimumBlockSize = (int)(10.0 * sampleRate / mTargetFrequency);
        if(mBlockSize < minimumBlockSize)
        {
            mBlockSize = minimumBlockSize;
        }

        mCoefficient = getCoefficient(mTargetFrequency);

        double lowerFrequency = 0;
        double upperFrequency = 0;
        for(CTCSSFrequency frequency : CTCSSFrequency.values())
        {
            double value = frequency.getFrequency();
            if(value > 0 && value < mTargetFrequency && value > lowerFrequency)
            {
                lowerFrequency = value;
            }
            if(value > mTargetFrequency && (upperFrequency == 0 || value < upperFrequency))
            {
                upperFrequency = value;
            }
        }
        mLowerCoefficient = lowerFrequency > 0 ? getCoefficient(lowerFrequency) : 0;
        mUpperCoefficient = upperFrequency > 0 ? getCoefficient(upperFrequency) : 0;
        mLowerAdjacentLeakageRatio = lowerFrequency > 0 ? getInBandLeakageRatio(lowerFrequency) : 0;
        mUpperAdjacentLeakageRatio = upperFrequency > 0 ? getInBandLeakageRatio(upperFrequency) : 0;
        mLowerToleranceCoefficient = getCoefficient(mTargetFrequency * 0.98);
        mUpperToleranceCoefficient = getCoefficient(mTargetFrequency * 1.02);

        reset();
    }

    private double getCoefficient(double frequency)
    {
        return 2.0 * Math.cos(2.0 * Math.PI * frequency / mSampleRate);
    }

    /**
     * Calculates how much an adjacent tone leaks into any accepted target measurement for this block size.
     */
    private double getInBandLeakageRatio(double signalFrequency)
    {
        double inBandResponse = Math.max(getResponseMagnitude(signalFrequency, mTargetFrequency),
                Math.max(getResponseMagnitude(signalFrequency, mTargetFrequency * 0.98),
                        getResponseMagnitude(signalFrequency, mTargetFrequency * 1.02)));
        double adjacentResponse = getResponseMagnitude(signalFrequency, signalFrequency);
        return adjacentResponse > 0 ? inBandResponse / adjacentResponse : 0;
    }

    private double getResponseMagnitude(double signalFrequency, double detectorFrequency)
    {
        double coefficient = getCoefficient(detectorFrequency);
        double sPrev = 0;
        double sPrev2 = 0;

        for(int sample = 0; sample < mBlockSize; sample++)
        {
            double value = Math.sin(2.0 * Math.PI * signalFrequency * sample / mSampleRate) +
                    coefficient * sPrev - sPrev2;
            sPrev2 = sPrev;
            sPrev = value;
        }

        return getMagnitudeSquared(sPrev, sPrev2, coefficient);
    }

    /**
     * Resets accumulated samples and detection state.
     */
    public void reset()
    {
        mS = 0;
        mSPrev = 0;
        mSPrev2 = 0;
        mLowerS = 0;
        mLowerSPrev = 0;
        mLowerSPrev2 = 0;
        mUpperS = 0;
        mUpperSPrev = 0;
        mUpperSPrev2 = 0;
        mLowerToleranceS = 0;
        mLowerToleranceSPrev = 0;
        mLowerToleranceSPrev2 = 0;
        mUpperToleranceS = 0;
        mUpperToleranceSPrev = 0;
        mUpperToleranceSPrev2 = 0;
        mSampleCount = 0;
        mHysteresisCount = 0;
        mToneDetected = false;
    }

    /**
     * Sets a listener that is notified each time the tone transitions from not-detected to detected.
     * @param listener to invoke on detection, or null to remove.
     */
    public void setToneDetectedListener(Runnable listener)
    {
        mToneDetectedListener = listener;
    }

    /**
     * Sets a listener that is notified each time the tone transitions from detected to not-detected.
     * @param listener to invoke on tone loss, or null to remove.
     */
    public void setToneLostListener(Runnable listener)
    {
        mToneLostListener = listener;
    }

    /**
     * Indicates if the CTCSS tone is currently detected
     * @return true if the tone is detected with sufficient confidence
     */
    public boolean isToneDetected()
    {
        return mToneDetected;
    }

    /**
     * Processes a buffer of demodulated audio samples for CTCSS tone detection.
     * @param samples demodulated audio (pre-noise-squelch, pre-high-pass-filter)
     */
    public void process(float[] samples)
    {
        for(float sample : samples)
        {
            // Goertzel iteration for center frequency
            mS = sample + (mCoefficient * mSPrev) - mSPrev2;
            mSPrev2 = mSPrev;
            mSPrev = mS;

            if(mLowerCoefficient != 0)
            {
                mLowerS = sample + (mLowerCoefficient * mLowerSPrev) - mLowerSPrev2;
                mLowerSPrev2 = mLowerSPrev;
                mLowerSPrev = mLowerS;
            }
            if(mUpperCoefficient != 0)
            {
                mUpperS = sample + (mUpperCoefficient * mUpperSPrev) - mUpperSPrev2;
                mUpperSPrev2 = mUpperSPrev;
                mUpperSPrev = mUpperS;
            }

            mLowerToleranceS = sample + (mLowerToleranceCoefficient * mLowerToleranceSPrev) -
                    mLowerToleranceSPrev2;
            mLowerToleranceSPrev2 = mLowerToleranceSPrev;
            mLowerToleranceSPrev = mLowerToleranceS;
            mUpperToleranceS = sample + (mUpperToleranceCoefficient * mUpperToleranceSPrev) -
                    mUpperToleranceSPrev2;
            mUpperToleranceSPrev2 = mUpperToleranceSPrev;
            mUpperToleranceSPrev = mUpperToleranceS;

            mSampleCount++;

            if(mSampleCount >= mBlockSize)
            {
                evaluateBlock();
                resetGoertzelState();
                mSampleCount = 0;
            }
        }
    }

    /**
     * Evaluates the current block for tone presence using the Goertzel magnitude result.
     * Compares the tone energy at the target frequency against a fixed reference level
     * calibrated for typical CTCSS tone amplitudes.
     */
    private void evaluateBlock()
    {
        double magnitude = getMagnitudeSquared(mSPrev, mSPrev2, mCoefficient);
        double lowerMagnitude = mLowerCoefficient != 0 ?
                getMagnitudeSquared(mLowerSPrev, mLowerSPrev2, mLowerCoefficient) : 0;
        double upperMagnitude = mUpperCoefficient != 0 ?
                getMagnitudeSquared(mUpperSPrev, mUpperSPrev2, mUpperCoefficient) : 0;
        double lowerToleranceMagnitude = getMagnitudeSquared(mLowerToleranceSPrev, mLowerToleranceSPrev2,
                mLowerToleranceCoefficient);
        double upperToleranceMagnitude = getMagnitudeSquared(mUpperToleranceSPrev, mUpperToleranceSPrev2,
                mUpperToleranceCoefficient);
        double inBandMagnitude = Math.max(magnitude, Math.max(lowerToleranceMagnitude, upperToleranceMagnitude));

        // Fixed reference level scaled to block size
        double totalPower = (double)mBlockSize * mBlockSize * 0.01;

        boolean tonePresent;
        if(totalPower <= 0)
        {
            tonePresent = false;
        }
        else
        {
            double ratio = 10.0 * Math.log10(inBandMagnitude / totalPower);
            double adjacentLeakage = Math.max(lowerMagnitude * mLowerAdjacentLeakageRatio,
                    upperMagnitude * mUpperAdjacentLeakageRatio);
            tonePresent = ratio > DETECTION_THRESHOLD_DB && inBandMagnitude > adjacentLeakage * 1.01;
        }

        // Apply hysteresis
        if(tonePresent)
        {
            mHysteresisCount = Math.min(mHysteresisCount + 1, mHysteresisCloseThreshold);
        }
        else
        {
            mHysteresisCount = Math.max(mHysteresisCount - 1, 0);
        }

        if(!mToneDetected && mHysteresisCount >= mHysteresisOpenThreshold)
        {
            mToneDetected = true;

            if(mToneDetectedListener != null)
            {
                mToneDetectedListener.run();
            }
        }
        else if(mToneDetected && mHysteresisCount <= 0)
        {
            mToneDetected = false;

            if(mToneLostListener != null)
            {
                mToneLostListener.run();
            }
        }
    }

    /**
     * Calculates the magnitude squared from the Goertzel state variables
     */
    private double getMagnitudeSquared(double sPrev, double sPrev2, double coefficient)
    {
        return (sPrev * sPrev) + (sPrev2 * sPrev2) - (coefficient * sPrev * sPrev2);
    }

    /**
     * Resets the Goertzel state variables for the next block
     */
    private void resetGoertzelState()
    {
        mS = 0;
        mSPrev = 0;
        mSPrev2 = 0;
        mLowerS = 0;
        mLowerSPrev = 0;
        mLowerSPrev2 = 0;
        mUpperS = 0;
        mUpperSPrev = 0;
        mUpperSPrev2 = 0;
        mLowerToleranceS = 0;
        mLowerToleranceSPrev = 0;
        mLowerToleranceSPrev2 = 0;
        mUpperToleranceS = 0;
        mUpperToleranceSPrev = 0;
        mUpperToleranceSPrev2 = 0;
    }
}
