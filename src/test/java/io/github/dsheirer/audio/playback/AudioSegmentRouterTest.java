/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.audio.playback;

import io.github.dsheirer.alias.Alias;
import io.github.dsheirer.alias.AliasList;
import io.github.dsheirer.alias.id.radio.Radio;
import io.github.dsheirer.alias.id.talkgroup.Talkgroup;
import io.github.dsheirer.audio.AudioSegment;
import io.github.dsheirer.module.decode.p25.identifier.radio.APCO25RadioIdentifier;
import io.github.dsheirer.module.decode.p25.identifier.talkgroup.APCO25Talkgroup;
import io.github.dsheirer.protocol.Protocol;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class AudioSegmentRouterTest
{
    @Test
    void identifiesUsableRoutedOutputFormats()
    {
        AudioFormat stereo = AudioSegmentRouter.getSupportedOutputFormat(mixerSupportingChannels(2));
        assertEquals(2, stereo.getChannels());
        assertEquals(8000.0f, stereo.getSampleRate());
        assertEquals(16, stereo.getSampleSizeInBits());

        AudioFormat mono = AudioSegmentRouter.getSupportedOutputFormat(mixerSupportingChannels(1));
        assertEquals(1, mono.getChannels());
        assertNull(AudioSegmentRouter.getSupportedOutputFormat(mixerSupportingChannels(0)));
    }

    private Mixer mixerSupportingChannels(int channels)
    {
        return (Mixer)Proxy.newProxyInstance(Mixer.class.getClassLoader(), new Class[]{Mixer.class},
            (proxy, method, args) ->
            {
                if(method.getName().equals("isLineSupported"))
                {
                    DataLine.Info lineInfo = (DataLine.Info)args[0];
                    return lineInfo.getFormats()[0].getChannels() == channels;
                }

                return method.getReturnType() == boolean.class ? false : null;
            });
    }

    @Test
    void selectsAliasWithConfiguredOutputDevice()
    {
        AliasList aliasList = new AliasList("test");
        Alias talkgroupAlias = new Alias("unrouted talkgroup");
        talkgroupAlias.addAliasID(new Talkgroup(Protocol.APCO25, 100));
        aliasList.addAlias(talkgroupAlias);
        Alias radioAlias = new Alias("routed radio");
        radioAlias.addAliasID(new Radio(Protocol.APCO25, 200));
        radioAlias.setAudioOutputDevice("test-device");
        aliasList.addAlias(radioAlias);

        AudioSegment segment = new AudioSegment(aliasList, 0);
        segment.addIdentifier(APCO25Talkgroup.create(100));
        segment.addIdentifier(APCO25RadioIdentifier.createFrom(200));

        assertSame(radioAlias, new AudioSegmentRouter().getAlias(segment));
    }

    @Test
    void unavailableOutputFallsBackToNormalPlaybackAndReleasesSegment()
    {
        AliasList aliasList = new AliasList("test");
        Alias alias = new Alias("routed");
        alias.addAliasID(new Talkgroup(Protocol.APCO25, 100));
        alias.setAudioOutputDevice("missing-test-device");
        aliasList.addAlias(alias);

        AudioSegment segment = new AudioSegment(aliasList, 0);
        segment.addIdentifier(APCO25Talkgroup.create(100));
        segment.addAudio(new float[160]);
        segment.incrementConsumerCount();
        AudioSegmentRouter router = new AudioSegmentRouter()
        {
            @Override
            SourceDataLine getOrCreateOutputLine(String deviceName)
            {
                return null;
            }
        };

        router.route(segment);
        segment.decrementConsumerCount();

        assertEquals(0, segment.getAudioBufferCount());
        assertNotEquals(io.github.dsheirer.alias.id.priority.Priority.DO_NOT_MONITOR,
            segment.monitorPriorityProperty().get());
        router.dispose();
    }

    @Test
    void outputLossAbortsActiveRouteAndReleasesSegment()
    {
        AliasList aliasList = new AliasList("test");
        Alias alias = new Alias("routed");
        alias.addAliasID(new Talkgroup(Protocol.APCO25, 100));
        alias.setAudioOutputDevice("temporary-device");
        aliasList.addAlias(alias);
        AudioSegment segment = new AudioSegment(aliasList, 0);
        segment.addIdentifier(APCO25Talkgroup.create(100));
        segment.addAudio(new float[160]);
        segment.incrementConsumerCount();
        AtomicInteger lookups = new AtomicInteger();
        SourceDataLine line = (SourceDataLine)Proxy.newProxyInstance(SourceDataLine.class.getClassLoader(),
            new Class[]{SourceDataLine.class}, (proxy, method, args) -> null);
        AudioSegmentRouter router = new AudioSegmentRouter()
        {
            @Override
            SourceDataLine getOrCreateOutputLine(String deviceName)
            {
                return lookups.getAndIncrement() == 0 ? line : null;
            }
        };

        router.route(segment);
        segment.decrementConsumerCount();
        assertEquals(1, segment.getAudioBufferCount());

        router.processActiveSegments();

        assertEquals(0, segment.getAudioBufferCount());
        router.dispose();
    }
}
