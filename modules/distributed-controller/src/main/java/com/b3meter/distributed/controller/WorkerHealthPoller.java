package com.jmeternext.distributed.controller;

import com.jmeternext.worker.proto.HealthStatus;
import com.jmeternext.worker.proto.WorkerState;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Periodically polls {@code GetHealth} on every registered worker and emits
 * status-change events when a worker becomes unavailable.
 *
 * <p>Polling interval: every {@value #POLL_INTERVAL_MS} ms (5 seconds).
 * A worker is declared unavailable after {@value #MAX_MISSED_HEARTBEATS} consecutive
 * missed heartbeats (15 seconds at the default interval).
 *
 * <p>Status events are delivered synchronously to all registered
 * {@link BiConsumer}&lt;String, WorkerAvailability&gt; listeners where:
 * <ul>
 *   <li>the first argument is the worker ID</li>
 *   <li>the second argument is {@link WorkerAvailability#AVAILABLE} or
 *       {@link WorkerAvailability#UNAVAILABLE}</li>
 * </ul>
 *
 * <p>Thread-safety: all public methods are thread-safe.
 */
public class WorkerHealthPoller implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(WorkerHealthPoller.class.getName());

    /** How often each worker is polled (milliseconds). */
    static final long POLL_INTERVAL_MS = 5_000L;

    /** Number of missed polls before a worker is declared unavailable. */
    static final int MAX_MISSED_HEARTBEATS = 3;

    /**
     * Worker availability states emitted to listeners.
     */
    public enum WorkerAvailability {
        /** Worker responded within the expected poll window. */
        AVAILABLE,
        /** Worker missed {@value WorkerHealthPoller#MAX_MISSED_HEARTBEATS} consecutive polls. */
        UNAVAILABLE
    }

    /** Per-worker consecutive miss counter. */
    private final ConcurrentHashMap<String, AtomicInteger> missCounters = new ConcurrentHashMap<>();

    /** Last known availability per worker — used to detect state transitions. */
    private final ConcurrentHashMap<String, WorkerAvailability> lastAvailability = new ConcurrentHashMap<>();

    /** Registered event listeners. */
    private final List<BiConsumer<String, WorkerAvailability>> listeners = new CopyOnWriteArrayList<>();

    /** Workers under active observation. */
    private final Map<String, WorkerClient> workers = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pollTask;

    /**
     * Creates a new poller with the default daemon scheduler.
     */
    public WorkerHealthPoller() {
        this(Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "worker-health-poller");
            t.setDaemon(true);
            return t;
        }));
    }

    /**
     * Creates a new poller using the provided scheduler (useful for testing with
     * a manual or fast-forward scheduler).
     *
     * @param scheduler the executor that drives the polling loop
     */
    WorkerHealthPoller(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Starts the periodic polling loop.
     *
     * <p>May be called multiple times; subsequent calls are no-ops if already started.
     */
    public synchronized void start() {
        if (pollTask != null && !pollTask.isDone()) {
            return; // already running
        }
        pollTask = scheduler.scheduleAtFixedRate(
                this::pollAll,
                POLL_INTERVAL_MS,
                POLL_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
        LOG.info("WorkerHealthPoller started");
    }

    /**
     * Stops the polling loop without shutting down the scheduler.
     */
    public synchronized void stop() {
        if (pollTask != null) {
            pollTask.cancel(false);
            pollTask = null;
        }
        LOG.info("WorkerHealthPoller stopped");
    }

    /**
     * Shuts down the poller and its scheduler, waiting up to 5 seconds.
     */
    @Override
    public void close() {
        stop();
        try {
            scheduler.shutdown();
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
    }

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    /**
     * Adds a worker to the polling set.
     *
     * <p>If the worker is already registered, this method is a no-op.
     *
     * @param client the worker client to poll; must not be {@code null}
     */
    public void addWorker(WorkerClient client) {
        String id = client.getWorkerId();
        workers.putIfAbsent(id, client);
        missCounters.putIfAbsent(id, new AtomicInteger(0));
        lastAvailability.putIfAbsent(id, WorkerAvailability.AVAILABLE);
        LOG.log(Level.FINE, "Registered worker={0} for health polling", id);
    }

    /**
     * Removes a worker from the polling set.
     *
     * @param workerId the worker to deregister
     */
    public void removeWorker(String workerId) {
        workers.remove(workerId);
        missCounters.remove(workerId);
        lastAvailability.remove(workerId);
    }

    /**
     * Registers a listener that is invoked whenever a worker's availability changes.
     *
     * @param listener the callback; first arg is worker ID, second is the new availability
     */
    public void addListener(BiConsumer<String, WorkerAvailability> listener) {
        listeners.add(listener);
    }

    /**
     * Returns the last known availability for the given worker.
     *
     * @param workerId the worker to query
     * @return current availability, or {@code null} if the worker is not registered
     */
    public WorkerAvailability currentAvailability(String workerId) {
        return lastAvailability.get(workerId);
    }

    /**
     * Returns the current consecutive miss count for a worker (visible for testing).
     *
     * @param workerId the worker to query
     * @return miss count, or 0 if not registered
     */
    int missCount(String workerId) {
        AtomicInteger counter = missCounters.get(workerId);
        return counter == null ? 0 : counter.get();
    }

    // -------------------------------------------------------------------------
    // Internal polling loop
    // -------------------------------------------------------------------------

    /**
     * Polls all registered workers once and updates availability tracking.
     * Called by the scheduler on each interval tick.
     */
    void pollAll() {
        for (Map.Entry<String, WorkerClient> entry : workers.entrySet()) {
            String workerId = entry.getKey();
            WorkerClient client = entry.getValue();
            pollWorker(workerId, client);
        }
    }

    private void pollWorker(String workerId, WorkerClient client) {
        try {
            HealthStatus status = client.getHealth("");
            WorkerState workerState = status.getState();

            if (workerState == WorkerState.WORKER_STATE_UNSPECIFIED) {
                // getHealth returned a failure sentinel
                recordMiss(workerId);
            } else {
                recordHeartbeat(workerId);
            }
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Health poll failed for worker={0}: {1}",
                    new Object[]{workerId, ex.getMessage()});
            recordMiss(workerId);
        }
    }

    private void recordHeartbeat(String workerId) {
        AtomicInteger counter = missCounters.get(workerId);
        if (counter == null) return;
        counter.set(0);
        transitionIfChanged(workerId, WorkerAvailability.AVAILABLE);
    }

    private void recordMiss(String workerId) {
        AtomicInteger counter = missCounters.get(workerId);
        if (counter == null) return;
        int misses = counter.incrementAndGet();
        LOG.log(Level.FINE, "Worker={0} missed heartbeat #{1}", new Object[]{workerId, misses});
        if (misses >= MAX_MISSED_HEARTBEATS) {
            transitionIfChanged(workerId, WorkerAvailability.UNAVAILABLE);
        }
    }

    private void transitionIfChanged(String workerId, WorkerAvailability newAvailability) {
        WorkerAvailability previous = lastAvailability.put(workerId, newAvailability);
        if (previous != newAvailability) {
            LOG.log(Level.INFO,
                    "Worker={0} availability changed: {1} → {2}",
                    new Object[]{workerId, previous, newAvailability});
            emitEvent(workerId, newAvailability);
        }
    }

    private void emitEvent(String workerId, WorkerAvailability availability) {
        for (BiConsumer<String, WorkerAvailability> listener : listeners) {
            try {
                listener.accept(workerId, availability);
            } catch (Exception ex) {
                LOG.log(Level.WARNING,
                        "Listener threw during availability event for worker={0}", workerId);
            }
        }
    }
}
