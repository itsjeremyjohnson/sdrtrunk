/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
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
package io.github.dsheirer.module.decode.p25.phase1;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * P25 pipeline diagnostics with per-channel log files.
 * Each enabled channel gets its own log file under {SDRTrunk logs}/p25_logs/.
 * Zero cost for channels with diagnostics disabled.
 *
 * Enabled via the "Pipeline Diagnostics" toggle in the P25 Phase 1 channel editor.
 * File location: {SDRTrunk logs dir}/p25_logs/p25-diag-{ChannelName}.log
 */
public class P25PipelineDiagnostics
{
    private static final Logger mLog = LoggerFactory.getLogger(P25PipelineDiagnostics.class);
    private static final String SUBDIR = "p25_logs";
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault());
    private static final Map<String, PrintWriter> CHANNEL_WRITERS = new ConcurrentHashMap<>();

    /**
     * Enable diagnostics for a channel. Creates a per-channel log file.
     * @param channelName the channel name (used in the log filename)
     * @param logsDirectory the SDRTrunk application logs directory
     */
    public static void enableChannel(String channelName, Path logsDirectory)
    {
        if(channelName == null || logsDirectory == null) return;
        if(CHANNEL_WRITERS.containsKey(channelName)) return;

        try
        {
            Path p25LogDir = logsDirectory.resolve(SUBDIR);
            Files.createDirectories(p25LogDir);
            String safeName = channelName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
            Path logFile = p25LogDir.resolve("p25-diag-" + safeName + ".log");
            PrintWriter writer = new PrintWriter(new FileWriter(logFile.toFile(), true), true);
            writer.printf("--- P25 Pipeline Diagnostics for [%s] started at %s ---%n", channelName, Instant.now());
            CHANNEL_WRITERS.put(channelName, writer);
            mLog.info("Pipeline diagnostics enabled for [{}] → {}", channelName, logFile);
        }
        catch(IOException e)
        {
            mLog.error("Failed to open pipeline diagnostics log for [{}]: {}", channelName, e.getMessage());
        }
    }

    /**
     * Disable diagnostics for a channel. Closes the log file.
     */
    public static void disableChannel(String channelName)
    {
        if(channelName == null) return;
        PrintWriter writer = CHANNEL_WRITERS.remove(channelName);
        if(writer != null)
        {
            writer.printf("--- P25 Pipeline Diagnostics for [%s] stopped at %s ---%n", channelName, Instant.now());
            writer.close();
            mLog.info("Pipeline diagnostics disabled for [{}]", channelName);
        }
    }

    public static boolean isEnabled()
    {
        return !CHANNEL_WRITERS.isEmpty();
    }

    public static boolean isEnabled(String channel)
    {
        return channel != null && CHANNEL_WRITERS.containsKey(channel);
    }

    public static void log(String channel, String stage, String event, String detail)
    {
        PrintWriter writer = CHANNEL_WRITERS.get(channel);
        if(writer != null)
        {
            writer.printf("%s | %-12s | %-24s | %s%n",
                TIME_FMT.format(Instant.now()), stage, event, detail);
        }
    }

    public static void log(String channel, String stage, String event)
    {
        log(channel, stage, event, "");
    }
}
