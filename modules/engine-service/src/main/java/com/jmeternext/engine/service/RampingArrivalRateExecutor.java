package com.jmeternext.engine.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
 * Generates iterations at a variable rate that changes across time-based stages,
 * with linear interpolation between each stage's target rate.
 *
 * <p>This is the arrival-rate counterpart to a ramping VU model. Instead of
 * changing the number of concurrent users over time, this executor changes the
 * <em>iteration dispatch rate</em> over time. VUs are auto-scaled from a pool
 * to sustain the current rate.
 *
 * <h2>Stage model</h2>
 * <p>Each {@link Stage} defines a duration and a target rate. The executor
 * linearly interpolates from the previous stage's target rate (or {@code startRate}
 * for the first stage) to the current stage's target rate over the stage's duration.
 *
 * <p>Example: start at 10 iter/s, ramp to 50 iter/s over 1 minute, hold at 50
 * for 2 minutes, ramp down to 5 iter/s over 30 seconds:
 * <pre>{@code
 * RampingArrivalRateExecutor executor = new RampingArrivalRateExecutor(
 *     10.0,   // startRate
 *     100,    // maxVUs
 *     10,     // preAllocatedVUs
 *     context,
 *     List.of(
 *         new Stage(Duration.ofMinutes(1),  50.0),  // ramp 10 → 50
 *         new Stage(Duration.ofMinutes(2),  50.0),  // hold at 50
 *         new Stage(Duration.ofSeconds(30),  5.0)   // ramp 50 → 5
 *     )
 * );
 * }</pre>
 *
 * <h2>Implementation</h2>
 * <p>A rate-adjustment task runs every 100ms on a platform-thread scheduler,
 * recalculating the current interpolated rate and adjusting the tick interval
 * of the dispatch loop accordingly. The dispatch mechanism uses the same
 * semaphore-based VU pool as {@link ArrivalRateExecutor}.
 *
 * <h2>Thread safety</h2>
 * <p>All mutable state is accessed through atomic variables or protected by
 * {@link ReentrantLock}. The lock is used (instead of {@code synchronized}) to
 * avoid pinning virtual threads to their carrier threads (JEP-491).
 *
 * <p>Inspired by k6's {@code ramping-arrival-rate} executor.
 *
 * @see ArrivalRateExecutor
 * @see ExecutorType#RAMPING_ARRIVAL_RATE
 */
