/*
 * *****************************************************************************
 * Copyright (C) 2026 Dennis Sheirer
 * ****************************************************************************
 */
package io.github.dsheirer.api;

import io.github.dsheirer.api.model.ApiAliasList;
import io.github.dsheirer.api.model.ApiBroadcastConfiguration;
import io.github.dsheirer.api.model.ApiChannel;
import io.github.dsheirer.api.model.ApiModelResponse;
import io.github.dsheirer.api.model.ApiRuntimeSummary;
import io.github.dsheirer.api.model.ApiTuner;

/**
 * Supplies secret-safe read-only models to the local control API.
 */
public interface LocalControlApiModelProvider
{
    ApiModelResponse<ApiChannel> getChannels(int limit, int offset);
    ApiModelResponse<ApiAliasList> getAliases(int limit, int offset);
    ApiModelResponse<ApiTuner> getTuners(int limit, int offset);
    ApiModelResponse<ApiBroadcastConfiguration> getBroadcasts(int limit, int offset);
    ApiRuntimeSummary getRuntimeSummary();
}
