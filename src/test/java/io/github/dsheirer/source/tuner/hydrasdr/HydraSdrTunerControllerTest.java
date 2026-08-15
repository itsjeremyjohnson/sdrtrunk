/*
 * *****************************************************************************
 * Copyright (C) 2026 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * *****************************************************************************
 */
package io.github.dsheirer.source.tuner.hydrasdr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HydraSdrTunerControllerTest
{
    private static final long SERIAL_1 = 0x1000000000000001L;
    private static final long SERIAL_2 = 0x1000000000000002L;
    private static final long SERIAL_3 = 0x1000000000000003L;

    @BeforeEach
    void resetAssignments()
    {
        HydraSdrTunerController.resetDeviceAssignment();
    }

    @Test
    void retainsAssignmentForRepeatedUsbLocation()
    {
        long[] serials = {SERIAL_1, SERIAL_2};

        assertEquals(SERIAL_1, HydraSdrTunerController.assignSerialForUsbPort(1, "2", serials));
        assertEquals(SERIAL_1, HydraSdrTunerController.assignSerialForUsbPort(1, "2", serials));
        assertEquals(SERIAL_2, HydraSdrTunerController.assignSerialForUsbPort(1, "3", serials));
        assertEquals(0, HydraSdrTunerController.assignSerialForUsbPort(1, "4", serials));
    }

    @Test
    void replacesAssignmentWhenPreviouslyAssignedSerialIsRemoved()
    {
        assertEquals(SERIAL_1,
            HydraSdrTunerController.assignSerialForUsbPort(1, "2", new long[] {SERIAL_1, SERIAL_2}));
        assertEquals(SERIAL_2,
            HydraSdrTunerController.assignSerialForUsbPort(1, "3", new long[] {SERIAL_1, SERIAL_2}));

        long[] currentSerials = {SERIAL_2, SERIAL_3};
        assertEquals(SERIAL_3, HydraSdrTunerController.assignSerialForUsbPort(1, "2", currentSerials));
        assertEquals(SERIAL_2, HydraSdrTunerController.assignSerialForUsbPort(1, "3", currentSerials));
    }

    @Test
    void unpluggedDeviceCanBeAssignedAtNewUsbLocation()
    {
        long[] serials = {SERIAL_1, SERIAL_2};
        assertEquals(SERIAL_1, HydraSdrTunerController.assignSerialForUsbPort(1, "2", serials));
        assertEquals(SERIAL_2, HydraSdrTunerController.assignSerialForUsbPort(1, "3", serials));

        HydraSdrTunerController.removeDeviceAssignment(1, "2");

        assertEquals(SERIAL_1, HydraSdrTunerController.assignSerialForUsbPort(2, "5", serials));
    }

    @Test
    void resetAllowsFreshEnumerationOrder()
    {
        long[] serials = {SERIAL_1, SERIAL_2};
        assertEquals(SERIAL_1, HydraSdrTunerController.assignSerialForUsbPort(1, "2", serials));

        HydraSdrTunerController.resetDeviceAssignment();

        assertEquals(SERIAL_1, HydraSdrTunerController.assignSerialForUsbPort(2, "5", serials));
    }

    @Test
    void selectsAdvertisedPresetWhenRequestedModeIsUnavailable()
    {
        assertEquals(HydraSdrTunerController.GAIN_MODE_SENSITIVITY,
            HydraSdrTunerController.selectSupportedGainMode(HydraSdrTunerController.GAIN_MODE_LINEARITY,
                false, true, false));
        assertEquals(HydraSdrTunerController.GAIN_MODE_CUSTOM,
            HydraSdrTunerController.selectSupportedGainMode(HydraSdrTunerController.GAIN_MODE_LINEARITY,
                false, false, true));
        assertEquals(-1, HydraSdrTunerController.selectSupportedGainMode(
            HydraSdrTunerController.GAIN_MODE_CUSTOM, false, false, false));
    }

    @Test
    void normalizesRestoredGainToAdvertisedRangeAndStep()
    {
        int[] gainInfo = {0, 0, 20, 4, 8, 0};

        assertEquals(0, HydraSdrTunerController.normalizeGainValue(-5, gainInfo));
        assertEquals(8, HydraSdrTunerController.normalizeGainValue(7, gainInfo));
        assertEquals(20, HydraSdrTunerController.normalizeGainValue(25, gainInfo));
    }

    @Test
    void usesAdvertisedPresetDefaultBeforeNormalization()
    {
        int[] gainInfo = {0, 1, 21, 4, 9, 0};

        assertEquals(9, HydraSdrTunerController.normalizePresetGain(0, 14, gainInfo));
        assertEquals(13, HydraSdrTunerController.normalizePresetGain(12, 14, gainInfo));
        assertEquals(14, HydraSdrTunerController.normalizePresetGain(0, 14, null));
    }

    @Test
    void identifiesOnlySupportedAgcsForPresetRestoration()
    {
        HydraSdrDeviceInfo deviceInfo = new HydraSdrDeviceInfo();
        assertArrayEquals(new int[0], HydraSdrTunerController.getSupportedPresetAgcTypes(deviceInfo));

        deviceInfo.setCapabilities(HydraSdrNative.CAP_LNA_AGC | HydraSdrNative.CAP_MIXER_AGC);
        assertArrayEquals(new int[] {HydraSdrNative.GAIN_TYPE_LNA_AGC, HydraSdrNative.GAIN_TYPE_MIXER_AGC},
            HydraSdrTunerController.getSupportedPresetAgcTypes(deviceInfo));
    }

    @Test
    void savingActivePresetPreservesInactivePresetGain()
    {
        HydraSdrTunerConfiguration configuration = new HydraSdrTunerConfiguration();
        configuration.setLinearityGain(15);
        configuration.setSensitivityGain(11);

        HydraSdrTunerEditor.savePresetGain(configuration, HydraSdrTunerController.GAIN_MODE_LINEARITY, 19);
        assertEquals(19, configuration.getLinearityGain());
        assertEquals(11, configuration.getSensitivityGain());

        HydraSdrTunerEditor.savePresetGain(configuration, HydraSdrTunerController.GAIN_MODE_SENSITIVITY, 13);
        assertEquals(19, configuration.getLinearityGain());
        assertEquals(13, configuration.getSensitivityGain());
    }
}