public final class RampingArrivalRateExecutor implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(RampingArrivalRateExecutor.class.getName());

    /**
     * How frequently (in milliseconds) the rate is recalculated and the tick
     * interval adjusted. 100ms provides smooth interpolation without excessive
     * overhead.
     */
    private static final long RATE_ADJUST_INTERVAL_MS = 100;

    // -------------------------------------------------------------------------
    // Configuration (immutable after construction)
    // -------------------------------------------------------------------------

    private final double startRate;
    private final int maxVUs;
    private final int preAllocatedVUs;
    private final TestRunContext context;
    private final List<Stage> stages;
    private final Duration totalDuration;

    // -------------------------------------------------------------------------
    // Runtime state
    // -------------------------------------------------------------------------

    /** Virtual-thread pool where iteration tasks execute. */
    private final ExecutorService vuPool;

    /** Platform-thread scheduler for the rate ticker and adjustment task. */
    private ScheduledExecutorService scheduler;

    /**
     * Semaphore tracking idle VUs. Initial permits = preAllocatedVUs.
     * Each iteration acquires a permit before dispatch and releases it on completion.
     */
    private final Semaphore idleVUs;

    /** Number of VUs currently executing an iteration. */
    private final AtomicInteger activeVUs = new AtomicInteger(0);

    /** Total number of VUs allocated (pre-allocated + dynamically added). */
    private final AtomicInteger allocatedVUs = new AtomicInteger(0);

    /** Total iterations successfully dispatched. */
    private final AtomicLong totalIterations = new AtomicLong(0);

    /** Iterations dropped because all VUs were busy. */
    private final AtomicLong droppedIterations = new AtomicLong(0);

    /** Guards start/stop transitions. */
    private final ReentrantLock stateLock = new ReentrantLock();

    /** {@code true} while the executor is actively dispatching iterations. */
    private volatile boolean running = false;

    /** Current rate in iterations per second, updated by the adjustment task. */
    private volatile double currentRate;

    /**
     * Nanosecond timestamp when {@link #start(Runnable)} was called.
     * Used to compute elapsed time for stage interpolation.
     */
    private volatile long startNanos;

    /**
     * Accumulated nanosecond debt from fractional tick intervals. Ensures that
     * sub-nanosecond remainders do not cause rate drift over time.
     */
    private volatile double tickDebtNanos = 0.0;

    /** Timestamp of the last dispatch, for interval-based dispatching. */
    private volatile long lastDispatchNanos;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Creates a new ramping arrival-rate executor.
     *
     * @param startRate       initial iteration rate (iterations/second); must be &ge; 0
     * @param maxVUs          maximum number of VUs in the pool; must be &ge; preAllocatedVUs
     * @param preAllocatedVUs number of VUs to pre-create; must be &ge; 1
     * @param context         the test run context; must not be {@code null}
     * @param stages          ordered list of stages; must not be empty
     * @throws IllegalArgumentException if any numeric constraint is violated or stages is empty
     * @throws NullPointerException     if {@code context} or {@code stages} is {@code null}
     */
    public RampingArrivalRateExecutor(double startRate,
                                      int maxVUs,
                                      int preAllocatedVUs,
                                      TestRunContext context,
                                      List<Stage> stages) {
        if (startRate < 0) {
            throw new IllegalArgumentException("startRate must be >= 0, got: " + startRate);
        }
        if (preAllocatedVUs < 1) {
            throw new IllegalArgumentException("preAllocatedVUs must be >= 1, got: " + preAllocatedVUs);
        }
        if (maxVUs < preAllocatedVUs) {
            throw new IllegalArgumentException(
                    "maxVUs (" + maxVUs + ") must be >= preAllocatedVUs (" + preAllocatedVUs + ")");
        }
        Objects.requireNonNull(stages, "stages must not be null");
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("stages must not be empty");
        }

        this.startRate = startRate;
        this.maxVUs = maxVUs;
        this.preAllocatedVUs = preAllocatedVUs;
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.stages = List.copyOf(stages);
        this.currentRate = startRate;

        this.vuPool = Executors.newVirtualThreadPerTaskExecutor();
        this.idleVUs = new Semaphore(preAllocatedVUs);
        this.allocatedVUs.set(preAllocatedVUs);

        // Pre-compute total duration across all stages
        Duration total = Duration.ZERO;
        for (Stage stage : this.stages) {
            total = total.plus(stage.duration());
        }
        this.totalDuration = total;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Starts the ramping arrival-rate execution.
     *
     * <p>A rate-adjustment task recalculates the current rate every 100ms using
     * linear interpolation across stages. A dispatch loop runs on the same
     * scheduler, dispatching iterations at the current rate.
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

        startNanos = System.nanoTime();
        lastDispatchNanos = startNanos;

        LOG.log(Level.INFO,
                "RampingArrivalRateExecutor: starting — startRate={0} iter/s, "
                        + "stages={1}, maxVUs={2}, preAllocated={3}, totalDuration={4}s",
                new Object[]{startRate, stages.size(), maxVUs, preAllocatedVUs,
                        totalDuration.toSeconds()});

        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "ramping-rate-scheduler-" + context.getRunId());
            t.setDaemon(true);
            return t;
        });

        // Rate adjustment task — recalculates current rate every 100ms
        scheduler.scheduleAtFixedRate(
                () -> adjustRate(),
                0,
                RATE_ADJUST_INTERVAL_MS,
                TimeUnit.MILLISECONDS);

        // Dispatch loop — runs every 1ms, dispatches based on accumulated debt
        scheduler.scheduleAtFixedRate(
                () -> dispatchLoop(iterationTask),
                0,
                1,
                TimeUnit.MILLISECONDS);

        // Duration-based shutdown
        scheduler.schedule(this::stop, totalDuration.toNanos(), TimeUnit.NANOSECONDS);
    }

    /**
     * Gracefully stops the executor.
     *
     * <p>The scheduler is shut down immediately (no new iterations dispatched).
     * In-flight iterations are given up to 30 seconds to complete.
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
                "RampingArrivalRateExecutor: stopping — dispatched={0}, dropped={1}, "
                        + "peakVUs={2}, activeVUs={3}, finalRate={4} iter/s",
                new Object[]{totalIterations.get(), droppedIterations.get(),
                        allocatedVUs.get(), activeVUs.get(), currentRate});

        if (scheduler != null) {
            scheduler.shutdownNow();
        }

        vuPool.shutdown();
        try {
            if (!vuPool.awaitTermination(30, TimeUnit.SECONDS)) {
                LOG.log(Level.WARNING,
                        "RampingArrivalRateExecutor: VU pool did not terminate within 30s, "
                                + "forcing shutdown");
                vuPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            vuPool.shutdownNow();
        }
    }

    /**
     * Closes this executor by calling {@link #stop()}.
     */
    @Override
    public void close() {
        stop();
    }

    // -------------------------------------------------------------------------
    // Rate interpolation
    // -------------------------------------------------------------------------

    /**
     * Recalculates the current target rate based on elapsed time and stage
     * definitions, using linear interpolation within each stage.
     */
    private void adjustRate() {
        if (!running) {
            return;
        }

        long elapsedNanos = System.nanoTime() - startNanos;
        double newRate = interpolateRate(elapsedNanos);

        if (Math.abs(newRate - currentRate) > 0.01) {
            LOG.log(Level.FINE,
                    "RampingArrivalRateExecutor: rate adjusted from {0} to {1} iter/s "
                            + "at elapsed={2}ms",
                    new Object[]{currentRate, newRate,
                            TimeUnit.NANOSECONDS.toMillis(elapsedNanos)});
        }

        currentRate = newRate;
    }

    /**
     * Computes the interpolated rate at the given elapsed time.
     *
     * <p>Walks through stages sequentially. Within each stage, linearly
     * interpolates between the previous stage's target rate (or {@code startRate})
     * and the current stage's target rate.
     *
     * @param elapsedNanos nanoseconds elapsed since start
     * @return interpolated rate in iterations/second
     */
    double interpolateRate(long elapsedNanos) {
        long accumulated = 0;
        double previousRate = startRate;

        for (Stage stage : stages) {
            long stageNanos = stage.duration().toNanos();

            if (elapsedNanos <= accumulated + stageNanos) {
                // We are within this stage — interpolate
                long intoStageNanos = elapsedNanos - accumulated;
                double progress = (double) intoStageNanos / stageNanos;
                return previousRate + (stage.targetRate() - previousRate) * progress;
            }

            accumulated += stageNanos;
            previousRate = stage.targetRate();
        }

        // Past all stages — hold at the final rate
        return previousRate;
    }

    // -------------------------------------------------------------------------
    // Dispatch logic
    // -------------------------------------------------------------------------

    /**
     * Called every 1ms by the scheduler. Determines how many iterations should
     * have been dispatched since the last call and dispatches them.
     *
     * <p>Uses a debt-accumulation model: each call adds {@code currentRate * elapsed}
     * iterations to the debt. For each whole iteration of debt, one iteration is
     * dispatched (or dropped if no VUs are available).
     */
    private void dispatchLoop(Runnable iterationTask) {
        if (!running) {
            return;
        }

        double rate = currentRate;
        if (rate <= 0) {
            return;
        }

        long now = System.nanoTime();
        long elapsedSinceLastDispatch = now - lastDispatchNanos;
        lastDispatchNanos = now;

        // Calculate how many iterations are owed
        double iterationsOwed = (rate * elapsedSinceLastDispatch / 1_000_000_000.0)
                + tickDebtNanos;

        int wholeTicks = (int) iterationsOwed;
        tickDebtNanos = iterationsOwed - wholeTicks;

        for (int i = 0; i < wholeTicks; i++) {
            if (!running) {
                break;
            }
            dispatchSingleIteration(iterationTask);
        }
    }

    /**
     * Attempts to dispatch a single iteration using the same VU-pool algorithm
     * as {@link ArrivalRateExecutor}.
     */
    private void dispatchSingleIteration(Runnable iterationTask) {
        // Try to acquire an idle VU
        if (idleVUs.tryAcquire()) {
            submitIteration(iterationTask);
            return;
        }

        // No idle VU — try to expand the pool
        stateLock.lock();
        try {
            if (allocatedVUs.get() < maxVUs) {
                allocatedVUs.incrementAndGet();
                idleVUs.release();
                idleVUs.tryAcquire();

                LOG.log(Level.FINE,
                        "RampingArrivalRateExecutor: expanded VU pool to {0}",
                        allocatedVUs.get());

                submitIteration(iterationTask);
                return;
            }
        } finally {
            stateLock.unlock();
        }

        // All VUs busy and at max capacity
        long dropped = droppedIterations.incrementAndGet();
        if (dropped == 1 || dropped % 100 == 0) {
            LOG.log(Level.WARNING,
                    "RampingArrivalRateExecutor: dropped iteration (total dropped={0}). "
                            + "All {1} VUs busy at rate={2} iter/s.",
                    new Object[]{dropped, maxVUs, currentRate});
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
                LOG.log(Level.WARNING,
                        "RampingArrivalRateExecutor: iteration failed", e);
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
    public ArrivalRateExecutor.Stats getStats() {
        return new ArrivalRateExecutor.Stats(
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
     * Returns the current interpolated rate in iterations per second.
     *
     * @return current rate
     */
    public double getCurrentRate() {
        return currentRate;
    }

    /**
     * Returns the configured start rate.
     *
     * @return start rate in iterations/second
     */
    public double getStartRate() {
        return startRate;
    }

    /**
     * Returns an unmodifiable view of the configured stages.
     *
     * @return stage list; never {@code null}
     */
    public List<Stage> getStages() {
        return stages; // already an unmodifiable copy from List.copyOf
    }

    /**
     * Returns the total duration across all stages.
     *
     * @return total duration; never {@code null}
     */
    public Duration getTotalDuration() {
        return totalDuration;
    }

    // -------------------------------------------------------------------------
    // Stage record
    // -------------------------------------------------------------------------

    /**
     * A single stage in a ramping arrival-rate profile.
     *
     * <p>Each stage defines a time window and a target rate. The executor linearly
     * interpolates from the previous rate to this stage's target rate over the
     * stage's duration.
     *
     * @param duration   how long this stage lasts; must be positive
     * @param targetRate the iteration rate (iter/s) to reach by the end of this stage; must be &ge; 0
     */
    public record Stage(Duration duration, double targetRate) {

        /**
         * Validates stage parameters.
         *
         * @throws IllegalArgumentException if duration is not positive or targetRate is negative
         * @throws NullPointerException     if duration is null
         */
        public Stage {
            Objects.requireNonNull(duration, "stage duration must not be null");
            if (duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException(
                        "stage duration must be positive, got: " + duration);
            }
            if (targetRate < 0) {
                throw new IllegalArgumentException(
                        "stage targetRate must be >= 0, got: " + targetRate);
            }
        }

        @Override
        public String toString() {
            return "Stage{duration=" + duration.toSeconds() + "s, targetRate=" + targetRate + "}";
        }
    }
}
