/*
 * *****************************************************************************
 * Copyright (C) 2026 Dennis Sheirer
 * ****************************************************************************
 */
package io.github.dsheirer.api.model;

/**
 * Alias-list summary DTO. Does not expose mutable alias objects.
 */
public class ApiAliasList
{
    private String mName;
    private int mAliasCount;

    public ApiAliasList(String name, int aliasCount)
    {
        mName = name;
        mAliasCount = aliasCount;
    }

    public String getName(){ return mName; }
    public int getAliasCount(){ return mAliasCount; }
}
