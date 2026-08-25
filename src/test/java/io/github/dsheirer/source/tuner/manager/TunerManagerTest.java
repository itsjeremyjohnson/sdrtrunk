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
package io.github.dsheirer.source.tuner.manager;

import io.github.dsheirer.source.config.SourceConfiguration;
import io.github.dsheirer.source.tuner.channel.ChannelSpecification;
import io.github.dsheirer.source.tuner.channel.TunerChannel;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunerManagerTest
{
    @Test
    void allocationEntryPointsShareManagerMonitor() throws Exception
    {
        assertSynchronized("getSource", SourceConfiguration.class, ChannelSpecification.class, String.class);
        assertSynchronized("getSource", TunerChannel.class, ChannelSpecification.class, String.class, String.class);
        assertSynchronized("getSourceWithHeadroom", SourceConfiguration.class, ChannelSpecification.class,
            String.class, int.class);
    }

    @Test
    void idleActivationPreservesConfiguredReserve()
    {
        assertFalse(TunerManager.canActivateIdleTuner(1, 1));
        assertTrue(TunerManager.canActivateIdleTuner(2, 1));
        assertTrue(TunerManager.canActivateIdleTuner(1, 0));
    }

    private static void assertSynchronized(String name, Class<?>... parameterTypes) throws Exception
    {
        assertTrue(Modifier.isSynchronized(TunerManager.class.getMethod(name, parameterTypes).getModifiers()),
            name + " must serialize tuner allocation through the TunerManager monitor");
    }
}
