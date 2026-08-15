/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.audio.playback;

import io.github.dsheirer.alias.Alias;
import io.github.dsheirer.alias.AliasList;
import io.github.dsheirer.alias.id.talkgroup.Talkgroup;
import io.github.dsheirer.audio.AudioSegment;
import io.github.dsheirer.module.decode.p25.identifier.talkgroup.APCO25Talkgroup;
import io.github.dsheirer.protocol.Protocol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AudioSegmentRouterTest
{
    @Test
    void holdsConsumerReferenceUntilRouterDisposal()
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
        AudioSegmentRouter router = new AudioSegmentRouter();

        router.route(segment);
        segment.decrementConsumerCount();

        assertEquals(1, segment.getAudioBufferCount());
        router.dispose();
        assertEquals(0, segment.getAudioBufferCount());
    }
}
