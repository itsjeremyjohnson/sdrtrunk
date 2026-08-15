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
package io.github.dsheirer.gui.playlist.record;

import io.github.dsheirer.record.RecorderType;
import io.github.dsheirer.record.config.RecordConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordConfigurationEditorTest
{
    @Test
    void activityRecorderEnumEnablesEditorToggle()
    {
        RecordConfiguration configuration = new RecordConfiguration();
        configuration.addRecorder(RecorderType.ACTIVITY_BASEBAND);

        assertTrue(RecordConfigurationEditor.isActivityRecordingEnabled(configuration));
    }
}
