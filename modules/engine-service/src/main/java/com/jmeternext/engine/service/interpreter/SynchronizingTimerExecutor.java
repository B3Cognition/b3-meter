package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code SynchronizingTimer} node.
 *
 * <p>The Synchronizing Timer (a.k.a. "Rendezvous Point") blocks each thread until
 * {@code groupSize} threads have arrived, then releases all of them simultaneously.
 * This is used to create artificial concurrency spikes.
 *
 * <p>JMeter properties:
 * <ul>
 *   <li>{@code groupSize} — number of threads to wait for (0 = all threads in group)</li>
 *   <li>{@code timeoutInMs} — maximum wait time in ms (0 = wait forever)</li>
 * </ul>
 *
 * <p>Implementation uses {@link CyclicBarrier} keyed by timer test name,
 * so multiple synchronizing timers in the same plan do not interfere.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class SynchronizingTimerExecutor {

    private static final Logger LOG = Logger.getLogger(SynchronizingTimerExecutor.class.getName());

    /**
     * Barriers keyed by timer identity (testName). A CyclicBarrier is reusable
     * across loop iterations.
     */
    private static final ConcurrentHashMap<String, CyclicBarrier> BARRIERS =
            new ConcurrentHashMap<>();

    private SynchronizingTimerExecutor() {}

    /**
     * Blocks the calling thread until {@code groupSize} threads arrive or the timeout expires.
     *
     * @param node the timer node; must not be {@code null}
     */
    public static void execute(PlanNode node) {
        Objects.requireNonNull(node, "node must not be null");

        int groupSize = node.getIntProp("groupSize", 0);
        if (groupSize <= 0) {
            // groupSize 0 means "all threads" — we default to 1 (no barrier) since
            // we cannot know the total thread count at this level
            groupSize = 1;
        }

        long timeoutMs = parseLong(node.getStringProp("timeoutInMs", "0"), 0L);

        String key = node.getTestName();

        final int gs = groupSize;
        CyclicBarrier barrier = BARRIERS.computeIfAbsent(key,
                k -> new CyclicBarrier(gs));

        // If the group size changed (e.g., different test run reusing static map),
        // replace the barrier
        if (barrier.getParties() != groupSize) {
            barrier = new CyclicBarrier(groupSize);
            BARRIERS.put(key, barrier);
        }

        LOG.log(Level.FINE,
                "SynchronizingTimer [{0}]: thread waiting (groupSize={1}, timeout={2}ms)",
                new Object[]{key, groupSize, timeoutMs});

        try {
            if (timeoutMs > 0) {
                barrier.await(timeoutMs, TimeUnit.MILLISECONDS);
            } else {
                barrier.await();
            }
        } catch (TimeoutException e) {
            LOG.log(Level.WARNING,
                    "SynchronizingTimer [{0}]: timed out after {1}ms",
                    new Object[]{key, timeoutMs});
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.BrokenBarrierException e) {
            LOG.log(Level.WARNING,
                    "SynchronizingTimer [{0}]: barrier broken", key);
        }
    }

    /**
     * Creates a barrier for the given key with the specified group size.
     * Visible for testing.
     */
    static CyclicBarrier getOrCreateBarrier(String key, int groupSize) {
        return BARRIERS.computeIfAbsent(key, k -> new CyclicBarrier(groupSize));
    }

    /** Clears all barriers. Useful for test isolation. */
    static void clearBarriers() {
        BARRIERS.clear();
    }

    private static long parseLong(String value, long defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
