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
package io.github.dsheirer.api;

import io.github.dsheirer.alias.Alias;
import io.github.dsheirer.api.model.ApiAliasList;
import io.github.dsheirer.api.model.ApiBroadcastConfiguration;
import io.github.dsheirer.api.model.ApiChannel;
import io.github.dsheirer.api.model.ApiModelResponse;
import io.github.dsheirer.api.model.ApiRuntimeSummary;
import io.github.dsheirer.api.model.ApiTuner;
import io.github.dsheirer.audio.broadcast.BroadcastConfiguration;
import io.github.dsheirer.audio.broadcast.BroadcastModel;
import io.github.dsheirer.audio.broadcast.ConfiguredBroadcast;
import io.github.dsheirer.controller.channel.Channel;
import io.github.dsheirer.controller.channel.ChannelModel;
import io.github.dsheirer.playlist.PlaylistManager;
import io.github.dsheirer.source.tuner.manager.DiscoveredTuner;
import io.github.dsheirer.source.tuner.manager.TunerManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Adapts live sdrtrunk models into immutable, secret-safe local API DTOs.
 */
public class RuntimeLocalControlApiModelProvider implements LocalControlApiModelProvider
{
    private final PlaylistManager mPlaylistManager;
    private final TunerManager mTunerManager;

    public RuntimeLocalControlApiModelProvider(PlaylistManager playlistManager, TunerManager tunerManager)
    {
        mPlaylistManager = playlistManager;
        mTunerManager = tunerManager;
    }

    @Override
    public ApiModelResponse<ApiChannel> getChannels(int limit, int offset)
    {
        List<ApiChannel> channels = new ArrayList<>();
        ChannelModel channelModel = mPlaylistManager.getChannelModel();

        for(Channel channel: channelModel.getChannels())
        {
            channels.add(toApiChannel(channel));
        }

        return page(channels, limit, offset);
    }

    @Override
    public ApiModelResponse<ApiAliasList> getAliases(int limit, int offset)
    {
        Map<String, Integer> countsByList = new LinkedHashMap<>();

        for(Alias alias: mPlaylistManager.getAliasModel().getAliases())
        {
            String aliasListName = alias.getAliasListName();

            if(aliasListName != null && !aliasListName.isEmpty())
            {
                countsByList.put(aliasListName, countsByList.getOrDefault(aliasListName, 0) + 1);
            }
        }

        List<ApiAliasList> aliases = countsByList.entrySet().stream()
            .map(entry -> new ApiAliasList(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());

        return page(aliases, limit, offset);
    }

    @Override
    public ApiModelResponse<ApiTuner> getTuners(int limit, int offset)
    {
        List<ApiTuner> tuners = new ArrayList<>();

        for(int x = 0; x < mTunerManager.getDiscoveredTunerModel().getRowCount(); x++)
        {
            DiscoveredTuner discoveredTuner = mTunerManager.getDiscoveredTunerModel().getDiscoveredTuner(x);

            if(discoveredTuner != null)
            {
                String status = discoveredTuner.getTunerStatus() != null ? discoveredTuner.getTunerStatus().name() : null;
                String tunerClass = discoveredTuner.getTunerClass() != null ? discoveredTuner.getTunerClass().name() : null;
                tuners.add(new ApiTuner(discoveredTuner.getId(), tunerClass, status, discoveredTuner.isAvailable(),
                    discoveredTuner.isEnabled(), discoveredTuner.getErrorMessage()));
            }
        }

        return page(tuners, limit, offset);
    }

    @Override
    public ApiModelResponse<ApiBroadcastConfiguration> getBroadcasts(int limit, int offset)
    {
        List<ApiBroadcastConfiguration> broadcasts = new ArrayList<>();
        BroadcastModel broadcastModel = mPlaylistManager.getBroadcastModel();

        for(ConfiguredBroadcast configuredBroadcast: broadcastModel.getConfiguredBroadcasts())
        {
            BroadcastConfiguration configuration = configuredBroadcast.getBroadcastConfiguration();
            String format = configuration.getBroadcastFormat() != null ? configuration.getBroadcastFormat().name() : null;
            String status = configuredBroadcast.broadcastStateProperty().get() != null ?
                configuredBroadcast.broadcastStateProperty().get().name() : null;
            broadcasts.add(new ApiBroadcastConfiguration(String.valueOf(configuration.getId()), format,
                configuration.getName(), configuration.isEnabled(), status, configuration.hasPassword()));
        }

        return page(broadcasts, limit, offset);
    }

    @Override
    public ApiRuntimeSummary getRuntimeSummary()
    {
        int configuredChannels = mPlaylistManager.getChannelModel().getChannels().size();
        int activeChannels = (int)mPlaylistManager.getChannelModel().getChannels().stream()
            .filter(Channel::isProcessing)
            .count();
        int aliasLists = (int)mPlaylistManager.getAliasModel().getAliases().stream()
            .map(Alias::getAliasListName)
            .filter(aliasListName -> aliasListName != null && !aliasListName.isEmpty())
            .distinct()
            .count();
        int tuners = mTunerManager.getDiscoveredTunerModel().getRowCount();
        int broadcasts = mPlaylistManager.getBroadcastModel().getConfiguredBroadcasts().size();

        return new ApiRuntimeSummary(configuredChannels, activeChannels, aliasLists, tuners, broadcasts);
    }

    private ApiChannel toApiChannel(Channel channel)
    {
        String decoderType = channel.getDecodeConfiguration() != null && channel.getDecodeConfiguration().getDecoderType() != null ?
            channel.getDecodeConfiguration().getDecoderType().name() : null;
        List<Long> frequencies = channel.getFrequencyList() != null ? new ArrayList<>(channel.getFrequencyList()) : List.of();

        return new ApiChannel(channel.getChannelID(), channel.getName(), channel.getSystem(), channel.getSite(),
            channel.getAliasListName(), channel.isAutoStart(), channel.getAutoStartOrder(), channel.isProcessing(),
            channel.getChannelType() != null ? channel.getChannelType().name() : null, decoderType, frequencies);
    }

    private <T> ApiModelResponse<T> page(List<T> items, int limit, int offset)
    {
        int safeOffset = Math.min(Math.max(0, offset), items.size());
        int safeLimit = Math.max(1, limit);
        int end = Math.min(items.size(), safeOffset + safeLimit);

        return ApiModelResponse.of(Collections.unmodifiableList(new ArrayList<>(items.subList(safeOffset, end))),
            items.size(), safeLimit, safeOffset);
    }
}
