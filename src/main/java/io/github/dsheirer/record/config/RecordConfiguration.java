/*
 * *****************************************************************************
 * Copyright (C) 2014-2022 Dennis Sheirer
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
package io.github.dsheirer.record.config;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import io.github.dsheirer.controller.config.Configuration;
import io.github.dsheirer.record.RecorderType;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains the types of recordings specified for a channel
 */
public class RecordConfiguration extends Configuration
{
    public static final float DEFAULT_ACTIVITY_SQUELCH_THRESHOLD = -70.0f;

    /**
     * Recording types requested for this configuration
     */
    private List<RecorderType> mRecorders = new ArrayList<>();
    private List<String> mRecorderValues = new ArrayList<>();
    private boolean mActivityTriggeredRecording = false;
    private float mActivitySquelchThreshold = DEFAULT_ACTIVITY_SQUELCH_THRESHOLD;

    /**
     * Constructs a recording configuration instance
     */
    public RecordConfiguration()
    {
        //Empty constructor required for deserialization
    }

    /**
     * List of recorder types specified in this configuration
     */
    @JsonIgnore
    public List<RecorderType> getRecorders()
    {
        return mRecorders;
    }

    /**
     * Sets the complete runtime recorder list and serialized values.
     */
    @JsonIgnore
    public void setRecorders(List<RecorderType> recorders)
    {
        mRecorders = recorders == null ? new ArrayList<>() : new ArrayList<>(recorders);
        mRecorderValues = new ArrayList<>(mRecorders.stream().map(RecorderType::name).toList());
    }

    @JacksonXmlProperty(isAttribute = false, localName = "recorder")
    @JsonGetter("recorder")
    public List<String> getRecorderValues()
    {
        return mRecorderValues;
    }

    @JsonSetter("recorder")
    public void setRecorderValues(List<String> recorderValues)
    {
        mRecorderValues = recorderValues == null ? new ArrayList<>() : new ArrayList<>(recorderValues);
        mRecorders = new ArrayList<>();

        for(String value : mRecorderValues)
        {
            try
            {
                mRecorders.add(RecorderType.valueOf(value));
            }
            catch(IllegalArgumentException ignored)
            {
                // Preserve future recorder values for round-trip, but omit unsupported runtime recorders.
            }
        }
    }

    /**
     * Adds the recorder type to the configuration
     */
    public void addRecorder(RecorderType recorder)
    {
        mRecorders.add(recorder);
        mRecorderValues.add(recorder.name());
    }

    /**
     * Removes all occurrences of a supported recorder type.
     * @return number of removed values
     */
    public int removeRecorder(RecorderType recorder)
    {
        int initialSize = mRecorders.size();
        mRecorders.removeIf(value -> value == recorder);
        mRecorderValues.removeIf(value -> recorder.name().equals(value));
        return initialSize - mRecorders.size();
    }

    /**
     * Clears supported recorder types while preserving unknown serialized values.
     */
    public void clearRecorders()
    {
        mRecorders.clear();
        mRecorderValues.removeIf(value -> {
            try
            {
                RecorderType.valueOf(value);
                return true;
            }
            catch(IllegalArgumentException ignored)
            {
                return false;
            }
        });
    }

    /**
     * Indicates if this configuration has the specified recorder type
     * @param recorderType to check
     * @return true if this configuration contains the specified recorder type
     */
    public boolean contains(RecorderType recorderType)
    {
        return mRecorders.contains(recorderType);
    }

    /**
     * Indicates if activity-triggered baseband recording is enabled.
     * @return true if enabled
     */
    @JacksonXmlProperty(isAttribute = true, localName = "activityTriggeredRecording")
    public boolean isActivityTriggeredRecording()
    {
        return mActivityTriggeredRecording;
    }

    /**
     * Sets activity-triggered baseband recording enabled state.
     * @param enabled true to enable
     */
    public void setActivityTriggeredRecording(boolean enabled)
    {
        mActivityTriggeredRecording = enabled;
    }

    /**
     * Gets the squelch threshold in dB for activity-triggered recording.
     * @return threshold in dB (default -70.0)
     */
    @JacksonXmlProperty(isAttribute = true, localName = "activitySquelchThreshold")
    public float getActivitySquelchThreshold()
    {
        return mActivitySquelchThreshold;
    }

    /**
     * Sets the squelch threshold in dB for activity-triggered recording.
     * @param threshold in dB
     */
    public void setActivitySquelchThreshold(float threshold)
    {
        mActivitySquelchThreshold = Math.max(-100.0f, Math.min(-30.0f, threshold));
    }

    /**
     * Indicates if activity-triggered recording has a non-default squelch threshold.
     */
    @JsonIgnore
    public boolean hasCustomSquelchThreshold()
    {
        return mActivitySquelchThreshold != DEFAULT_ACTIVITY_SQUELCH_THRESHOLD;
    }
}
