/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.module.decode.p25.phase1;

import io.github.dsheirer.controller.channel.Channel;
import io.github.dsheirer.controller.channel.Channel.ChannelType;
import io.github.dsheirer.identifier.Identifier;
import io.github.dsheirer.module.decode.p25.identifier.talkgroup.APCO25Talkgroup;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class P25P1DecoderStateTest
{
    @Test
    void conventionalTalkgroupOverrideDoesNotReplaceTrafficChannelTalkgroup() throws Exception
    {
        Channel conventional = channel(ChannelType.STANDARD, 999);
        Channel traffic = channel(ChannelType.TRAFFIC, 999);
        List<Identifier> conventionalIdentifiers = identifiers(100);
        List<Identifier> trafficIdentifiers = identifiers(100);

        applyOverride(new P25P1DecoderState(conventional), conventionalIdentifiers);
        applyOverride(new P25P1DecoderState(traffic), trafficIdentifiers);

        assertEquals(APCO25Talkgroup.create(999), conventionalIdentifiers.getFirst());
        assertEquals(APCO25Talkgroup.create(100), trafficIdentifiers.getFirst());
    }

    private static Channel channel(ChannelType type, int override)
    {
        Channel channel = new Channel("test", type);
        DecodeConfigP25Phase1 configuration = new DecodeConfigP25Phase1();
        configuration.setTalkgroup(override);
        configuration.setTrafficChannelPoolSize(0);
        channel.setDecodeConfiguration(configuration);
        return channel;
    }

    private static List<Identifier> identifiers(int talkgroup)
    {
        return new ArrayList<>(List.of(APCO25Talkgroup.create(talkgroup)));
    }

    private static void applyOverride(P25P1DecoderState state, List<Identifier> identifiers) throws Exception
    {
        Method method = P25P1DecoderState.class.getDeclaredMethod("applyTalkgroupOverride", List.class);
        method.setAccessible(true);
        method.invoke(state, identifiers);
    }
}
