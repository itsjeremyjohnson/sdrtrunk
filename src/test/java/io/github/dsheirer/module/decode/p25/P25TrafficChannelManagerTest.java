/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.module.decode.p25;

import io.github.dsheirer.controller.channel.Channel;
import io.github.dsheirer.module.decode.p25.phase1.message.IFrequencyBand;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class P25TrafficChannelManagerTest
{
    @Test
    void preservesFrequencyBandsAcrossParentControlFrequencyRotation()
    {
        Channel parent = new Channel("control");
        P25TrafficChannelManager manager = new P25TrafficChannelManager(parent);
        IFrequencyBand band = (IFrequencyBand)Proxy.newProxyInstance(IFrequencyBand.class.getClassLoader(),
            new Class[]{IFrequencyBand.class}, (proxy, method, args) -> method.getName().equals("getIdentifier") ? 7 : 0);
        manager.processFrequencyBand(band);

        manager.processControlFrequencyUpdate(851_000_000L, 852_000_000L, parent);

        assertTrue(manager.hasFrequencyBand(7));
    }
}
