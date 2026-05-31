/*
 * *****************************************************************************
 * Copyright (C) 2026 Jeremy Johnson
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
package io.github.dsheirer.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SDRTrunkStartupDecisionTest
{
    @Test
    public void calibrationDialogDoesNotBlockConfiguredAutoStartChannels()
    {
        assertFalse(SDRTrunk.shouldShowCalibrationDialogBeforeAutoStart(true, false, true));
    }

    @Test
    public void calibrationDialogShowsWhenCalibrationIsNeededAndNoChannelsAutoStart()
    {
        assertTrue(SDRTrunk.shouldShowCalibrationDialogBeforeAutoStart(true, false, false));
    }

    @Test
    public void calibrationDialogDoesNotShowWhenCalibrationIsNotNeeded()
    {
        assertFalse(SDRTrunk.shouldShowCalibrationDialogBeforeAutoStart(false, false, false));
    }

    @Test
    public void calibrationDialogDoesNotShowInHeadlessMode()
    {
        assertFalse(SDRTrunk.shouldShowCalibrationDialogBeforeAutoStart(true, true, false));
    }
}
