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

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link VirtualUserThreadFactory}.
 *
 * Verifies:
 * - Created threads are virtual (isVirtual() == true)
 * - Thread naming follows the vu-{runId}-N pattern starting at 0
 * - Counter increments correctly for multiple threads
 * - Null runId is rejected at construction time
 * - Null runnable is rejected at thread-creation time
 */
class VirtualUserThreadFactoryTest {

    // -------------------------------------------------------------------------
    // Thread creation — virtual thread verification
    // -------------------------------------------------------------------------

    @Test
    void createdThreadIsVirtual() throws InterruptedException {
        VirtualUserThreadFactory factory = new VirtualUserThreadFactory("run-001");
        AtomicReference<Boolean> isVirtual = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        factory.newThread(() -> {
            isVirtual.set(Thread.currentThread().isVirtual());
            done.countDown();
        });

        done.await();
        assertTrue(isVirtual.get(), "Created thread must be a virtual thread");
    }

    // -------------------------------------------------------------------------
    // Thread naming
    // -------------------------------------------------------------------------

    @Test
    void firstThreadIsNamedWithIndexZero() throws InterruptedException {
        VirtualUserThreadFactory factory = new VirtualUserThreadFactory("run-abc");
        AtomicReference<String> name = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        factory.newThread(() -> {
            name.set(Thread.currentThread().getName());
            done.countDown();
        });

        done.await();
        assertEquals("vu-run-abc-0", name.get());
    }

    @Test
    void secondThreadHasIndexOne() throws InterruptedException {
        VirtualUserThreadFactory factory = new VirtualUserThreadFactory("run-xyz");
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<String> firstName = new AtomicReference<>();
        AtomicReference<String> secondName = new AtomicReference<>();

        factory.newThread(() -> {
            firstName.set(Thread.currentThread().getName());
            done.countDown();
        });
        factory.newThread(() -> {
            secondName.set(Thread.currentThread().getName());
            done.countDown();
        });

        done.await();
        assertEquals("vu-run-xyz-0", firstName.get());
        assertEquals("vu-run-xyz-1", secondName.get());
    }

    // -------------------------------------------------------------------------
    // Counter
    // -------------------------------------------------------------------------

    @Test
    void counterStartsAtZeroBeforeAnyThread() {
        VirtualUserThreadFactory factory = new VirtualUserThreadFactory("run-count");
        assertEquals(0, factory.createdCount());
    }

    @Test
    void counterReflectsNumberOfCreatedThreads() throws InterruptedException {
        VirtualUserThreadFactory factory = new VirtualUserThreadFactory("run-count");
        int threadCount = 5;
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            factory.newThread(done::countDown);
        }

        done.await();
        assertEquals(threadCount, factory.createdCount());
    }

    // -------------------------------------------------------------------------
    // Null guards
    // -------------------------------------------------------------------------

    @Test
    void nullRunIdIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new VirtualUserThreadFactory(null),
                "Constructor must reject null runId");
    }

    @Test
    void nullRunnableIsRejected() {
        VirtualUserThreadFactory factory = new VirtualUserThreadFactory("run-null");
        assertThrows(NullPointerException.class,
                () -> factory.newThread(null),
                "newThread must reject null runnable");
    }
}
