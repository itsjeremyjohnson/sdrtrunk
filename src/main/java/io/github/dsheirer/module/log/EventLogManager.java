/*
 * ******************************************************************************
 * sdrtrunk
 * Copyright (C) 2014-2018 Dennis Sheirer
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
 * *****************************************************************************
 */
package io.github.dsheirer.module.log;

import io.github.dsheirer.alias.AliasModel;
import io.github.dsheirer.controller.channel.Channel;
import io.github.dsheirer.module.Module;
import io.github.dsheirer.module.decode.dmr.DecodeConfigDMR;
import io.github.dsheirer.module.log.config.EventLogConfiguration;
import io.github.dsheirer.preference.UserPreferences;
import io.github.dsheirer.source.config.SourceConfigTuner;
import io.github.dsheirer.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.List;

public class EventLogManager
{
    private final static Logger mLog = LoggerFactory.getLogger(EventLogManager.class);

    private UserPreferences mUserPreferences;
    private AliasModel mAliasModel;
    private final ConcurrentHashMap<SystemLoggerKey, RollingSystemEventLogger> mSystemLoggers = new ConcurrentHashMap<>();

    public EventLogManager(AliasModel aliasModel, UserPreferences userPreferences)
    {
        mAliasModel = aliasModel;
        mUserPreferences = userPreferences;
    }

    public List<Module> getLoggers(Channel channel)
    {
        EventLogConfiguration config = channel.getEventLogConfiguration();
        String prefix = StringUtils.replaceIllegalCharacters(channel.getName());
        long frequency = 0;

        if(channel.getSourceConfiguration() instanceof SourceConfigTuner)
        {
            frequency = ((SourceConfigTuner)channel.getSourceConfiguration()).getFrequency();
        }

        List<Module> loggers = new ArrayList<Module>();

        for(EventLogType type : config.getLoggers())
        {
            switch(type)
            {
                case CALL_EVENT:
                case DECODED_MESSAGE:
                    if(channel.getChannelType() == Channel.ChannelType.STANDARD)
                    {
                        loggers.add(getLogger(type, prefix, frequency));
                    }
                    break;
                case TRAFFIC_CALL_EVENT:
                case TRAFFIC_DECODED_MESSAGE:
                    if(channel.getChannelType() == Channel.ChannelType.TRAFFIC)
                    {
                        loggers.add(getLogger(type, prefix, frequency));
                    }
                    break;
                case SYSTEM_CALL_EVENT:
                    //DMR traffic events are rebroadcast through the parent channel's aggregate event chain.
                    if(!(channel.isTrafficChannel() && channel.getDecodeConfiguration() instanceof DecodeConfigDMR))
                    {
                        loggers.add(getSystemEventLogModule(channel));
                    }
                    break;
            }
        }

        return loggers;
    }


    private Module getSystemEventLogModule(Channel channel)
    {
        String systemName = channel.getSystem();
        if(systemName == null || systemName.trim().isEmpty())
        {
            systemName = channel.getName();
        }
        String systemIdentity = systemName.trim();
        String filePrefix = getSystemLoggerFilePrefix(systemIdentity);
        Path eventLogDirectory = mUserPreferences.getDirectoryPreference().getDirectoryEventLog()
            .toAbsolutePath().normalize();
        SystemLoggerKey key = new SystemLoggerKey(eventLogDirectory, systemIdentity);
        RollingSystemEventLogger logger = mSystemLoggers.computeIfAbsent(key,
            ignored -> new RollingSystemEventLogger(eventLogDirectory, filePrefix));
        return new SystemEventLogModule(logger, mAliasModel);
    }

    static String getSystemLoggerFilePrefix(String systemIdentity)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(systemIdentity.getBytes(StandardCharsets.UTF_8));
            String identitySuffix = HexFormat.of().formatHex(digest, 0, 16);
            return StringUtils.replaceIllegalCharacters(systemIdentity) + "_" + identitySuffix;
        }
        catch(NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private record SystemLoggerKey(Path directory, String systemIdentity)
    {
    }

    public EventLogger getLogger(EventLogType eventLogType, String prefix, long frequency)
    {
        StringBuilder sb = new StringBuilder();

        sb.append(prefix);
        sb.append(eventLogType.getFileSuffix());
        sb.append(".log");

        Path eventLogDirectory = mUserPreferences.getDirectoryPreference().getDirectoryEventLog();

        switch(eventLogType)
        {
            case CALL_EVENT:
                return new DecodeEventLogger(mAliasModel, eventLogDirectory, sb.toString(), frequency);
            case DECODED_MESSAGE:
                return new MessageEventLogger(eventLogDirectory, sb.toString(), MessageEventLogger.Type.DECODED, frequency);
            case TRAFFIC_CALL_EVENT:
                return new DecodeEventLogger(mAliasModel, eventLogDirectory, sb.toString(), frequency);
            case TRAFFIC_DECODED_MESSAGE:
                return new MessageEventLogger(eventLogDirectory, sb.toString(), MessageEventLogger.Type.DECODED, frequency);
            default:
                return null;
        }
    }
}
