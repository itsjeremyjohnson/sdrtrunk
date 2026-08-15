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

import io.github.dsheirer.module.discovery.Discovery;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscoveryEditorTest
{
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
