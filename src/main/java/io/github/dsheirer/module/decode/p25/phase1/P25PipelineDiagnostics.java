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

/**
 * P25 pipeline diagnostics with per-channel log files.
 * Each enabled channel gets its own log file in the SDRTrunk logs directory.
 * Zero cost for channels with diagnostics disabled.
 *
 * Channels are enabled/disabled via the "Pipeline Diagnostics" toggle
 * in the P25 Phase 1 channel configuration editor.
 *
 * Each log line: timestamp | stage | event | detail
 * File location: {SDRTrunk logs dir}/p25-diag-{ChannelName}.log
 */
public class P25PipelineDiagnostics
{
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault());
    private static final Map<String, PrintWriter> CHANNEL_WRITERS = new ConcurrentHashMap<>();

    /**
     * Enable diagnostics for a channel. Creates a per-channel log file in the logs directory.
     * @param channelName the channel name (used in the log filename)
     * @param logsDirectory the SDRTrunk logs directory path
     */
    public static void enableChannel(String channelName, Path logsDirectory)
    {
        if(channelName == null || logsDirectory == null)
        {
            System.out.println("P25 diag: enableChannel called with null — channel=" + channelName + " dir=" + logsDirectory);
            return;
        }
        if(CHANNEL_WRITERS.containsKey(channelName)) return;

        try
        {
            Files.createDirectories(logsDirectory);
            String safeName = channelName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
            Path logFile = logsDirectory.resolve("p25-diag-" + safeName + ".log");
            System.out.println("P25 diag: enabling diagnostics for [" + channelName + "] → " + logFile);
            PrintWriter writer = new PrintWriter(new FileWriter(logFile.toFile(), true), true);
            writer.printf("--- P25 Pipeline Diagnostics for [%s] started at %s ---%n", channelName, Instant.now());
            CHANNEL_WRITERS.put(channelName, writer);
        }
        catch(IOException e)
        {
            System.err.println("P25 diag: failed to open log for channel [" + channelName + "]: " + e.getMessage());
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
        }
    }

    /**
     * Returns true if diagnostics are enabled globally (any channel active).
     */
    public static boolean isEnabled()
    {
        return !CHANNEL_WRITERS.isEmpty();
    }

    /**
     * Returns true if diagnostics are enabled for the given channel name.
     */
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
