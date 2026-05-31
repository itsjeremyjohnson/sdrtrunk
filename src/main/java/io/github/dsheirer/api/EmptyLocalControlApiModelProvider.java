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
 * Empty model provider used before the application runtime models are attached.
 */
public class EmptyLocalControlApiModelProvider implements LocalControlApiModelProvider
{
    public static final EmptyLocalControlApiModelProvider INSTANCE = new EmptyLocalControlApiModelProvider();

    private EmptyLocalControlApiModelProvider()
    {
    }

    @Override
    public ApiModelResponse<ApiChannel> getChannels(int limit, int offset)
    {
        return ApiModelResponse.of(List.of(), 0, limit, offset);
    }

    @Override
    public ApiModelResponse<ApiAliasList> getAliases(int limit, int offset)
    {
        return ApiModelResponse.of(List.of(), 0, limit, offset);
    }

    @Override
    public ApiModelResponse<ApiTuner> getTuners(int limit, int offset)
    {
        return ApiModelResponse.of(List.of(), 0, limit, offset);
    }

    @Override
    public ApiModelResponse<ApiBroadcastConfiguration> getBroadcasts(int limit, int offset)
    {
        return ApiModelResponse.of(List.of(), 0, limit, offset);
    }

    @Override
    public ApiRuntimeSummary getRuntimeSummary()
    {
        return new ApiRuntimeSummary(0, 0, 0, 0, 0);
    }

    @Override
    public ApiModelResponse<ApiAuditRecord> getAuditRecords(int limit, int offset)
    {
        return ApiModelResponse.of(List.of(), 0, limit, offset);
    }

    @Override
    public ApiControlResult controlChannel(String channelId, String action, boolean dryRun, String endpoint)
    {
        throw new IllegalArgumentException("runtime_models_not_attached");
    }
}
