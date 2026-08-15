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
    void resetAllowsFreshEnumerationOrder()
    {
        long[] serials = {SERIAL_1, SERIAL_2};
        assertEquals(SERIAL_1, HydraSdrTunerController.assignSerialForUsbPort(1, "2", serials));

        HydraSdrTunerController.resetDeviceAssignment();

        assertEquals(SERIAL_1, HydraSdrTunerController.assignSerialForUsbPort(2, "5", serials));
    }
}
