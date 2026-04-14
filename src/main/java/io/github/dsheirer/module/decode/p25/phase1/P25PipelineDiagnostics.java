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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * P25 pipeline diagnostics with per-channel UI toggle.
 * Writes structured events to a log file. Zero cost for channels with diagnostics disabled.
 *
 * Channels are enabled/disabled at runtime via {@link #enableChannel(String)} and
 * {@link #disableChannel(String)}, driven by the "Pipeline Diagnostics" toggle
 * in the P25 Phase 1 channel configuration editor.
 *
 * Each log line: timestamp | channel | stage | event | detail
 *
 * Output file: p25-pipeline-diag.log (configurable via -Dp25.diag.file=path)
 */
public class P25PipelineDiagnostics
{
    private static final String DIAG_FILE = System.getProperty("p25.diag.file", "p25-pipeline-diag.log");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault());
    private static final Set<String> ENABLED_CHANNELS = new CopyOnWriteArraySet<>();
    private static PrintWriter sWriter;

    public static synchronized void enableChannel(String channelName)
    {
        if(channelName == null) return;
        ENABLED_CHANNELS.add(channelName);
        ensureWriter();
        log(channelName, "DIAG", "ENABLED", "Pipeline diagnostics enabled for channel");
    }

    public static void disableChannel(String channelName)
    {
        if(channelName == null) return;
        log(channelName, "DIAG", "DISABLED", "Pipeline diagnostics disabled for channel");
        ENABLED_CHANNELS.remove(channelName);
    }

    private static synchronized void ensureWriter()
    {
        if(sWriter == null)
        {
            try
            {
                sWriter = new PrintWriter(new FileWriter(DIAG_FILE, true), true);
                sWriter.println("--- P25 Pipeline Diagnostics started at " + Instant.now() + " ---");
            }
            catch(IOException e)
            {
                System.err.println("P25 diag: failed to open " + DIAG_FILE + ": " + e.getMessage());
            }
        }
    }

    public static boolean isEnabled()
    {
        return !ENABLED_CHANNELS.isEmpty() && sWriter != null;
    }

    public static boolean isEnabled(String channel)
    {
        return channel != null && ENABLED_CHANNELS.contains(channel) && sWriter != null;
    }

    public static void log(String channel, String stage, String event, String detail)
    {
        if(sWriter != null && ENABLED_CHANNELS.contains(channel))
        {
            sWriter.printf("%s | %-20s | %-12s | %-24s | %s%n",
                TIME_FMT.format(Instant.now()), channel, stage, event, detail);
        }
    }

    public static void log(String channel, String stage, String event)
    {
        log(channel, stage, event, "");
    }
}
