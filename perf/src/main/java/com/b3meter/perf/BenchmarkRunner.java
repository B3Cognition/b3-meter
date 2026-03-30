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

import java.util.logging.Logger;

/**
 * Entry point for the b3meter performance regression suite.
 *
 * <p>Runs all benchmark tasks sequentially, writes {@code perf/results.json},
 * and compares the results against the checked-in {@code perf/.baseline.json}.
 * If any SLO target is violated the process exits with code {@code 1} so that
 * the CI build fails.
 *
 * <h2>Usage</h2>
 * <pre>
 *   java -jar perf/benchmark-runner.jar
 * </pre>
 *
 * <p>The working directory must be the repository root when the JAR is invoked
 * directly. In CI the {@code benchmark.yml} workflow step {@code java -jar
 * perf/benchmark-runner.jar} satisfies this requirement because the workflow
 * runs from the checkout root.
 *
 * <h2>Benchmarks executed</h2>
 * <ol>
 *   <li>{@link EngineThroughputBenchmark} — 100 VUs × 60 s, target &gt;2 000 samples/sec</li>
 *   <li>{@link VirtualThreadScaleBenchmark} — ramp 100→5 000 VUs, target &lt;4 GB heap</li>
 *   <li>{@link ApiLatencyBenchmark} — 1 000 in-process requests, target p99 &le;200 ms</li>
 * </ol>
 */
public final class BenchmarkRunner {

    private static final Logger LOG = Logger.getLogger(BenchmarkRunner.class.getName());

    /** Path where results are written. Relative to repository root. */
    static final String RESULTS_PATH  = "perf/results.json";

    /** Path to the checked-in baseline. Relative to repository root. */
    static final String BASELINE_PATH = "perf/.baseline.json";

    private BenchmarkRunner() {
        // utility class — no instances
    }

    /**
     * Runs all benchmarks, saves results, and compares against baseline.
     *
     * @param args not used; reserved for future flags (e.g. {@code --output})
     */
    public static void main(String[] args) {
        LOG.info("[BenchmarkRunner] Starting b3meter performance regression suite");

        BenchmarkResults results = new BenchmarkResults();

        results.add("engine_throughput",    runEngineBenchmark());
        results.add("virtual_thread_scale", runVirtualThreadBenchmark());
        results.add("api_latency_p99_ms",   runApiLatencyBenchmark());

        results.save(RESULTS_PATH);
        results.compareBaseline(BASELINE_PATH);

        LOG.info("[BenchmarkRunner] Suite complete");
    }

    // -------------------------------------------------------------------------
    // Individual benchmark launchers (package-private for testability)
    // -------------------------------------------------------------------------

    static BenchmarkResult runEngineBenchmark() {
        LOG.info("[BenchmarkRunner] Running EngineThroughputBenchmark ...");
        return new EngineThroughputBenchmark().run();
    }

    static BenchmarkResult runVirtualThreadBenchmark() {
        LOG.info("[BenchmarkRunner] Running VirtualThreadScaleBenchmark ...");
        return new VirtualThreadScaleBenchmark().run();
    }

    static BenchmarkResult runApiLatencyBenchmark() {
        LOG.info("[BenchmarkRunner] Running ApiLatencyBenchmark ...");
        return new ApiLatencyBenchmark().run();
    }
}
