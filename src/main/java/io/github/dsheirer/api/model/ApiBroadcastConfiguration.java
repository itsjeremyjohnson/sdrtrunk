/*
 * *****************************************************************************
 * Copyright (C) 2026 Dennis Sheirer
 * ****************************************************************************
 */
package io.github.dsheirer.api.model;

/**
 * Secret-redacted broadcast configuration/runtime DTO.
 */
public class ApiBroadcastConfiguration
{
    private String mId;
    private String mFormat;
    private String mName;
    private boolean mEnabled;
    private String mStatus;
    private boolean mSecretRedacted;

    public ApiBroadcastConfiguration(String id, String format, String name, boolean enabled, String status,
                                     boolean secretRedacted)
    {
        mId = id;
        mFormat = format;
        mName = name;
        mEnabled = enabled;
        mStatus = status;
        mSecretRedacted = secretRedacted;
    }

    public String getId(){ return mId; }
    public String getFormat(){ return mFormat; }
    public String getName(){ return mName; }
    public boolean isEnabled(){ return mEnabled; }
    public String getStatus(){ return mStatus; }
    public boolean isSecretRedacted(){ return mSecretRedacted; }
}
