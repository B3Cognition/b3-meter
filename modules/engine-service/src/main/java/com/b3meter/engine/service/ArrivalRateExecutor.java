/*
 * Copyright 2024-2026 b3meter Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.b3meter.engine.service;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generates iterations at a target rate (iterations/second), independent of VU count.
 *
 * <p>Unlike {@link VirtualUserExecutor} which spawns N VUs each looping forever,
 * this executor maintains a VU pool and dispatches iterations at a fixed rate.
 * If a VU is still busy when the next iteration is due, a new VU is allocated
 * (up to {@code maxVUs}). If all VUs are busy and the pool is at capacity,
 * the iteration is dropped and counted.
 *
 * <p>This is the conceptually correct model for load testing: you test the server
 * at a known request rate, not at a rate determined by client-side VU capacity.
 * The actual VU count becomes an output metric rather than an input parameter.
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>A single-thread {@link ScheduledExecutorService} (platform thread) acts as
 *       the rate ticker, firing at intervals of {@code 1_000_000_000 / targetRate}
 *       nanoseconds.</li>
 *   <li>On each tick, the ticker attempts to acquire a permit from a
 *       {@link Semaphore} that tracks idle VUs.</li>
 *   <li>If a permit is available, the iteration is submitted to the virtual-thread
 *       pool. The permit is released when the iteration completes.</li>
 *   <li>If no permit is available and {@code allocatedVUs < maxVUs}, the pool is
 *       expanded by adding a new permit, which is then immediately acquired.</li>
 *   <li>If no permit is available and the pool is at capacity, the iteration is
 *       dropped and {@code droppedIterations} is incremented.</li>
 * </ol>
 *
 * <h2>Thread safety</h2>
 * <p>All mutable state is accessed through atomic variables or protected by
 * {@link ReentrantLock}. The lock is used (instead of {@code synchronized}) to
 * avoid pinning virtual threads to their carrier threads (JEP-491).
 *
 * <p>Inspired by k6's {@code constant-arrival-rate} executor.
 *
 * @see RampingArrivalRateExecutor
 * @see ExecutorType#CONSTANT_ARRIVAL_RATE
 */
