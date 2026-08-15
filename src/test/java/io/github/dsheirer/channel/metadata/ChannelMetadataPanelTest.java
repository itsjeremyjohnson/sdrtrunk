/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.channel.metadata;

import io.github.dsheirer.alias.Alias;
import io.github.dsheirer.alias.AliasModel;
import io.github.dsheirer.alias.id.priority.Priority;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChannelMetadataPanelTest
{
    @Test
    void idleChannelDoesNotExpandMuteTargetToSharedAliasList()
    {
        ChannelMetadata metadata = new ChannelMetadata(new AliasModel());

        assertNull(ChannelMetadataPanel.getChannelAliases(metadata));
    }

    @Test
    void temporaryMuteRestoresPriorPlaybackPriority()
    {
        Alias alias = new Alias("priority");
        alias.setCallPriority(25);
        Map<Alias,Integer> priorities = new HashMap<>();

        ChannelMetadataPanel.applyTemporaryMute(alias, true, priorities);
        assertEquals(Priority.DO_NOT_MONITOR, alias.getPlaybackPriority());

        ChannelMetadataPanel.applyTemporaryMute(alias, false, priorities);
        assertEquals(25, alias.getPlaybackPriority());
    }
}
