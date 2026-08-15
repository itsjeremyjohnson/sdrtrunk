/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.module.decode.dmr;

import io.github.dsheirer.alias.Alias;
import io.github.dsheirer.alias.AliasList;
import io.github.dsheirer.alias.id.talkgroup.Talkgroup;
import io.github.dsheirer.controller.channel.Channel;
import io.github.dsheirer.identifier.MutableIdentifierCollection;
import io.github.dsheirer.module.decode.dmr.identifier.DMRTalkgroup;
import io.github.dsheirer.protocol.Protocol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DMRTrafficChannelManagerTest
{
    @Test
    void appliesAliasFilteringIndependentlyToTrafficTimeslotCalls()
    {
        DecodeConfigDMR configuration = new DecodeConfigDMR();
        configuration.setIgnoreUnaliasedTalkgroups(true);
        configuration.setTrafficChannelPoolSize(0);
        Channel channel = new Channel("control");
        channel.setDecodeConfiguration(configuration);

        AliasList aliasList = new AliasList("test");
        Alias alias = new Alias("allowed");
        alias.addAliasID(new Talkgroup(Protocol.DMR, 100));
        aliasList.addAlias(alias);
        DMRTrafficChannelManager manager = new DMRTrafficChannelManager(channel, aliasList);

        MutableIdentifierCollection allowed = new MutableIdentifierCollection(1);
        allowed.update(DMRTalkgroup.create(100));
        MutableIdentifierCollection ignored = new MutableIdentifierCollection(2);
        ignored.update(DMRTalkgroup.create(200));

        assertTrue(manager.hasAlias(allowed));
        assertFalse(manager.hasAlias(ignored));
    }
}
