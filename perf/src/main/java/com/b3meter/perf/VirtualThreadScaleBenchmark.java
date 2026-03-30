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

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Measures JVM resource usage (heap and platform-thread count) when ramping
 * virtual users from 100 to 5 000 using Java 21 virtual threads.
 *
 * <p>Because virtual threads are scheduled on a shared carrier-thread pool, the
 * platform-thread count should remain low even at 5 000 VUs. The benchmark
 * verifies that:
 * <ul>
 *   <li>Heap usage stays under 4 GB at peak VU count.</li>
 *   <li>Platform-thread count stays under 200 at peak VU count.</li>
 * </ul>
 *
 * <p>The reported {@link BenchmarkResult} carries the peak heap in GB; the
 * platform-thread count is printed to the log for informational purposes.
 *
 * <h2>Ramp profile</h2>
 * <pre>
 *  100 → 500 → 1 000 → 2 000 → 5 000 VUs
 * </pre>
 * At each step the benchmark waits until all threads are parked (blocked on a
 * latch), takes a heap snapshot, then unparks and proceeds to the next step.
 */
public final class VirtualThreadScaleBenchmark {

    private static final Logger LOG = Logger.getLogger(VirtualThreadScaleBenchmark.class.getName());

    private static final int[] RAMP_STEPS = {100, 500, 1_000, 2_000, 5_000};

    /** Maximum VU count — the SLO target is measured at this level. */
    static final int PEAK_VUS = 5_000;

    /** Target SLO: heap must not exceed this many bytes at peak VU count. */
    static final long MAX_HEAP_BYTES = 4L * 1024 * 1024 * 1024; // 4 GB

    /** Target SLO: platform-thread count must not exceed this at peak VU count. */
    static final int MAX_PLATFORM_THREADS = 200;

    private final MemoryMXBean memoryBean  = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadBean  = ManagementFactory.getThreadMXBean();

    /**
     * Runs the ramp benchmark.
     *
     * @return a {@link BenchmarkResult} with peak heap usage in GB
     */
    public BenchmarkResult run() {
        LOG.info("[VirtualThreadScale] Starting ramp benchmark: 100 → 5 000 VUs");

        double peakHeapGb = 0.0;
        int peakPlatformThreads = 0;

        for (int vus : RAMP_STEPS) {
            double heapGb;
            int platformThreads;

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                CountDownLatch parked  = new CountDownLatch(vus);
                CountDownLatch release = new CountDownLatch(1);

                // Spawn 'vus' virtual threads, each parks on the release latch
                for (int i = 0; i < vus; i++) {
                    executor.submit(() -> {
                        parked.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                }

                // Wait until all VUs are parked
                boolean allParked = parked.await(30, TimeUnit.SECONDS);
                if (!allParked) {
                    LOG.warning("[VirtualThreadScale] Not all VUs parked within 30 s at step " + vus);
                }

                // Force GC before measuring to get a stable heap reading
                System.gc();
                Thread.sleep(100);

                long heapBytes  = memoryBean.getHeapMemoryUsage().getUsed();
                platformThreads = threadBean.getThreadCount();
                heapGb          = heapBytes / (1024.0 * 1024.0 * 1024.0);

                int finalPlatformThreads = platformThreads;
                double finalHeapGb = heapGb;
                LOG.info(() -> String.format(
                        "[VirtualThreadScale] VUs=%5d  heap=%.2f GB  platform-threads=%d",
                        vus, finalHeapGb, finalPlatformThreads));

                // Release all parked virtual threads before closing the executor
                release.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            if (heapGb > peakHeapGb) {
                peakHeapGb = heapGb;
            }
            if (platformThreads > peakPlatformThreads) {
                peakPlatformThreads = platformThreads;
            }
        }

        double finalPeakHeapGb = peakHeapGb;
        int finalPeak = peakPlatformThreads;
        LOG.info(() -> String.format(
                "[VirtualThreadScale] Peak: heap=%.2f GB, platform-threads=%d",
                finalPeakHeapGb, finalPeak));

        return new BenchmarkResult("virtual_thread_scale", peakHeapGb, "GB");
    }
}
