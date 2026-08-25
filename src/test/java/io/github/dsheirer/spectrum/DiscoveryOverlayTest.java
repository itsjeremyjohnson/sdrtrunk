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
package io.github.dsheirer.spectrum;

import com.google.common.eventbus.Subscribe;
import io.github.dsheirer.eventbus.MyEventBus;
import io.github.dsheirer.gui.playlist.channel.ShowDiscoveryRequest;
import io.github.dsheirer.module.discovery.Discovery;
import io.github.dsheirer.module.discovery.DiscoveryModel;
import io.github.dsheirer.module.discovery.DiscoveryState;
import io.github.dsheirer.preference.discovery.DiscoveryPreference;
import io.github.dsheirer.preference.discovery.OverlayDisplay;
import io.github.dsheirer.settings.SettingsManager;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoveryOverlayTest
{
    private Preferences mPreferenceNode;

    @AfterEach
    void tearDown() throws Exception
    {
        if(mPreferenceNode != null)
        {
            mPreferenceNode.removeNode();
        }
    }

    @Test
    void sessionAllOverridesIdentifiedOnlyPreference()
    {
        DiscoveryModel model = new DiscoveryModel();
        Discovery discovery = new Discovery(100_100_000L, 10_000, -70.0, 15.0, Instant.now());
        discovery.setState(DiscoveryState.UNIDENTIFIED);
        model.add(discovery);
        mPreferenceNode = Preferences.userRoot().node("sdrtrunk-discovery-overlay-" + UUID.randomUUID());
        DiscoveryPreference preference = new DiscoveryPreference(type -> {}, mPreferenceNode);
        OverlayPanel axis = new OverlayPanel(new SettingsManager(), null, null)
        {
            @Override public double getAxisFromFrequency(long value) { return (value - 100_000_000L) / 1_000.0; }
        };
        DiscoveryOverlay overlay = new DiscoveryOverlay(model, preference, axis);
        overlay.setSize(300, 100);

        try
        {
            assertFalse(overlay.contains(100, 50));
            overlay.setDiscoveryDisplay(DiscoveryOverlay.DiscoveryDisplay.ALL);
            assertTrue(overlay.contains(100, 50), "Session ALL must display unidentified discoveries");
        }
        finally
        {
            overlay.dispose();
            axis.dispose();
        }
    }

    @Test
    void preferenceChangesApplyLiveUntilSessionOverride() throws Exception
    {
        DiscoveryModel model = new DiscoveryModel();
        Discovery discovery = new Discovery(100_100_000L, 10_000, -70.0, 15.0, Instant.now());
        discovery.setState(DiscoveryState.UNIDENTIFIED);
        model.add(discovery);
        mPreferenceNode = Preferences.userRoot().node("sdrtrunk-discovery-overlay-" + UUID.randomUUID());
        DiscoveryPreference preference = new DiscoveryPreference(type -> {}, mPreferenceNode);
        OverlayPanel axis = new OverlayPanel(new SettingsManager(), null, null)
        {
            @Override public double getAxisFromFrequency(long value) { return (value - 100_000_000L) / 1_000.0; }
        };
        DiscoveryOverlay overlay = new DiscoveryOverlay(model, preference, axis);
        overlay.setSize(300, 100);

        try
        {
            assertFalse(overlay.contains(100, 50));
            preference.setOverlayDisplay(OverlayDisplay.ALL);
            SwingUtilities.invokeAndWait(() -> {});
            assertTrue(overlay.contains(100, 50), "Preference changes must update the installed overlay");

            overlay.setDiscoveryDisplay(DiscoveryOverlay.DiscoveryDisplay.NONE);
            preference.setOverlayDisplay(OverlayDisplay.IDENTIFIED_ONLY);
            SwingUtilities.invokeAndWait(() -> {});
            assertEquals(DiscoveryOverlay.DiscoveryDisplay.NONE, overlay.getDiscoveryDisplay());
            assertFalse(overlay.contains(100, 50), "A session override must remain authoritative");
        }
        finally
        {
            overlay.dispose();
            axis.dispose();
        }
    }

    @Test
    void markerClickPostsRequestForMatchingDiscovery()
    {
        long frequency = 100_100_000L;
        DiscoveryModel model = new DiscoveryModel();
        Discovery discovery = new Discovery(frequency, 10_000, -70.0, 15.0, Instant.now());
        discovery.setState(DiscoveryState.IDENTIFIED);
        model.add(discovery);

        mPreferenceNode = Preferences.userRoot().node("sdrtrunk-discovery-overlay-" + UUID.randomUUID());
        DiscoveryPreference preference = new DiscoveryPreference(type -> {}, mPreferenceNode);
        OverlayPanel axis = new OverlayPanel(new SettingsManager(), null, null)
        {
            @Override
            public double getAxisFromFrequency(long value)
            {
                return (value - 100_000_000L) / 1_000.0;
            }
        };
        DiscoveryOverlay overlay = new DiscoveryOverlay(model, preference, axis);
        overlay.setSize(300, 100);
        AtomicReference<ShowDiscoveryRequest> received = new AtomicReference<>();
        Object subscriber = new Object()
        {
            @Subscribe
            public void receive(ShowDiscoveryRequest request)
            {
                received.set(request);
            }
        };
        MyEventBus.getGlobalEventBus().register(subscriber);

        try
        {
            assertTrue(overlay.contains(100, 50));
            assertFalse(overlay.contains(250, 50));
            overlay.dispatchEvent(new MouseEvent(overlay, MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(), 0, 100, 50, 1, false, MouseEvent.BUTTON1));
            assertEquals(frequency, received.get().focusFrequencyHz());
        }
        finally
        {
            MyEventBus.getGlobalEventBus().unregister(subscriber);
            overlay.dispose();
            axis.dispose();
        }
    }
}
