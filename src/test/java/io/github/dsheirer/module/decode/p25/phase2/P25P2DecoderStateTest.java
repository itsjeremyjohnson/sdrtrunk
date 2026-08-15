/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.module.decode.p25.phase2;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dsheirer.alias.Alias;
import io.github.dsheirer.alias.AliasList;
import io.github.dsheirer.alias.id.talkgroup.Talkgroup;
import io.github.dsheirer.controller.channel.Channel;
import io.github.dsheirer.identifier.patch.PatchGroupManager;
import io.github.dsheirer.module.decode.p25.P25TrafficChannelManager;
import io.github.dsheirer.module.decode.p25.identifier.talkgroup.APCO25Talkgroup;
import io.github.dsheirer.module.decode.p25.phase2.message.P25P2Message;
import io.github.dsheirer.protocol.Protocol;
import org.junit.jupiter.api.Test;

class P25P2DecoderStateTest
{
    @Test
    void appliesAliasFilteringIndependentlyPerTimeslot()
    {
        DecodeConfigP25Phase2 configuration = new DecodeConfigP25Phase2();
        configuration.setIgnoreUnaliasedTalkgroups(true);
        configuration.setTrafficChannelPoolSize(0);
        Channel channel = new Channel("control");
        channel.setDecodeConfiguration(configuration);

        AliasList aliasList = new AliasList("test");
        Alias alias = new Alias("allowed");
        alias.addAliasID(new Talkgroup(Protocol.APCO25, 100));
        aliasList.addAlias(alias);
        P25TrafficChannelManager manager = new P25TrafficChannelManager(channel, aliasList);
        PatchGroupManager patchGroupManager = new PatchGroupManager();
        P25P2DecoderState allowed = new P25P2DecoderState(channel, P25P2Message.TIMESLOT_1, manager,
            patchGroupManager);
        P25P2DecoderState ignored = new P25P2DecoderState(channel, P25P2Message.TIMESLOT_2, manager,
            patchGroupManager);
        allowed.getIdentifierCollection().update(APCO25Talkgroup.create(100));
        ignored.getIdentifierCollection().update(APCO25Talkgroup.create(200));

        assertTrue(allowed.isCurrentCallAllowed());
        assertFalse(ignored.isCurrentCallAllowed());
    }
}
