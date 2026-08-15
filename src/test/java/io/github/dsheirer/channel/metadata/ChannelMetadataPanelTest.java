/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.channel.metadata;

import io.github.dsheirer.alias.AliasModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class ChannelMetadataPanelTest
{
    @Test
    void idleChannelDoesNotExpandMuteTargetToSharedAliasList()
    {
        ChannelMetadata metadata = new ChannelMetadata(new AliasModel());

        assertNull(ChannelMetadataPanel.getChannelAliases(metadata));
    }
}
