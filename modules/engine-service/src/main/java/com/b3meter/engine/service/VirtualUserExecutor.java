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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages virtual user lifecycle for a test run.
 *
 * <p>Uses an {@link ExecutorService} backed by virtual threads
 * ({@link Executors#newVirtualThreadPerTaskExecutor()}) to schedule virtual users.
 * Each submitted task represents one virtual user iteration.
 *
 * <p>Uses {@link ReentrantLock} instead of {@code synchronized} blocks to avoid
 * virtual-thread pinning described in JEP-491. Platform threads that are pinned
 * inside a {@code synchronized} block cannot be unmounted from their carrier thread,
 * which defeats the scalability benefit of virtual threads. {@link ReentrantLock}
 * does not pin.
 *
 * <p>Implements {@link AutoCloseable} so it can be used in try-with-resources blocks.
 * {@link #close()} delegates to {@link #shutdownNow()}.
 */
public final class VirtualUserExecutor implements AutoCloseable {

    private final ExecutorService executor;
    private final ReentrantLock stateLock = new ReentrantLock();
    private final TestRunContext context;
    private final AtomicInteger activeUsers = new AtomicInteger(0);
    private volatile boolean shutdown = false;

    /**
     * Constructs an executor bound to the given run context.
     *
     * @param context the run context; must not be {@code null}
     * @throws NullPointerException if {@code context} is {@code null}
     */
    public VirtualUserExecutor(TestRunContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Submits a virtual-user task for execution on a virtual thread.
     *
     * <p>The task's active-user count is incremented before submission and
     * decremented in a {@code finally} block after the task completes (whether
     * normally or via exception). {@link UIBridge#onThreadFinished} is called
     * with the remaining active count after each completion.
     *
     * @param userTask the task representing one virtual user; must not be {@code null}
     * @return a {@link Future} representing the pending execution
     * @throws IllegalStateException if the executor has been shut down
     * @throws NullPointerException  if {@code userTask} is {@code null}
     */
    public Future<?> submitVirtualUser(Runnable userTask) {
        Objects.requireNonNull(userTask, "userTask must not be null");
        stateLock.lock();
        try {
            if (shutdown) {
                throw new IllegalStateException("Executor is shut down");
            }
            activeUsers.incrementAndGet();
            return executor.submit(() -> {
                try {
                    userTask.run();
                } finally {
                    int remaining = activeUsers.decrementAndGet();
                    context.getUiBridge().onThreadFinished(
                            context,
                            Thread.currentThread().getName(),
                            remaining);
                }
            });
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Returns the number of virtual users currently executing.
     *
     * @return non-negative count of active virtual users
     */
    public int activeUserCount() {
        return activeUsers.get();
    }

    /**
     * Initiates an orderly shutdown and waits up to {@code timeout} for active
     * users to complete.
     *
     * <p>No new virtual users may be submitted after this call returns.
     *
     * @param timeout maximum time to wait
     * @param unit    time unit for the {@code timeout} argument
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public void shutdownGracefully(long timeout, TimeUnit unit) throws InterruptedException {
        stateLock.lock();
        try {
            shutdown = true;
        } finally {
            stateLock.unlock();
        }
        executor.shutdown();
        executor.awaitTermination(timeout, unit);
    }

    /**
     * Attempts to stop all actively executing virtual users immediately.
     *
     * <p>No new virtual users may be submitted after this call returns.
     * Already-running tasks receive an interrupt signal.
     */
    public void shutdownNow() {
        stateLock.lock();
        try {
            shutdown = true;
        } finally {
            stateLock.unlock();
        }
        executor.shutdownNow();
    }

    /**
     * Closes this executor by calling {@link #shutdownNow()}.
     *
     * <p>Allows use in try-with-resources blocks.
     */
    @Override
    public void close() {
        shutdownNow();
    }
}
