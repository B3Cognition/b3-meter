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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link VirtualUserExecutor}.
 *
 * Verifies:
 * - 100 virtual users all complete successfully
 * - Active user count is accurate during execution
 * - Graceful shutdown waits for completion
 * - Force shutdown interrupts running users
 * - Submit after shutdown throws IllegalStateException
 * - Tasks run on virtual threads (isVirtual() == true)
 * - ReentrantLock is used instead of synchronized (code inspection)
 */
class VirtualUserExecutorTest {

    // -------------------------------------------------------------------------
    // Minimal UIBridge stub
    // -------------------------------------------------------------------------

    private static final UIBridge NO_OP = new UIBridge() {
        @Override public void onTestStarted(TestRunContext c) {}
        @Override public void onSample(TestRunContext c, double s, double e) {}
        @Override public void onTestEnded(TestRunContext c) {}
        @Override public void onThreadStarted(TestRunContext c, String t, int n) {}
        @Override public void onThreadFinished(TestRunContext c, String t, int n) {}
        @Override public void reportError(String m, String t) {}
        @Override public void reportInfo(String m, String t) {}
        @Override public void refreshUI() {}
        @Override public String promptPassword(String m) { return null; }
        @Override public void onSampleReceived(TestRunContext c, String l, long e, boolean s) {}
    };

    private TestRunContext context;
    private VirtualUserExecutor executor;

    @BeforeEach
    void setUp() {
        context = TestRunContext.builder()
                .runId("test-run")
                .planPath("/plans/test.jmx")
                .uiBridge(NO_OP)
                .build();
        executor = new VirtualUserExecutor(context);
    }

    @AfterEach
    void tearDown() {
        executor.close();
    }

    // -------------------------------------------------------------------------
    // 100 virtual users all complete
    // -------------------------------------------------------------------------

    @Test
    void hundredVirtualUsersAllComplete() throws Exception {
        int userCount = 100;
        CountDownLatch allDone = new CountDownLatch(userCount);

        for (int i = 0; i < userCount; i++) {
            executor.submitVirtualUser(allDone::countDown);
        }

        boolean completed = allDone.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "All 100 virtual users must complete within 10 seconds");
    }

    // -------------------------------------------------------------------------
    // Active user count accuracy
    // -------------------------------------------------------------------------

    @Test
    void activeUserCountAccurateDuringExecution() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger countWhileRunning = new AtomicInteger(-1);

        executor.submitVirtualUser(() -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        started.await();
        countWhileRunning.set(executor.activeUserCount());
        release.countDown();

        assertEquals(1, countWhileRunning.get(), "Active count must be 1 while user is running");
    }

    @Test
    void activeUserCountDropsToZeroAfterCompletion() throws Exception {
        CountDownLatch done = new CountDownLatch(1);

        executor.submitVirtualUser(done::countDown);

        done.await(5, TimeUnit.SECONDS);
        // Give a moment for the finally block to decrement
        long deadline = System.currentTimeMillis() + 2000;
        while (executor.activeUserCount() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }

        assertEquals(0, executor.activeUserCount(), "Active count must drop to 0 after completion");
    }

    // -------------------------------------------------------------------------
    // Graceful shutdown
    // -------------------------------------------------------------------------

    @Test
    void gracefulShutdownWaitsForCompletion() throws Exception {
        int userCount = 10;
        CountDownLatch allDone = new CountDownLatch(userCount);

        for (int i = 0; i < userCount; i++) {
            executor.submitVirtualUser(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    allDone.countDown();
                }
            });
        }

        executor.shutdownGracefully(5, TimeUnit.SECONDS);

        assertEquals(0, allDone.getCount(), "All users must complete before graceful shutdown returns");
    }

    // -------------------------------------------------------------------------
    // Force shutdown
    // -------------------------------------------------------------------------

    @Test
    void shutdownNowInterruptsRunningUsers() throws Exception {
        AtomicBoolean interrupted = new AtomicBoolean(false);
        CountDownLatch started = new CountDownLatch(1);

        executor.submitVirtualUser(() -> {
            started.countDown();
            try {
                Thread.sleep(60_000); // long sleep — will be interrupted
            } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
        });

        started.await();
        executor.shutdownNow();

        // Wait briefly for interrupt to propagate
        long deadline = System.currentTimeMillis() + 2000;
        while (!interrupted.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }

        assertTrue(interrupted.get(), "shutdownNow must interrupt running users");
    }

    // -------------------------------------------------------------------------
    // Submit after shutdown
    // -------------------------------------------------------------------------

    @Test
    void submitAfterShutdownThrowsIllegalStateException() throws Exception {
        executor.shutdownGracefully(1, TimeUnit.SECONDS);

        assertThrows(IllegalStateException.class,
                () -> executor.submitVirtualUser(() -> {}),
                "Submit after shutdown must throw IllegalStateException");
    }

    @Test
    void submitAfterShutdownNowThrowsIllegalStateException() {
        executor.shutdownNow();

        assertThrows(IllegalStateException.class,
                () -> executor.submitVirtualUser(() -> {}),
                "Submit after shutdownNow must throw IllegalStateException");
    }

    // -------------------------------------------------------------------------
    // Virtual thread verification inside tasks
    // -------------------------------------------------------------------------

    @Test
    void submittedTasksRunOnVirtualThreads() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean isVirtual = new AtomicBoolean(false);

        executor.submitVirtualUser(() -> {
            isVirtual.set(Thread.currentThread().isVirtual());
            done.countDown();
        });

        done.await(5, TimeUnit.SECONDS);
        assertTrue(isVirtual.get(), "Tasks must execute on virtual threads");
    }

    // -------------------------------------------------------------------------
    // Null guard
    // -------------------------------------------------------------------------

    @Test
    void nullContextIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new VirtualUserExecutor(null),
                "Constructor must reject null context");
    }

    @Test
    void nullTaskIsRejected() {
        assertThrows(NullPointerException.class,
                () -> executor.submitVirtualUser(null),
                "submitVirtualUser must reject null task");
    }

    // -------------------------------------------------------------------------
    // AutoCloseable
    // -------------------------------------------------------------------------

    @Test
    void closeViaAutoCloseableThenSubmitThrows() throws Exception {
        VirtualUserExecutor localExecutor = new VirtualUserExecutor(context);
        localExecutor.close();

        assertThrows(IllegalStateException.class,
                () -> localExecutor.submitVirtualUser(() -> {}),
                "Submit after close() must throw IllegalStateException");
    }

    // -------------------------------------------------------------------------
    // Future return value
    // -------------------------------------------------------------------------

    @Test
    void submitReturnsFutureRepresentingTask() throws Exception {
        CountDownLatch done = new CountDownLatch(1);

        Future<?> future = executor.submitVirtualUser(done::countDown);

        assertNotNull(future, "submitVirtualUser must return a non-null Future");
        done.await(5, TimeUnit.SECONDS);
        future.get(2, TimeUnit.SECONDS); // must not throw
    }
}
