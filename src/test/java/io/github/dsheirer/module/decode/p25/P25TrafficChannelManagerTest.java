/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.module.decode.p25;

import io.github.dsheirer.controller.channel.Channel;
import io.github.dsheirer.module.decode.p25.phase1.DecodeConfigP25Phase1;
import io.github.dsheirer.module.decode.p25.phase1.message.IFrequencyBand;
import io.github.dsheirer.module.decode.p25.phase2.DecodeConfigP25Phase2;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class P25TrafficChannelManagerTest
{
    @Test
    void phaseOneParentHangtimePropagatesToPhaseTwoTrafficChannels() throws Exception
    {
        Channel parent = new Channel("control");
        DecodeConfigP25Phase1 configuration = new DecodeConfigP25Phase1();
        configuration.setTrafficChannelPoolSize(1);
        configuration.setAudioHangtimeMs(275);
        parent.setDecodeConfiguration(configuration);
        P25TrafficChannelManager manager = new P25TrafficChannelManager(parent);
        Field field = P25TrafficChannelManager.class.getDeclaredField("mManagedPhase2TrafficChannels");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Channel> trafficChannels = (List<Channel>)field.get(manager);

        DecodeConfigP25Phase2 trafficConfiguration =
            (DecodeConfigP25Phase2)trafficChannels.getFirst().getDecodeConfiguration();

        assertEquals(275, trafficConfiguration.getAudioHangtimeMs());
    }

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
