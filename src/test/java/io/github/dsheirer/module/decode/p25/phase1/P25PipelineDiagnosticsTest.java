/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * *****************************************************************************
 */
package io.github.dsheirer.module.decode.p25.phase1;

import io.github.dsheirer.controller.channel.Channel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class P25PipelineDiagnosticsTest
{
    @TempDir
    Path mTempDirectory;

    @Test
    void logsOnlyUnderTheEnabledChannelKey() throws IOException
    {
        String channelName = "Test Channel";
        P25PipelineDiagnostics.enableChannel(channelName, mTempDirectory);
        try
        {
            P25PipelineDiagnostics.log("AUDIO", "AUDIO_MOD", "DROPPED");
            P25PipelineDiagnostics.log(channelName, "AUDIO_MOD", "LDU_PROCESS");

            Path log = mTempDirectory.resolve("p25_logs/p25-diag-Test_Channel.log");
            String contents = Files.readString(log);
            assertTrue(contents.contains("LDU_PROCESS"));
            assertFalse(contents.contains("DROPPED"));
        }
        finally
        {
            P25PipelineDiagnostics.disableChannel(channelName);
        }

        assertFalse(P25PipelineDiagnostics.isEnabled(channelName));
    }

    @Test
    void sameNameChannelsHaveIndependentWriters() throws IOException
    {
        Channel first = new Channel("Traffic");
        Channel second = new Channel("Traffic");
        String firstKey = P25PipelineDiagnostics.keyFor(first);
        String secondKey = P25PipelineDiagnostics.keyFor(second);

        P25PipelineDiagnostics.enableChannel(firstKey, mTempDirectory);
        P25PipelineDiagnostics.enableChannel(secondKey, mTempDirectory);
        try
        {
            P25PipelineDiagnostics.disableChannel(firstKey);
            P25PipelineDiagnostics.log(secondKey, "TEST", "STILL_ACTIVE");

            assertFalse(P25PipelineDiagnostics.isEnabled(firstKey));
            assertTrue(P25PipelineDiagnostics.isEnabled(secondKey));
            Path secondLog = mTempDirectory.resolve("p25_logs/p25-diag-" + secondKey + ".log");
            assertTrue(Files.readString(secondLog).contains("STILL_ACTIVE"));
        }
        finally
        {
            P25PipelineDiagnostics.disableChannel(firstKey);
            P25PipelineDiagnostics.disableChannel(secondKey);
        }
    }
}
