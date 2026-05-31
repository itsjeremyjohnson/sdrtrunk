/*
 * *****************************************************************************
 * Copyright (C) 2026 Dennis Sheirer
 * ****************************************************************************
 */
package io.github.dsheirer.api.model;

import java.time.Instant;
import java.util.List;

/**
 * Append-only local control API audit record DTO.
 */
public class ApiAuditRecord
{
    private String mTimestamp;
    private String mEndpoint;
    private String mActorMode;
    private String mAction;
    private String mChannelId;
    private boolean mDryRun;
    private String mResult;
    private List<String> mChangedFields;

    public ApiAuditRecord(String endpoint, String actorMode, String action, String channelId, boolean dryRun,
                          String result, List<String> changedFields)
    {
        mTimestamp = Instant.now().toString();
        mEndpoint = endpoint;
        mActorMode = actorMode;
        mAction = action;
        mChannelId = channelId;
        mDryRun = dryRun;
        mResult = result;
        mChangedFields = changedFields;
    }

    public String getTimestamp()
    {
        return mTimestamp;
    }

    public String getEndpoint()
    {
        return mEndpoint;
    }

    public String getActorMode()
    {
        return mActorMode;
    }

    public String getAction()
    {
        return mAction;
    }

    public String getChannelId()
    {
        return mChannelId;
    }

    public boolean isDryRun()
    {
        return mDryRun;
    }

    public String getResult()
    {
        return mResult;
    }

    public List<String> getChangedFields()
    {
        return mChangedFields;
    }
}
