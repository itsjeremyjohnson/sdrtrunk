/*
 * *****************************************************************************
 * Copyright (C) 2026 Dennis Sheirer
 * ****************************************************************************
 */
package io.github.dsheirer.api.model;

import java.time.Instant;

/**
 * Runtime summary DTO for operator dashboards and monitoring tools.
 */
public class ApiRuntimeSummary
{
    private static final String SCHEMA_VERSION = "1.0";

    private String mSchemaVersion = SCHEMA_VERSION;
    private String mGeneratedAt = Instant.now().toString();
    private int mConfiguredChannelCount;
    private int mActiveChannelCount;
    private int mAliasListCount;
    private int mTunerCount;
    private int mBroadcastCount;

    public ApiRuntimeSummary(int configuredChannelCount, int activeChannelCount, int aliasListCount, int tunerCount,
                             int broadcastCount)
    {
        mConfiguredChannelCount = configuredChannelCount;
        mActiveChannelCount = activeChannelCount;
        mAliasListCount = aliasListCount;
        mTunerCount = tunerCount;
        mBroadcastCount = broadcastCount;
    }

    public String getSchemaVersion(){ return mSchemaVersion; }
    public String getGeneratedAt(){ return mGeneratedAt; }
    public int getConfiguredChannelCount(){ return mConfiguredChannelCount; }
    public int getActiveChannelCount(){ return mActiveChannelCount; }
    public int getAliasListCount(){ return mAliasListCount; }
    public int getTunerCount(){ return mTunerCount; }
    public int getBroadcastCount(){ return mBroadcastCount; }
}
