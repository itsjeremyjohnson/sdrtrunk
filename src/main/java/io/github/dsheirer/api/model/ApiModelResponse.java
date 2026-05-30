/*
 * *****************************************************************************
 * Copyright (C) 2026 Dennis Sheirer
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
package io.github.dsheirer.api.model;

import java.time.Instant;
import java.util.List;

/**
 * Versioned, paginated read-model response envelope for local API collections.
 */
public class ApiModelResponse<T>
{
    private static final String SCHEMA_VERSION = "1.0";

    private String mSchemaVersion = SCHEMA_VERSION;
    private String mGeneratedAt = Instant.now().toString();
    private List<T> mItems;
    private int mTotal;
    private int mLimit;
    private int mOffset;

    public ApiModelResponse(List<T> items, int total, int limit, int offset)
    {
        mItems = items;
        mTotal = total;
        mLimit = limit;
        mOffset = offset;
    }

    public static <T> ApiModelResponse<T> of(List<T> items, int total, int limit, int offset)
    {
        return new ApiModelResponse<>(items, total, limit, offset);
    }

    public String getSchemaVersion()
    {
        return mSchemaVersion;
    }

    public String getGeneratedAt()
    {
        return mGeneratedAt;
    }

    public List<T> getItems()
    {
        return mItems;
    }

    public int getTotal()
    {
        return mTotal;
    }

    public int getLimit()
    {
        return mLimit;
    }

    public int getOffset()
    {
        return mOffset;
    }
}
