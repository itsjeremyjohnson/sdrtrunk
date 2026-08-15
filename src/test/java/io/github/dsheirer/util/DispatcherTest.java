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
package io.github.dsheirer.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatcherTest
{
    @Test
    void ordinaryStopAbandonsRemainingInFlightBatch() throws Exception
    {
        Dispatcher<Integer> dispatcher = new Dispatcher<>("dispatcher ordinary stop test", 10);
        CountDownLatch firstElementStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstElement = new CountDownLatch(1);
        CountDownLatch firstElementCompleted = new CountDownLatch(1);
        CountDownLatch secondElementDelivered = new CountDownLatch(1);
        List<Integer> received = new ArrayList<>();

        dispatcher.setListener(element -> {
            if(element == 1)
            {
                firstElementStarted.countDown();
                try
                {
                    releaseFirstElement.await(2, TimeUnit.SECONDS);
                }
                catch(InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            }
            else
            {
                secondElementDelivered.countDown();
            }

            synchronized(received)
            {
                received.add(element);
            }
            firstElementCompleted.countDown();
        });
        dispatcher.start();
        dispatcher.receive(1);
        dispatcher.receive(2);

        assertTrue(firstElementStarted.await(2, TimeUnit.SECONDS));
        dispatcher.stop();
        releaseFirstElement.countDown();

        assertTrue(firstElementCompleted.await(2, TimeUnit.SECONDS));
        assertEquals(false, secondElementDelivered.await(100, TimeUnit.MILLISECONDS));
        assertEquals(List.of(1), received);
    }

    @Test
    void flushAndStopWaitsForInFlightBatch() throws Exception
    {
        Dispatcher<Integer> dispatcher = new Dispatcher<>("dispatcher test", 10);
        CountDownLatch firstElementStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstElement = new CountDownLatch(1);
        List<Integer> received = new ArrayList<>();

        dispatcher.setListener(element -> {
            if(element == 1)
            {
                firstElementStarted.countDown();
                try
                {
                    releaseFirstElement.await(2, TimeUnit.SECONDS);
                }
                catch(InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            }

            synchronized(received)
            {
                received.add(element);
            }
        });
        dispatcher.start();
        dispatcher.receive(1);
        dispatcher.receive(2);

        assertTrue(firstElementStarted.await(2, TimeUnit.SECONDS));
        Thread stopThread = Thread.ofPlatform().start(dispatcher::flushAndStop);
        releaseFirstElement.countDown();
        stopThread.join(2000);

        assertEquals(List.of(1, 2), received);
    }
}
