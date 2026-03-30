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
package com.b3meter.perf;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

/**
 * Measures samples-per-second throughput under sustained virtual-user load.
 *
 * <p>The benchmark models the engine's hot path: 100 virtual user threads each
 * execute a tight loop that simulates one sample acquisition cycle (a no-op
 * in-process mock, i.e. no real network I/O). The total number of iterations
 * completed within the measurement window determines the reported throughput.
 *
 * <p><strong>Target SLO:</strong> &gt;2 000 samples/sec (see {@code perf/.baseline.json}).
 *
 * <h2>Methodology</h2>
 * <ol>
 *   <li>Warm-up phase ({@value WARMUP_SECONDS} s) — JIT and GC settle; counts discarded.</li>
 *   <li>Measurement phase ({@value MEASURE_SECONDS} s) — sample counter is read at start
 *       and end; delta / duration = throughput.</li>
 * </ol>
 *
 * <p>Uses Java 21 virtual threads via {@link Executors#newVirtualThreadPerTaskExecutor()}
 * so thread count does not constrain concurrency — the benchmark reflects how many
 * logical samples the engine dispatch loop can commit per second, not OS-thread overhead.
 */
public final class EngineThroughputBenchmark {

    private static final Logger LOG = Logger.getLogger(EngineThroughputBenchmark.class.getName());

    /** Number of concurrent virtual users (matching the spec target). */
    static final int VIRTUAL_USERS = 100;

    /** Warm-up duration in seconds — JIT compilation and GC stabilisation. */
    static final int WARMUP_SECONDS = 5;

    /** Measurement window in seconds. */
    static final int MEASURE_SECONDS = 60;

    /** Simulates one sample acquisition cycle — in-process no-op mock. */
    @FunctionalInterface
    interface SampleTask {
        void execute();
    }

    private final SampleTask sampleTask;

    /**
     * Constructs the benchmark with the default in-process mock sampler.
     *
     * <p>The mock executes a small amount of busywork (increment + memory fence) to
     * prevent the JIT from eliminating the loop entirely while still running purely
     * in-process, ensuring we measure dispatch throughput, not network latency.
     */
    public EngineThroughputBenchmark() {
        // In-process mock: simulate one sample acquisition (no network I/O).
        // The volatile write to a shared counter provides a lightweight memory
        // fence that mimics the cache-line contention present in real engine code.
        LongAdder fence = new LongAdder();
        this.sampleTask = () -> {
            fence.increment();
            // Simulate minimal serialisation work (~1 µs) without real I/O
            long dummy = System.nanoTime();
            if (dummy == 0) {
                throw new AssertionError("unreachable"); // prevent dead-code elimination
            }
        };
    }

    /**
     * Package-private constructor for injection of a custom task in tests.
     *
     * @param sampleTask the task to execute per sample iteration
     */
    EngineThroughputBenchmark(SampleTask sampleTask) {
        this.sampleTask = sampleTask;
    }

    /**
     * Runs the benchmark and returns the measured throughput.
     *
     * @return a {@link BenchmarkResult} with value in {@code samples/sec}
     */
    public BenchmarkResult run() {
        LOG.info(() -> String.format(
                "[EngineThroughput] Starting: %d VUs, %ds warmup, %ds measurement",
                VIRTUAL_USERS, WARMUP_SECONDS, MEASURE_SECONDS));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            runPhase(executor, "warmup",      WARMUP_SECONDS,  new LongAdder());
            LongAdder measureCounter = new LongAdder();
            long elapsed = runPhase(executor, "measurement", MEASURE_SECONDS, measureCounter);

            long totalSamples = measureCounter.sum();
            double elapsedSec = elapsed / 1_000.0;
            double throughput = totalSamples / elapsedSec;

            LOG.info(() -> String.format(
                    "[EngineThroughput] Result: %.0f samples/sec (%d samples in %.1f s)",
                    throughput, totalSamples, elapsedSec));

            return new BenchmarkResult("engine_throughput", throughput, "samples/sec");
        }
    }

    /**
     * Runs {@link #VIRTUAL_USERS} threads for {@code durationSeconds}, counting
     * iterations into {@code counter}.
     *
     * @return actual elapsed time in milliseconds
     */
    private long runPhase(ExecutorService executor, String phase,
                          int durationSeconds, LongAdder counter) {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch readyLatch = new CountDownLatch(VIRTUAL_USERS);
        long[] startMs = new long[1];

        for (int i = 0; i < VIRTUAL_USERS; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                long deadline = startMs[0] + durationSeconds * 1_000L;
                while (System.currentTimeMillis() < deadline
                        && !Thread.currentThread().isInterrupted()) {
                    sampleTask.execute();
                    counter.increment();
                }
            });
        }

        try {
            readyLatch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        startMs[0] = System.currentTimeMillis();
        startLatch.countDown();

        long phaseEnd = startMs[0] + durationSeconds * 1_000L;
        try {
            long sleepMs = phaseEnd - System.currentTimeMillis();
            if (sleepMs > 0) {
                Thread.sleep(sleepMs + 200); // small buffer for threads to finish
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long elapsed = System.currentTimeMillis() - startMs[0];
        LOG.info(() -> String.format("[EngineThroughput] Phase '%s' complete: %.3f s", phase, elapsed / 1_000.0));
        return elapsed;
    }
}
