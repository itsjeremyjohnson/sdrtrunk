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
}
