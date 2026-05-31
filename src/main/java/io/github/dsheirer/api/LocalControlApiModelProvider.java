/*
 * *****************************************************************************
 * Copyright (C) 2026 Dennis Sheirer
 * ****************************************************************************
 */
package io.github.dsheirer.api;

import io.github.dsheirer.api.model.ApiAliasList;
import io.github.dsheirer.api.model.ApiAuditRecord;
import io.github.dsheirer.api.model.ApiBroadcastConfiguration;
import io.github.dsheirer.api.model.ApiChannel;
import io.github.dsheirer.api.model.ApiControlResult;
import io.github.dsheirer.api.model.ApiModelResponse;
import io.github.dsheirer.api.model.ApiRuntimeSummary;
import io.github.dsheirer.api.model.ApiTuner;
import java.util.List;

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

    default ApiModelResponse<ApiAuditRecord> getAuditRecords(int limit, int offset)
    {
        return ApiModelResponse.of(List.of(), 0, limit, offset);
    }

    default ApiControlResult controlChannel(String channelId, String action, boolean dryRun, String endpoint)
    {
        throw new IllegalArgumentException("runtime_control_not_supported");
    }
}
