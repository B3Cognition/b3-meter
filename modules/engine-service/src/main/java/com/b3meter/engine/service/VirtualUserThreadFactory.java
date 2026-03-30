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
