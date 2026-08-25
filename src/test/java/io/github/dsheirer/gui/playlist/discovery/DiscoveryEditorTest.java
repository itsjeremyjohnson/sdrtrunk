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
package io.github.dsheirer.gui.playlist.discovery;

import io.github.dsheirer.controller.channel.Channel;
import io.github.dsheirer.module.discovery.Discovery;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscoveryEditorTest
{
    @Test
    void createdChannelObserverTracksTemporaryLiveState()
    {
        Discovery discovery = new Discovery(155_000_000L, 12_500, -80.0, 10.0, Instant.now());
        AtomicInteger invalidations = new AtomicInteger();
        DiscoveryEditor.CreatedChannelStateObserver observer =
            new DiscoveryEditor.CreatedChannelStateObserver(invalidations::incrementAndGet);
        observer.observe(discovery);
        Channel channel = new Channel("discovery");
        channel.setTemporaryLive(true);
        discovery.setCreatedChannel(channel);
        int beforeSave = invalidations.get();

        channel.setTemporaryLive(false);

        assertEquals(beforeSave + 1, invalidations.get(),
            "Saving the observed channel must invalidate the State cell");
    }

    @Test
    void powerSnrBindingObservesBothValues()
    {
        Discovery discovery = new Discovery(155_000_000L, 12_500, -80.0, 10.0, Instant.now());
        var binding = DiscoveryEditor.powerSnrBinding(discovery);
        assertEquals("-80.0 / 10.0 dB", binding.get());

        discovery.snrDbProperty().set(18.5);

        assertEquals("-80.0 / 18.5 dB", binding.get());
    }
}
