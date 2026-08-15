/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * ****************************************************************************
 */
package io.github.dsheirer.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;

/**
 * Small utility for safely running a {@link Runnable} on the JavaFX Application Thread
 * from any calling thread, with a headless / unit-test fallback.
 *
 * <h3>Behaviour</h3>
 * <ul>
 *   <li>If the caller is already on the FX thread — run inline immediately.</li>
 *   <li>If the FX toolkit is running but we are on a background thread — delegate to
 *       {@link Platform#runLater(Runnable)} so the action executes on the FX thread.</li>
 *   <li>If the FX toolkit is not running (headless / unit tests) —
 *       {@code Platform.runLater} throws {@code IllegalStateException: Toolkit not initialized}.
 *       We catch that exception and execute the action inline on the calling thread.
 *       This keeps all headless tests green without any special mock setup.</li>
 * </ul>
 *
 * <p>This class has no instance state and provides asynchronous and synchronous helpers.</p>
 */
public final class FxThreads
{
    private static final long RUN_AND_WAIT_TIMEOUT_SECONDS = 5L;

    /** Utility class — do not instantiate. */
    private FxThreads() {}

    /**
     * Runs {@code action} on the JavaFX Application Thread, or inline if the toolkit is
     * not running (headless / test path).
     *
     * @param action the action to run; must not be null
     */
    public static void run(Runnable action)
    {
        if(Platform.isFxApplicationThread())
        {
            action.run();
            return;
        }

        try
        {
            Platform.runLater(action);
        }
        catch(IllegalStateException e)
        {
            // FX toolkit not started — run inline (headless / test path)
            action.run();
        }
    }

    /**
     * Runs {@code action} synchronously on the JavaFX Application Thread.  This is intended
     * for ordered model mutations where the caller must not continue until the FX mutation
     * has completed.  In headless tests where the toolkit is not initialized, it runs inline.
     *
     * @param action the action to run; must not be null
     */
    public static void runAndWait(Runnable action)
    {
        if(Platform.isFxApplicationThread())
        {
            action.run();
            return;
        }

        CountDownLatch completed = new CountDownLatch(1);
        AtomicBoolean queued = new AtomicBoolean(true);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        try
        {
            Platform.runLater(() ->
            {
                if(!queued.compareAndSet(true, false))
                {
                    completed.countDown();
                    return;
                }

                try
                {
                    action.run();
                }
                catch(Throwable t)
                {
                    failure.set(t);
                }
                finally
                {
                    completed.countDown();
                }
            });
        }
        catch(IllegalStateException e)
        {
            action.run();
            return;
        }

        try
        {
            if(!completed.await(RUN_AND_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            {
                if(queued.compareAndSet(true, false))
                {
                    throw new IllegalStateException("JavaFX action was not accepted before shutdown");
                }

                // The action started concurrently with the timeout; preserve synchronous ordering.
                completed.await();
            }
        }
        catch(InterruptedException e)
        {
            queued.compareAndSet(true, false);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for JavaFX action", e);
        }

        Throwable thrown = failure.get();

        if(thrown instanceof RuntimeException runtimeException)
        {
            throw runtimeException;
        }
        else if(thrown instanceof Error error)
        {
            throw error;
        }
        else if(thrown != null)
        {
            throw new IllegalStateException("JavaFX action failed", thrown);
        }
    }
}
