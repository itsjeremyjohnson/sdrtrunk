/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.controller.channel;

import io.github.dsheirer.module.decode.event.DecodeEventHistory;
import io.github.dsheirer.module.decode.event.IDecodeEvent;
import io.github.dsheirer.sample.Listener;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChannelProcessingManagerTest
{
    @Test
    void childHistoryRegistrationRemovesListenerFromSource()
    {
        DecodeEventHistory source = new DecodeEventHistory(10);
        AtomicInteger received = new AtomicInteger();
        Listener<IDecodeEvent> listener = event -> received.incrementAndGet();
        source.addListener(listener);
        ChannelProcessingManager.ChildHistoryRegistration registration =
            new ChannelProcessingManager.ChildHistoryRegistration(source, listener);

        registration.remove();
        source.receive(null);

        assertEquals(0, received.get());
    }
}