public final class ArrivalRateExecutor implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(ArrivalRateExecutor.class.getName());

    // -------------------------------------------------------------------------
    // Configuration (immutable after construction)
    // -------------------------------------------------------------------------

    private final double targetRate;
    private final int maxVUs;
    private final int preAllocatedVUs;
    private final Duration duration;
    private final TestRunContext context;

    // -------------------------------------------------------------------------
    // Runtime state
    // -------------------------------------------------------------------------

    /** Virtual-thread pool where iteration tasks execute. */
    private final ExecutorService vuPool;

    /** Platform-thread scheduler that fires the rate ticker. */
    private ScheduledExecutorService ticker;

    /**
     * Semaphore tracking idle VUs. Initial permits = preAllocatedVUs.
     * Each iteration acquires a permit before dispatch and releases it on completion.
     * New permits are added when the pool is expanded.
     */
    private final Semaphore idleVUs;

    /** Number of VUs currently executing an iteration. */
    private final AtomicInteger activeVUs = new AtomicInteger(0);

    /** Total number of VUs allocated (pre-allocated + dynamically added). */
    private final AtomicInteger allocatedVUs = new AtomicInteger(0);

    /** Total iterations successfully dispatched. */
    private final AtomicLong totalIterations = new AtomicLong(0);

    /** Iterations that could not be dispatched because all VUs were busy. */
    private final AtomicLong droppedIterations = new AtomicLong(0);

    /** Guards start/stop transitions to prevent concurrent lifecycle changes. */
    private final ReentrantLock stateLock = new ReentrantLock();

    /** {@code true} while the executor is actively dispatching iterations. */
    private volatile boolean running = false;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Creates a new constant arrival-rate executor.
     *
     * @param targetRate      desired iterations per second; must be &gt; 0
     * @param maxVUs          maximum number of VUs in the pool; must be &ge; preAllocatedVUs
     * @param preAllocatedVUs number of VUs to pre-create; must be &ge; 1
     * @param duration        total execution duration; must not be {@code null} or zero
     * @param context         the test run context; must not be {@code null}
     * @throws IllegalArgumentException if any numeric constraint is violated
     * @throws NullPointerException     if {@code duration} or {@code context} is {@code null}
     */
    public ArrivalRateExecutor(double targetRate,
                               int maxVUs,
                               int preAllocatedVUs,
                               Duration duration,
                               TestRunContext context) {
        if (targetRate <= 0) {
            throw new IllegalArgumentException("targetRate must be > 0, got: " + targetRate);
        }
        if (preAllocatedVUs < 1) {
            throw new IllegalArgumentException("preAllocatedVUs must be >= 1, got: " + preAllocatedVUs);
        }
        if (maxVUs < preAllocatedVUs) {
            throw new IllegalArgumentException(
                    "maxVUs (" + maxVUs + ") must be >= preAllocatedVUs (" + preAllocatedVUs + ")");
        }
        Objects.requireNonNull(duration, "duration must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("duration must be positive, got: " + duration);
        }

        this.targetRate = targetRate;
        this.maxVUs = maxVUs;
        this.preAllocatedVUs = preAllocatedVUs;
        this.duration = duration;
        this.context = Objects.requireNonNull(context, "context must not be null");

        this.vuPool = Executors.newVirtualThreadPerTaskExecutor();
        this.idleVUs = new Semaphore(preAllocatedVUs);
        this.allocatedVUs.set(preAllocatedVUs);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Starts dispatching iterations at the configured rate.
     *
     * <p>Each tick submits {@code iterationTask} to the virtual-thread pool.
     * The task receives no arguments; it should capture any needed context
     * via closure or from the {@link TestRunContext}.
     *
     * <p>This method returns immediately. The ticker runs on a dedicated platform
     * thread. The test duration is enforced by scheduling a shutdown task.
     *
     * @param iterationTask the work to execute for each iteration; must not be {@code null}
     * @throws IllegalStateException if already running
     * @throws NullPointerException  if {@code iterationTask} is {@code null}
     */
    public void start(Runnable iterationTask) {
        Objects.requireNonNull(iterationTask, "iterationTask must not be null");

        stateLock.lock();
        try {
            if (running) {
                throw new IllegalStateException("Executor is already running");
            }
            running = true;
        } finally {
            stateLock.unlock();
        }

        long tickIntervalNanos = (long) (1_000_000_000.0 / targetRate);

        LOG.log(Level.INFO,
                "ArrivalRateExecutor: starting — rate={0} iter/s, preAllocated={1}, "
                        + "maxVUs={2}, duration={3}s, tickInterval={4}ns",
                new Object[]{targetRate, preAllocatedVUs, maxVUs,
                        duration.toSeconds(), tickIntervalNanos});

        ticker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "arrival-rate-ticker-" + context.getRunId());
            t.setDaemon(true);
            return t;
        });

        // Schedule the rate ticker
        ticker.scheduleAtFixedRate(
                () -> dispatchIteration(iterationTask),
                0,
                tickIntervalNanos,
                TimeUnit.NANOSECONDS);

        // Schedule the duration-based shutdown
        ticker.schedule(this::stop, duration.toNanos(), TimeUnit.NANOSECONDS);
    }

    /**
     * Gracefully stops the executor.
     *
     * <p>The ticker is shut down immediately (no new iterations will be dispatched).
     * In-flight iterations on the virtual-thread pool are given up to 30 seconds
     * to complete before the pool is forcibly shut down.
     */
    public void stop() {
        stateLock.lock();
        try {
            if (!running) {
                return;
            }
            running = false;
        } finally {
            stateLock.unlock();
        }

        LOG.log(Level.INFO,
                "ArrivalRateExecutor: stopping — dispatched={0}, dropped={1}, "
                        + "peakVUs={2}, activeVUs={3}",
                new Object[]{totalIterations.get(), droppedIterations.get(),
                        allocatedVUs.get(), activeVUs.get()});

        // Stop the ticker first so no new iterations are dispatched
        if (ticker != null) {
            ticker.shutdownNow();
        }

        // Give in-flight iterations time to finish
        vuPool.shutdown();
        try {
            if (!vuPool.awaitTermination(30, TimeUnit.SECONDS)) {
                LOG.log(Level.WARNING,
                        "ArrivalRateExecutor: VU pool did not terminate within 30s, forcing shutdown");
                vuPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            vuPool.shutdownNow();
        }
    }

    /**
     * Closes this executor by calling {@link #stop()}.
     *
     * <p>Allows use in try-with-resources blocks.
     */
    @Override
    public void close() {
        stop();
    }

    // -------------------------------------------------------------------------
    // Core dispatch logic
    // -------------------------------------------------------------------------

    /**
     * Attempts to dispatch a single iteration. Called by the ticker on each tick.
     *
     * <p>The algorithm:
     * <ol>
     *   <li>Try to acquire a semaphore permit (idle VU) without blocking.</li>
     *   <li>If successful, submit the iteration to the VU pool.</li>
     *   <li>If no permit available, try to expand the pool.</li>
     *   <li>If pool is at max capacity, drop the iteration.</li>
     * </ol>
     */
    private void dispatchIteration(Runnable iterationTask) {
        if (!running) {
            return;
        }

        // Try to acquire an idle VU
        if (idleVUs.tryAcquire()) {
            submitIteration(iterationTask);
            return;
        }

        // No idle VU — try to expand the pool
        stateLock.lock();
        try {
            if (allocatedVUs.get() < maxVUs) {
                int newCount = allocatedVUs.incrementAndGet();
                // Add a new permit and immediately acquire it
                idleVUs.release();
                idleVUs.tryAcquire(); // will succeed since we just released

                LOG.log(Level.FINE,
                        "ArrivalRateExecutor: expanded VU pool to {0} (max={1})",
                        new Object[]{newCount, maxVUs});

                submitIteration(iterationTask);
                return;
            }
        } finally {
            stateLock.unlock();
        }

        // All VUs busy and at max capacity — drop the iteration
        long dropped = droppedIterations.incrementAndGet();
        if (dropped == 1 || dropped % 100 == 0) {
            LOG.log(Level.WARNING,
                    "ArrivalRateExecutor: dropped iteration (total dropped={0}). "
                            + "All {1} VUs are busy. Consider increasing maxVUs.",
                    new Object[]{dropped, maxVUs});
        }
    }

    /**
     * Submits a single iteration to the virtual-thread pool.
     * The caller must have already acquired a semaphore permit.
     */
    private void submitIteration(Runnable iterationTask) {
        totalIterations.incrementAndGet();
        activeVUs.incrementAndGet();

        vuPool.submit(() -> {
            try {
                iterationTask.run();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "ArrivalRateExecutor: iteration failed", e);
            } finally {
                activeVUs.decrementAndGet();
                idleVUs.release();
            }
        });
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns a snapshot of the executor's runtime statistics.
     *
     * @return current statistics; never {@code null}
     */
    public Stats getStats() {
        return new Stats(
                activeVUs.get(),
                allocatedVUs.get(),
                totalIterations.get(),
                droppedIterations.get());
    }

    /**
     * Returns {@code true} if the executor is currently dispatching iterations.
     *
     * @return whether the executor is running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Returns the configured target rate in iterations per second.
     *
     * @return target rate
     */
    public double getTargetRate() {
        return targetRate;
    }

    /**
     * Returns the configured maximum VU count.
     *
     * @return max VUs
     */
    public int getMaxVUs() {
        return maxVUs;
    }

    /**
     * Returns the configured execution duration.
     *
     * @return duration; never {@code null}
     */
    public Duration getDuration() {
        return duration;
    }

    // -------------------------------------------------------------------------
    // Stats record
    // -------------------------------------------------------------------------

    /**
     * Immutable snapshot of arrival-rate executor statistics.
     *
     * @param activeVUs         number of VUs currently executing an iteration
     * @param allocatedVUs      total VUs allocated (pre-allocated + dynamically added)
     * @param totalIterations   iterations successfully dispatched
     * @param droppedIterations iterations dropped because all VUs were busy
     */
    public record Stats(
            int activeVUs,
            int allocatedVUs,
            long totalIterations,
            long droppedIterations) {

        @Override
        public String toString() {
            return "Stats{active=" + activeVUs
                    + ", allocated=" + allocatedVUs
                    + ", iterations=" + totalIterations
                    + ", dropped=" + droppedIterations + '}';
        }
    }
}
