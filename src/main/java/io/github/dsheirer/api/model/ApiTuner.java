/*
 * *****************************************************************************
 * Copyright (C) 2026 Dennis Sheirer
 * ****************************************************************************
 */
package io.github.dsheirer.api.model;

/**
 * Read-only discovered tuner DTO.
 */
public class ApiTuner
{
    private String mId;
    private String mTunerClass;
    private String mStatus;
    private boolean mAvailable;
    private boolean mEnabled;
    private String mErrorMessage;

    public ApiTuner(String id, String tunerClass, String status, boolean available, boolean enabled, String errorMessage)
    {
        mId = id;
        mTunerClass = tunerClass;
        mStatus = status;
        mAvailable = available;
        mEnabled = enabled;
        mErrorMessage = errorMessage;
    }

    public String getId(){ return mId; }
    public String getTunerClass(){ return mTunerClass; }
    public String getStatus(){ return mStatus; }
    public boolean isAvailable(){ return mAvailable; }
    public boolean isEnabled(){ return mEnabled; }
    public String getErrorMessage(){ return mErrorMessage; }
}
