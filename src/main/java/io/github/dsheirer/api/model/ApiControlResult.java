/*
 * *****************************************************************************
 * Copyright (C) 2026 Dennis Sheirer
 * ****************************************************************************
 */
package io.github.dsheirer.api.model;

import java.util.List;

/**
 * Result for a local control API runtime mutation request.
 */
public class ApiControlResult
{
    private String mChannelId;
    private String mAction;
    private boolean mDryRun;
    private boolean mWouldChange;
    private String mResult;
    private List<String> mMessages;

    public ApiControlResult(String channelId, String action, boolean dryRun, boolean wouldChange, String result,
                            List<String> messages)
    {
        mChannelId = channelId;
        mAction = action;
        mDryRun = dryRun;
        mWouldChange = wouldChange;
        mResult = result;
        mMessages = messages;
    }

    public String getChannelId()
    {
        return mChannelId;
    }

    public String getAction()
    {
        return mAction;
    }

    public boolean isDryRun()
    {
        return mDryRun;
    }

    public boolean isWouldChange()
    {
        return mWouldChange;
    }

    public String getResult()
    {
        return mResult;
    }

    public List<String> getMessages()
    {
        return mMessages;
    }
}
