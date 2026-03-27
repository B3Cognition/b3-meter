package com.jmeternext.engine.service;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Creates virtual threads for virtual users.
 *
 * <p>Each virtual user runs on a virtual thread, enabling massive concurrency
 * without the memory overhead of platform threads.
 *
 * <p>Requires Java 21+ (virtual threads are GA since JEP-444). This project's
 * build toolchain is pinned to Java 21, so the fallback branch is unreachable
 * in production but retained for documentation purposes.
 *
 * <p>Thread naming convention: {@code vu-{runId}-{sequenceNumber}} where the
 * sequence number starts at 0 and increments atomically for each created thread.
 */
public final class VirtualUserThreadFactory implements ThreadFactory {

    private final String runId;
    private final AtomicInteger counter = new AtomicInteger(0);

    /**
     * Constructs a factory bound to the given run identifier.
     *
     * @param runId unique identifier of the test run; must not be {@code null}
     * @throws NullPointerException if {@code runId} is {@code null}
     */
    public VirtualUserThreadFactory(String runId) {
        this.runId = Objects.requireNonNull(runId, "runId must not be null");
    }

    /**
     * Creates a new virtual thread that will immediately start executing {@code r}.
     *
     * <p>The thread name follows the pattern {@code vu-{runId}-N} where N is the
     * zero-based creation index for this factory instance.
     *
     * @param r the runnable to execute; must not be {@code null}
     * @return a new, already-started virtual thread
     */
    @Override
    public Thread newThread(Runnable r) {
        Objects.requireNonNull(r, "runnable must not be null");
        return Thread.ofVirtual()
                .name("vu-" + runId + "-", counter.getAndIncrement())
                .start(r);
    }

    /**
     * Returns the total number of threads created by this factory since construction.
     *
     * @return non-negative thread count
     */
    public int createdCount() {
        return counter.get();
    }
}
