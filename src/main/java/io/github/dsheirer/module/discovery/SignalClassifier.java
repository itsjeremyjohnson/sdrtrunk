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
package io.github.dsheirer.module.discovery;

import io.github.dsheirer.dsp.squelch.PowerMonitor;
import io.github.dsheirer.dsp.window.WindowFactory;
import io.github.dsheirer.dsp.window.WindowType;
import io.github.dsheirer.module.ProcessingChain;
import io.github.dsheirer.module.decode.DecoderFactory;
import io.github.dsheirer.module.decode.DecoderType;
import io.github.dsheirer.module.decode.analog.DecodeConfigAnalog;
import io.github.dsheirer.module.decode.analog.DecodeConfigAnalog.Bandwidth;
import io.github.dsheirer.module.decode.config.DecodeConfiguration;
import io.github.dsheirer.preference.discovery.DiscoveryPreference;
import io.github.dsheirer.source.ComplexSource;
import io.github.dsheirer.source.SourceEvent;
import io.github.dsheirer.source.SourceException;
import io.github.dsheirer.source.config.SourceConfigTuner;
import io.github.dsheirer.source.tuner.channel.ChannelSpecification;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.jtransforms.fft.FloatFFT_1D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Engine that classifies (auto-detects the protocol of) a signal at a given frequency.
 *
 * <h3>Algorithm overview</h3>
 * <ol>
 *   <li>Acquire a {@link ComplexSource} from the injected {@link SourceProvider}.
 *       If the provider returns {@code null} (no tuner capacity) → {@link ClassificationOutcome#ERROR}.</li>
 *   <li>Start a {@link ComplexSampleFanout} over the real source and subscribe a
 *       {@link PowerMonitor} and short FFT accumulator to it. Wait up to the <em>energy gate window</em>
 *       (~1 s), then compare the strongest averaged spectral bin with the adaptive median-bin noise floor.
 *       If it does not exceed the configured threshold, return {@link ClassificationOutcome#NO_SIGNAL};
 *       otherwise continue probing and retain the peak channel power for result reporting.</li>
 *   <li>Subscribe up to N probe chains concurrently (N = {@link DiscoveryPreference#getMaxConcurrentProbes()},
 *       clamped ≥ 1), in {@link CandidateOrdering} priority order.  As soon as any chain
 *       reaches {@link LockState#LOCKED} the remaining lower-priority candidates are skipped.
 *       Each candidate's per-protocol probe window ({@link DiscoveryPreference#probeWindow(DecoderType)})
 *       acts as a per-candidate deadline; the request's {@code overallDeadline} is the global cap.</li>
 *   <li>Pick the best result (highest quality among any LOCKED candidates, then PARTIAL,
 *       then NONE).  Build the final {@link ClassificationResult}.</li>
 *   <li>Always close the {@link ClassificationSession} (stops fanout + all probe chains).</li>
 * </ol>
 *
 * <h3>Cancellation</h3>
 * <p>{@link #classify(ClassificationRequest)} submits the work via
 * {@link ExecutorService#submit(java.util.concurrent.Callable)} and returns a wrapper
 * {@link CompletableFuture} whose {@code cancel()} propagates an interrupt to the worker
 * thread and sets an internal cancelled flag.  The energy-gate wait loop and probe-wait
 * loop both poll the flag and respond within ~100 ms, producing a
 * {@link ClassificationOutcome#CANCELLED} result with full cleanup.</p>
 *
 * <h3>Testability seams</h3>
 * <ul>
 *   <li>{@link SourceProvider} — injected; default binding wraps {@code TunerManager.getSource}</li>
 *   <li>{@link ProbeChainFactory} — injected; tests supply scripted fakes</li>
 * </ul>
 */
public class SignalClassifier implements Classifier
{
    private static final Logger mLog = LoggerFactory.getLogger(SignalClassifier.class);

    /**
     * How long the energy gate collects power readings before making its decision.
     * Long enough to receive several PowerMonitor notifications (~500 ms each at 25 kHz).
     */
    private static final Duration ENERGY_GATE_WINDOW = Duration.ofMillis(1000);

    /**
     * Poll interval used when waiting for the LockWatcher to settle.
     */
    private static final long POLL_INTERVAL_MS = 25;

    private final SourceProvider mSourceProvider;
    private final ProbeChainFactory mProbeChainFactory;
    private final DiscoveryPreference mDiscoveryPreference;
    private final ExecutorService mExecutor;
    private final Object mClassificationGate = new Object();
    private int mActiveClassifications;

    /**
     * Constructs a {@code SignalClassifier}.
     *
     * @param sourceProvider     seam for acquiring a tuner channel source
     * @param probeChainFactory  factory that builds decoder-only probe chains
     * @param discoveryPreference user preferences (thresholds, windows, etc.)
     * @param executor           thread pool on which classify() runs its work
     */
    public SignalClassifier(SourceProvider sourceProvider,
                            ProbeChainFactory probeChainFactory,
                            DiscoveryPreference discoveryPreference,
                            ExecutorService executor)
    {
        mSourceProvider = sourceProvider;
        mProbeChainFactory = probeChainFactory;
        mDiscoveryPreference = discoveryPreference;
        mExecutor = executor;
    }

    /**
     * Classifies the signal at the frequency specified by the request.
     *
     * <p>The returned future never completes exceptionally; all errors are
     * encoded as {@link ClassificationOutcome#ERROR} results.  Calling
     * {@code future.cancel(mayInterruptIfRunning)} (either value) interrupts the
     * worker thread and initiates full cleanup; the result will be
     * {@link ClassificationOutcome#CANCELLED}.</p>
     *
     * @param request classification parameters
     * @return a future that resolves to the classification result; cancellable
     */
    public CompletableFuture<ClassificationResult> classify(ClassificationRequest request)
    {
        AtomicBoolean cancelledFlag = new AtomicBoolean(false);

        // Wrapper that overrides cancel() to interrupt the worker thread.
        // Using AtomicReference so the inner class can assign workerFuture after construction.
        AtomicReference<Future<?>> workerFutureRef = new AtomicReference<>();

        CompletableFuture<ClassificationResult> wrapper = new CompletableFuture<ClassificationResult>()
        {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning)
            {
                cancelledFlag.set(true);
                // Mark as cancelled BEFORE interrupting the worker so that when the
                // worker thread calls wrapper.complete(result) after being interrupted,
                // the future is already in the cancelled state and complete() is a no-op.
                boolean cancelled = super.cancel(mayInterruptIfRunning);
                Future<?> wf = workerFutureRef.get();
                if(wf != null)
                {
                    wf.cancel(true); // interrupt the worker thread
                }
                return cancelled;
            }
        };

        // Submit work as a real Future so we can interrupt it on cancel.
        Future<?> workerFuture = mExecutor.submit(() -> {
            try
            {
                ClassificationResult result = doClassify(request, cancelledFlag);
                wrapper.complete(result); // no-op if wrapper was already cancelled
            }
            catch(Throwable t)
            {
                // Belt-and-suspenders: doClassify wraps internally, but guard here too
                mLog.error("Unexpected error in SignalClassifier worker", t);
                wrapper.complete(ClassificationResult.error(request.centerFrequencyHz(), t.getMessage()));
            }
        });

        workerFutureRef.set(workerFuture);

        // If wrapper was cancelled before we set the reference, interrupt now.
        if(wrapper.isCancelled())
        {
            workerFuture.cancel(true);
        }

        return wrapper;
    }

    // -------------------------------------------------------------------------
    // Core probe logic (runs on executor thread)
    // -------------------------------------------------------------------------

    private ClassificationResult doClassify(ClassificationRequest request, AtomicBoolean cancelledFlag)
    {
        long freqHz = request.centerFrequencyHz();
        Instant overallDeadline = Instant.now().plus(request.overallDeadline());
        boolean admitted = false;

        try
        {
            admitted = acquireClassificationSlot(cancelledFlag, overallDeadline);

            if(!admitted)
            {
                return ClassificationResult.cancelled(freqHz);
            }

            return doClassifyInternal(request, freqHz, cancelledFlag, overallDeadline);
        }
        catch(Throwable t)
        {
            mLog.error("Classification failed unexpectedly at {} Hz", freqHz, t);
            return ClassificationResult.error(freqHz, t.getMessage());
        }
        finally
        {
            if(admitted)
            {
                releaseClassificationSlot();
            }
        }
    }

    /**
     * Waits for admission under the live classification-concurrency preference.
     */
    private boolean acquireClassificationSlot(AtomicBoolean cancelledFlag, Instant overallDeadline)
    {
        synchronized(mClassificationGate)
        {
            while(mActiveClassifications >= Math.max(1, mDiscoveryPreference.getMaxConcurrentClassifications()))
            {
                if(cancelledFlag.get() || Thread.currentThread().isInterrupted()
                    || !Instant.now().isBefore(overallDeadline))
                {
                    return false;
                }

                try
                {
                    long remainingMillis = Math.max(1L, Duration.between(Instant.now(), overallDeadline).toMillis());
                    mClassificationGate.wait(Math.min(POLL_INTERVAL_MS, remainingMillis));
                }
                catch(InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }

            if(cancelledFlag.get() || Thread.currentThread().isInterrupted()
                || !Instant.now().isBefore(overallDeadline))
            {
                return false;
            }

            mActiveClassifications++;
            return true;
        }
    }

    private void releaseClassificationSlot()
    {
        synchronized(mClassificationGate)
        {
            mActiveClassifications--;
            mClassificationGate.notifyAll();
        }
    }

    /**
     * Snapshot of LockWatcher data captured before the probe chain is torn down.
     * Needed because the watcher is not accessible after {@code dispose()}.
     */
    private record WatcherSnapshot(SignalKind kind, String summary, Map<String, String> metadata) {}

    /**
     * Builds one source allocation that is wide enough for every requested decoder and the
     * operator-observed bandwidth.  Probe chains still apply their decoder-specific filtering.
     */
    static ChannelSpecification buildSharedChannelSpecification(ClassificationRequest request)
    {
        double minimumSampleRate = 0.0;
        int bandwidth = 0;
        double passFrequency = 0.0;
        double stopFrequency = 0.0;

        for(DecoderType decoderType : request.candidateDecoders())
        {
            DecodeConfiguration decodeConfiguration = DecoderFactory.getDecodeConfiguration(decoderType);
            ChannelSpecification specification = decodeConfiguration.getChannelSpecification();
            minimumSampleRate = Math.max(minimumSampleRate, specification.getMinimumSampleRate());
            bandwidth = Math.max(bandwidth, specification.getBandwidth());
            passFrequency = Math.max(passFrequency, specification.getPassFrequency());
            stopFrequency = Math.max(stopFrequency, specification.getStopFrequency());
        }

        if(request.approximateBandwidthHz() > bandwidth)
        {
            bandwidth = request.approximateBandwidthHz();
            passFrequency = Math.max(passFrequency, bandwidth / 2.0);
            stopFrequency = Math.max(stopFrequency, passFrequency + 1_000.0);
        }

        minimumSampleRate = Math.max(minimumSampleRate, stopFrequency * 2.0);
        return new ChannelSpecification(minimumSampleRate, bandwidth, passFrequency, stopFrequency);
    }

    static DecodeConfiguration buildResultConfiguration(DecoderType decoderType, int approximateBandwidthHz)
    {
        DecodeConfiguration configuration = DecoderFactory.getDecodeConfiguration(decoderType);

        if(configuration instanceof DecodeConfigAnalog analogConfiguration && approximateBandwidthHz > 0)
        {
            var supportedBandwidths = decoderType == DecoderType.NBFM
                ? Bandwidth.FM_BANDWIDTHS
                : Bandwidth.AM_BANDWIDTHS;
            Bandwidth selected = supportedBandwidths.stream()
                .filter(bandwidth -> bandwidth.getValue() >= approximateBandwidthHz)
                .findFirst()
                .orElseGet(() -> supportedBandwidths.stream()
                    .max(java.util.Comparator.comparingDouble(Bandwidth::getValue))
                    .orElse(analogConfiguration.getBandwidth()));
            analogConfiguration.setBandwidth(selected);
        }

        return configuration;
    }

    private ClassificationResult doClassifyInternal(ClassificationRequest request,
                                                     long freqHz,
                                                     AtomicBoolean cancelledFlag,
                                                     Instant overallDeadline)
    {
        if(request.candidateDecoders().isEmpty())
        {
            return ClassificationResult.unidentified(freqHz, List.of(), Double.NaN);
        }

        // --- Step 1: Acquire source ------------------------------------------
        SourceConfigTuner sourceConfig = new SourceConfigTuner();
        sourceConfig.setFrequency(freqHz);

        ChannelSpecification channelSpec = buildSharedChannelSpecification(request);

        ComplexSource realSource;
        int headroomChannels = mDiscoveryPreference.getTunerHeadroomChannels();

        try
        {
            realSource = mSourceProvider.acquireWithHeadroom(sourceConfig, channelSpec,
                "discovery-" + freqHz, headroomChannels);
        }
        catch(SourceException e)
        {
            mLog.warn("SignalClassifier: error acquiring source for {} Hz: {}", freqHz, e.getMessage());
            return ClassificationResult.error(freqHz, "source error: " + e.getMessage());
        }

        if(realSource == null)
        {
            mLog.info("SignalClassifier: no tuner capacity available for {} Hz", freqHz);
            return ClassificationResult.error(freqHz, "no tuner capacity available");
        }

        // --- Step 2: Fan-out + energy gate ------------------------------------
        ComplexSampleFanout fanout = new ComplexSampleFanout(realSource);

        try(ClassificationSession session = new ClassificationSession(realSource, fanout))
        {
            // Set up the energy gate BEFORE starting the source so the subscriber
            // is registered when the first samples arrive.
            double signalPowerDb = runEnergyGateWithStart(fanout, realSource, cancelledFlag, overallDeadline);

            if(cancelledFlag.get() || Thread.currentThread().isInterrupted())
            {
                return ClassificationResult.cancelled(freqHz);
            }

            if(Double.isNaN(signalPowerDb))
            {
                return ClassificationResult.noSignal(freqHz, Double.NaN);
            }

            // --- Step 3: Probe loop with bounded concurrency -----------------
            List<DecoderType> ordered = CandidateOrdering.order(request.candidateDecoders(),
                request.approximateBandwidthHz());

            int maxConcurrent = Math.max(1, mDiscoveryPreference.getMaxConcurrentProbes());

            List<Candidate> candidates = new ArrayList<>();
            Map<DecoderType, WatcherSnapshot> snapshots = new HashMap<>();
            Map<DecoderType, DecodeConfiguration> winningConfigurations = new HashMap<>();
            Map<DecoderType, Double> winningQualities = new HashMap<>();

            // Active slots: at most maxConcurrent chains running simultaneously.  P25 Phase 1
            // contributes separate C4FM and CQPSK variants at the same decoder priority.
            List<ProbeChain> activeChains = new ArrayList<>(maxConcurrent);
            List<Instant> activeDeadlines = new ArrayList<>(maxConcurrent);
            Deque<PendingProbe> pendingVariants = new ArrayDeque<>();
            int[] nextIndex = {0};
            int lockedPriority = Integer.MAX_VALUE;

            launchAvailableProbes(ordered, nextIndex, lockedPriority, pendingVariants, activeChains,
                activeDeadlines, maxConcurrent, fanout, session, freqHz, candidates);

            // Poll active chains; as one completes, record it and optionally start the next.
            while(!activeChains.isEmpty())
            {
                if(cancelledFlag.get() || Thread.currentThread().isInterrupted()
                    || Instant.now().isAfter(overallDeadline))
                {
                    break;
                }

                boolean anyCompleted = false;

                for(int i = 0; i < activeChains.size(); i++)
                {
                    ProbeChain pc = activeChains.get(i);
                    Instant probeDeadline = activeDeadlines.get(i);
                    LockState state = pc.lockWatcher().getLockState();
                    boolean timedOut = Instant.now().isAfter(probeDeadline) || Instant.now().isAfter(overallDeadline);

                    if(state == LockState.LOCKED || state == LockState.ERROR || timedOut)
                    {
                        LockState finalState = (timedOut && state != LockState.LOCKED && state != LockState.ERROR)
                            ? pc.lockWatcher().getLockState() : state;
                        recordProbeResult(pc, finalState, candidates, snapshots, winningConfigurations,
                            winningQualities);
                        tearDownChain(pc, session, fanout);
                        activeChains.remove(i);
                        activeDeadlines.remove(i);
                        anyCompleted = true;
                        i--;

                        if(finalState == LockState.LOCKED)
                        {
                            lockedPriority = Math.min(lockedPriority, ordered.indexOf(pc.decoderType()));
                            stopLowerPriorityProbes(ordered, lockedPriority, activeChains, activeDeadlines,
                                candidates, session, fanout);
                        }

                        launchAvailableProbes(ordered, nextIndex, lockedPriority, pendingVariants, activeChains,
                            activeDeadlines, maxConcurrent, fanout, session, freqHz, candidates);
                    }
                }

                if(!anyCompleted && !activeChains.isEmpty())
                {
                    try
                    {
                        Thread.sleep(POLL_INTERVAL_MS);
                    }
                    catch(InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                        cancelledFlag.set(true);
                        break;
                    }
                }
            }

            disposePendingProbes(pendingVariants);

            // Tear down any remaining active chains (deadline or cancel)
            for(ProbeChain pc : activeChains)
            {
                recordProbeResult(pc, pc.lockWatcher().getLockState(), candidates, snapshots,
                    winningConfigurations, winningQualities);
                tearDownChain(pc, session, fanout);
            }

            // Check cancellation after cleanup
            if(cancelledFlag.get() || Thread.currentThread().isInterrupted())
            {
                return ClassificationResult.cancelled(freqHz);
            }

            // --- Step 4: Build result -----------------------------------------
            // Find the best LOCKED candidate in CandidateOrdering priority order.
            // When multiple chains lock concurrently, the one with the lowest index in
            // 'ordered' wins (spec §5.3 step 4: highest-priority-by-bandwidth that locked).
            DecoderType winnerType = null;
            int winnerPriority = Integer.MAX_VALUE;

            for(DecoderType decoderType : winningConfigurations.keySet())
            {
                int priorityIdx = ordered.indexOf(decoderType);
                if(priorityIdx < winnerPriority)
                {
                    winnerPriority = priorityIdx;
                    winnerType = decoderType;
                }
            }

            if(winnerType != null)
            {
                DecodeConfiguration bestConfig = winningConfigurations.get(winnerType);
                if(bestConfig instanceof DecodeConfigAnalog || bestConfig == null)
                {
                    bestConfig = buildResultConfiguration(winnerType, request.approximateBandwidthHz());
                }
                WatcherSnapshot snap = snapshots.getOrDefault(winnerType,
                    new WatcherSnapshot(SignalKind.UNKNOWN, "", Map.of()));

                // Build summary: "P25 Phase 1 · control · NAC:0x293"
                String summary = winnerType.getDisplayString()
                    + (snap.summary().isBlank() ? "" : " · " + snap.summary());

                return ClassificationResult.identified(
                    freqHz,
                    candidates,
                    winnerType,
                    bestConfig,
                    snap.kind(),
                    summary,
                    snap.metadata(),
                    signalPowerDb
                );
            }
            else
            {
                return ClassificationResult.unidentified(freqHz, candidates, signalPowerDb);
            }
            // session.close() runs here via try-with-resources
        }
    }

    private record PendingProbe(ProbeChain chain, Duration window) {}

    private void launchAvailableProbes(List<DecoderType> ordered, int[] nextIndex, int lockedPriority,
                                       Deque<PendingProbe> pendingVariants, List<ProbeChain> activeChains,
                                       List<Instant> activeDeadlines, int maxConcurrent,
                                       ComplexSampleFanout fanout, ClassificationSession session,
                                       long freqHz, List<Candidate> candidates)
    {
        while(activeChains.size() < maxConcurrent)
        {
            if(pendingVariants.isEmpty())
            {
                if(nextIndex[0] >= ordered.size() || nextIndex[0] > lockedPriority)
                {
                    return;
                }

                DecoderType decoderType = ordered.get(nextIndex[0]++);
                try
                {
                    List<ProbeChain> variants = mProbeChainFactory.buildAll(decoderType);
                    Duration variantWindow = dividedProbeWindow(mDiscoveryPreference.probeWindow(decoderType),
                        variants.size(), maxConcurrent);
                    variants.forEach(variant -> pendingVariants.addLast(new PendingProbe(variant, variantWindow)));
                }
                catch(Exception e)
                {
                    mLog.warn("SignalClassifier: error building probe for {} at {} Hz: {}",
                        decoderType, freqHz, e.getMessage());
                    candidates.add(new Candidate(decoderType, LockState.ERROR, 0.0, e.getMessage()));
                    continue;
                }
            }

            PendingProbe pending = pendingVariants.removeFirst();
            ProbeChain launched = launchProbeChain(pending.chain(), fanout, session, freqHz, candidates);
            if(launched != null)
            {
                activeChains.add(launched);
                activeDeadlines.add(Instant.now().plus(pending.window()));
            }
        }
    }

    private static void recordProbeResult(ProbeChain pc, LockState state, List<Candidate> candidates,
                                          Map<DecoderType, WatcherSnapshot> snapshots,
                                          Map<DecoderType, DecodeConfiguration> winningConfigurations,
                                          Map<DecoderType, Double> winningQualities)
    {
        double quality = pc.lockWatcher().getLockQuality();
        candidates.add(new Candidate(pc.decoderType(), state, quality, null));

        if(state == LockState.LOCKED && quality >= winningQualities.getOrDefault(pc.decoderType(), -1.0))
        {
            winningQualities.put(pc.decoderType(), quality);
            winningConfigurations.put(pc.decoderType(), DecoderFactory.copy(pc.decodeConfiguration()));
            snapshots.put(pc.decoderType(), new WatcherSnapshot(pc.lockWatcher().getKind(),
                pc.lockWatcher().getSummary(), pc.lockWatcher().getMetadata()));
        }
    }

    private void stopLowerPriorityProbes(List<DecoderType> ordered, int lockedPriority,
                                         List<ProbeChain> activeChains, List<Instant> activeDeadlines,
                                         List<Candidate> candidates, ClassificationSession session,
                                         ComplexSampleFanout fanout)
    {
        for(int i = activeChains.size() - 1; i >= 0; i--)
        {
            ProbeChain active = activeChains.get(i);
            if(ordered.indexOf(active.decoderType()) > lockedPriority)
            {
                candidates.add(new Candidate(active.decoderType(), active.lockWatcher().getLockState(),
                    active.lockWatcher().getLockQuality(), null));
                tearDownChain(active, session, fanout);
                activeChains.remove(i);
                activeDeadlines.remove(i);
            }
        }
    }

    static Duration dividedProbeWindow(Duration decoderWindow, int variantCount, int maxConcurrent)
    {
        if(maxConcurrent == 1 && variantCount > 1)
        {
            return decoderWindow.dividedBy(variantCount);
        }

        return decoderWindow;
    }

    private static void disposePendingProbes(Deque<PendingProbe> pendingVariants)
    {
        for(PendingProbe pending : pendingVariants)
        {
            try
            {
                pending.chain().chain().dispose();
            }
            catch(Exception ignored)
            {
                // Best-effort disposal of variants that were never started.
            }
        }
        pendingVariants.clear();
    }

    // -------------------------------------------------------------------------
    // Probe chain launch / teardown helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a probe chain for the given decoder, wires it to the fanout, registers it with
     * the session, and starts it.  On any failure, records an ERROR candidate and returns null.
     */
    private ProbeChain launchProbeChain(ProbeChain pc, ComplexSampleFanout fanout,
                                         ClassificationSession session, long freqHz,
                                         List<Candidate> candidates)
    {
        ComplexSource subscriberSource = null;

        try
        {
            subscriberSource = fanout.newSubscriberSource();

            // Register with session immediately after build, before setSource/start,
            // so a mid-setup exception cannot leak a running chain.
            if(pc.chain() != null)
            {
                session.addProbeChain(pc.chain());
                pc.chain().setSource(subscriberSource);
                pc.chain().start();
            }

            return new ProbeChain(pc.decoderType(), pc.decodeConfiguration(), pc.chain(), pc.lockWatcher(),
                subscriberSource);
        }
        catch(Exception e)
        {
            if(subscriberSource != null)
            {
                fanout.removeSubscriberSource(subscriberSource);
            }

            if(pc != null && pc.chain() != null)
            {
                session.removeProbeChain(pc.chain());
                try { pc.chain().stop(); } catch(Exception ex) { mLog.debug("Stop error for failed probe chain", ex); }
                try { pc.chain().dispose(); } catch(Exception ex) { mLog.debug("Dispose error for failed probe chain", ex); }
            }

            mLog.warn("SignalClassifier: error launching probe for {} at {} Hz: {}",
                pc.decoderType(), freqHz, e.getMessage());
            candidates.add(new Candidate(pc.decoderType(), LockState.ERROR, 0.0, e.getMessage()));
            return null;
        }
    }

    /**
     * Stops and disposes a probe chain and removes it from the fanout / session.
     */
    private void tearDownChain(ProbeChain pc, ClassificationSession session, ComplexSampleFanout fanout)
    {
        if(pc.source() != null)
        {
            fanout.removeSubscriberSource(pc.source());
        }

        if(pc.chain() != null)
        {
            session.removeProbeChain(pc.chain());
            try { pc.chain().stop(); } catch(Exception ex) { mLog.debug("Stop error for probe chain", ex); }
            try { pc.chain().dispose(); } catch(Exception ex) { mLog.debug("Dispose error for probe chain", ex); }
        }
    }

    // -------------------------------------------------------------------------
    // Energy gate
    // -------------------------------------------------------------------------

    /**
     * Attaches a {@link PowerMonitor} subscriber to the fanout, then starts the real
     * source, and waits up to {@link #ENERGY_GATE_WINDOW} collecting power readings.
     *
     * <p>If no readings arrive, or the strongest spectral bin does not exceed the adaptive
     * FFT-bin noise floor by the configured threshold, {@code Double.NaN} is returned.
     * Otherwise the peak measured channel power is returned for result reporting.</p>
     *
     * <p>The subscriber is registered <em>before</em> the source is started so that
     * samples delivered synchronously during {@code start()} (as in tests) are captured.</p>
     *
     * @param fanout        the active fanout (real source not yet started)
     * @param realSource    the real source to start
     * @param cancelledFlag set to true if the caller has been cancelled
     * @return the measured peak power in dBm, or {@code Double.NaN} if no signal was detected
     */
    private double runEnergyGateWithStart(ComplexSampleFanout fanout, ComplexSource realSource,
                                           AtomicBoolean cancelledFlag, Instant overallDeadline)
    {
        // Collect power readings so we can report the peak measured signal power.
        List<Double> powerReadings = new ArrayList<>();
        Object lock = new Object();
        boolean[] collecting = {true};

        PowerMonitor pm = new PowerMonitor();
        pm.setSampleRate((int) realSource.getSampleRate());
        EnergySpectrum spectrum = new EnergySpectrum();

        pm.setSourceEventListener(event -> {
            if(event.getEvent() == SourceEvent.Event.NOTIFICATION_CHANNEL_POWER && event.hasValue())
            {
                double powerDb = event.getValue().doubleValue();

                synchronized(lock)
                {
                    if(collecting[0])
                    {
                        powerReadings.add(powerDb);
                        lock.notifyAll();
                    }
                }
            }
        });

        // Register subscriber BEFORE starting the source
        ComplexSource energySub = fanout.newSubscriberSource();
        energySub.setListener(samples ->
        {
            spectrum.process(samples.i(), samples.q());
            pm.process(samples.i(), samples.q());
        });

        // Now start the source — any synchronously pushed samples will be captured
        realSource.start();

        long gateEndMs = Math.min(System.currentTimeMillis() + ENERGY_GATE_WINDOW.toMillis(),
            overallDeadline.toEpochMilli());

        synchronized(lock)
        {
            while(System.currentTimeMillis() < gateEndMs && !cancelledFlag.get()
                && !Thread.currentThread().isInterrupted())
            {
                long remaining = gateEndMs - System.currentTimeMillis();

                if(remaining <= 0)
                {
                    break;
                }

                try
                {
                    lock.wait(Math.min(remaining, 50));
                }
                catch(InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        fanout.removeSubscriberSource(energySub);

        synchronized(lock)
        {
            collecting[0] = false;

            // If cancelled, return NaN — caller will check cancelledFlag and return CANCELLED
            if(cancelledFlag.get() || Thread.currentThread().isInterrupted())
            {
                return Double.NaN;
            }

            double peakPower = powerReadings.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
            double spectralSnr = spectrum.getPeakSnrDb();
            return Double.isFinite(peakPower) && spectralSnr >= mDiscoveryPreference.getEnergyThresholdDb()
                ? peakPower : Double.NaN;
        }
    }

    /**
     * Accumulates short FFT frames and compares the strongest averaged bin with the median-bin adaptive floor.
     */
    private static class EnergySpectrum
    {
        private static final int FFT_SIZE = 2048;
        private final float[] mWindow = WindowFactory.getWindow(WindowType.HANN, FFT_SIZE);
        private final FloatFFT_1D mFft = new FloatFFT_1D(FFT_SIZE);
        private final float[] mFrame = new float[FFT_SIZE * 2];
        private final double[] mPowerSum = new double[FFT_SIZE];
        private int mPointer;
        private int mFrameCount;

        synchronized void process(float[] i, float[] q)
        {
            int count = Math.min(i.length, q.length);
            for(int sample = 0; sample < count; sample++)
            {
                mFrame[mPointer * 2] = i[sample] * mWindow[mPointer];
                mFrame[mPointer * 2 + 1] = q[sample] * mWindow[mPointer];
                mPointer++;

                if(mPointer == FFT_SIZE)
                {
                    mFft.complexForward(mFrame);
                    for(int bin = 0; bin < FFT_SIZE; bin++)
                    {
                        float real = mFrame[bin * 2];
                        float imaginary = mFrame[bin * 2 + 1];
                        mPowerSum[bin] += real * real + imaginary * imaginary;
                    }
                    mFrameCount++;
                    mPointer = 0;
                }
            }
        }

        synchronized double getPeakSnrDb()
        {
            if(mFrameCount == 0)
            {
                return Double.NaN;
            }

            float[] averagedDb = new float[FFT_SIZE];
            float peak = -Float.MAX_VALUE;
            for(int bin = 0; bin < FFT_SIZE; bin++)
            {
                averagedDb[bin] = (float)(10.0 * Math.log10(Math.max(1.0e-20,
                    mPowerSum[bin] / mFrameCount)));
                peak = Math.max(peak, averagedDb[bin]);
            }

            float[] floorBins = averagedDb.clone();
            Arrays.sort(floorBins);
            double noiseFloor = floorBins[floorBins.length / 2];
            return peak - noiseFloor;
        }
    }
}
