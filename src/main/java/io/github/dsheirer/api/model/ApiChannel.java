/*
 * *****************************************************************************
 * Copyright (C) 2026 Dennis Sheirer
 * ****************************************************************************
 */
package io.github.dsheirer.api.model;

import java.util.List;

/**
 * Secret-safe read-only channel configuration/runtime DTO.
 */
public class ApiChannel
{
    private int mId;
    private String mName;
    private String mSystem;
    private String mSite;
    private String mAliasList;
    private boolean mAutoStart;
    private int mAutoStartOrder;
    private boolean mProcessing;
    private String mChannelType;
    private String mDecoderType;
    private List<Long> mFrequencies;

    public ApiChannel(int id, String name, String system, String site, String aliasList, boolean autoStart,
                      int autoStartOrder, boolean processing, String channelType, String decoderType,
                      List<Long> frequencies)
    {
        mId = id;
        mName = name;
        mSystem = system;
        mSite = site;
        mAliasList = aliasList;
        mAutoStart = autoStart;
        mAutoStartOrder = autoStartOrder;
        mProcessing = processing;
        mChannelType = channelType;
        mDecoderType = decoderType;
        mFrequencies = frequencies;
    }

    public int getId(){ return mId; }
    public String getName(){ return mName; }
    public String getSystem(){ return mSystem; }
    public String getSite(){ return mSite; }
    public String getAliasList(){ return mAliasList; }
    public boolean isAutoStart(){ return mAutoStart; }
    public int getAutoStartOrder(){ return mAutoStartOrder; }
    public boolean isProcessing(){ return mProcessing; }
    public String getChannelType(){ return mChannelType; }
    public String getDecoderType(){ return mDecoderType; }
    public List<Long> getFrequencies(){ return mFrequencies; }
}
