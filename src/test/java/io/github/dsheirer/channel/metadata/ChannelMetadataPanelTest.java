/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.channel.metadata;

import io.github.dsheirer.alias.Alias;
import io.github.dsheirer.alias.AliasModel;
import io.github.dsheirer.alias.id.priority.Priority;
import io.github.dsheirer.audio.AbstractAudioModule;
import io.github.dsheirer.source.tuner.Tuner;
import io.github.dsheirer.source.tuner.TunerFrequencyErrorMonitor;
import io.github.dsheirer.source.tuner.channel.PassThroughChannelSource;
import io.github.dsheirer.source.tuner.channel.TunerChannel;
import io.github.dsheirer.source.tuner.test.TestTuner;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelMetadataPanelTest
{
    @Test
    void idleChannelDoesNotExpandMuteTargetToSharedAliasList()
    {
        ChannelMetadata metadata = new ChannelMetadata(new AliasModel());

        assertNull(ChannelMetadataPanel.getChannelAliases(metadata));
    }

    @Test
    void resolvesWaterfallTunerFromLiveChannelSource() throws Exception
    {
        TestTuner tuner = new TestTuner(null);

        try
        {
            PassThroughChannelSource source = new PassThroughChannelSource(null, tuner.getTunerController(),
                new TunerChannel(851_000_000, 12_500), "test");
            source.setTuner(tuner);

            assertSame(tuner, ChannelMetadataPanel.getLiveTuner(source));
        }
        finally
        {
            Field monitorField = Tuner.class.getDeclaredField("mTunerFrequencyErrorMonitor");
            monitorField.setAccessible(true);
            ((TunerFrequencyErrorMonitor)monitorField.get(tuner)).stop();
        }
    }

    @Test
    void synchronizesReusedAudioModuleMuteInBothDirections()
    {
        TestAudioModule module = new TestAudioModule();

        ChannelMetadataPanel.synchronizeMute(module, true);
        assertTrue(module.isMuted());

        ChannelMetadataPanel.synchronizeMute(module, false);
        assertFalse(module.isMuted());
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

    private static class TestAudioModule extends AbstractAudioModule
    {
        TestAudioModule()
        {
            super(null);
        }

        @Override
        public void reset()
        {
        }

        @Override
        public void start()
        {
        }
    }
}
