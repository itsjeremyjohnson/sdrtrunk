/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.channel.metadata;

import io.github.dsheirer.alias.Alias;
import io.github.dsheirer.alias.AliasModel;
import io.github.dsheirer.alias.id.priority.Priority;
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
    void temporaryMuteDoesNotChangePersistedPriority()
    {
        Alias alias = new Alias("priority");
        alias.setCallPriority(25);

        ChannelMetadataPanel.applyTemporaryMute(alias, true);
        assertEquals(Priority.DO_NOT_MONITOR, alias.getPlaybackPriority());
        assertEquals(25, alias.getAliasIdentifiers().stream()
            .filter(Priority.class::isInstance)
            .map(Priority.class::cast)
            .findFirst()
            .orElseThrow()
            .getPriority());

        ChannelMetadataPanel.applyTemporaryMute(alias, false);
        assertEquals(25, alias.getPlaybackPriority());
    }
}
