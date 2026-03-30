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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable accumulator for {@link BenchmarkResult} instances produced during a
 * benchmark run.
 *
 * <p>After all benchmarks complete, call {@link #save(String)} to write
 * {@code results.json}, then {@link #compareBaseline(String)} to evaluate
 * the run against the checked-in {@code .baseline.json} targets.
 *
 * <p>Baseline JSON structure (one entry per benchmark key):
 * <pre>
 * {
 *   "engine_throughput":       { "target": 2000, "tolerance_pct": 5 },
 *   "virtual_thread_scale":    { "max_heap_gb": 4, "max_platform_threads": 200 },
 *   "api_latency_p99_ms":      { "target": 200, "tolerance_pct": 10 }
 * }
 * </pre>
 *
 * <p>Results JSON structure:
 * <pre>
 * {
 *   "timestamp": "2026-03-25T10:00:00Z",
 *   "results": {
 *     "engine_throughput":       { "value": 2150.0, "unit": "samples/sec" },
 *     "virtual_thread_scale":    { "value": 3.1,    "unit": "GB" },
 *     "api_latency_p99_ms":      { "value": 87.0,   "unit": "ms" }
 *   }
 * }
 * </pre>
 */
public final class BenchmarkResults {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /** Insertion-ordered map from benchmark name to result. */
    private final Map<String, BenchmarkResult> results = new LinkedHashMap<>();

    /** Adds (or replaces) a result entry. */
    public void add(String name, BenchmarkResult result) {
        results.put(name, result);
    }

    /** Returns an unmodifiable view of all collected results. */
    public Map<String, BenchmarkResult> all() {
        return Collections.unmodifiableMap(results);
    }

    /**
     * Serialises the results to {@code path} as JSON.
     *
     * @param path target file path (e.g. {@code "perf/results.json"})
     * @throws RuntimeException wrapping any {@link IOException}
     */
    public void save(String path) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("timestamp", Instant.now().toString());

        ObjectNode resultsNode = root.putObject("results");
        results.forEach((name, r) -> {
            ObjectNode entry = resultsNode.putObject(name);
            entry.put("value", r.getValue());
            entry.put("unit",  r.getUnit());
        });

        try {
            MAPPER.writeValue(new File(path), root);
            System.out.println("[BenchmarkResults] Results written to: " + path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write results to: " + path, e);
        }
    }

    /**
     * Reads {@code baselinePath} and compares each result against its SLO target.
     *
     * <p>Violations are printed to stdout and collected. If any violation is found
     * this method calls {@link System#exit(int)} with code {@code 1} so that CI
     * fails the build.
     *
     * @param baselinePath path to the {@code .baseline.json} file
     */
    public void compareBaseline(String baselinePath) {
        File baselineFile = new File(baselinePath);
        if (!baselineFile.exists()) {
            System.out.println("[BenchmarkResults] No baseline found at: " + baselinePath + " — skipping comparison");
            return;
        }

        JsonNode baseline;
        try {
            baseline = MAPPER.readTree(baselineFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read baseline from: " + baselinePath, e);
        }

        List<String> violations = new ArrayList<>();

        // engine_throughput — value must be >= target * (1 - tolerance_pct/100)
        checkMinThroughput(baseline, "engine_throughput", violations);

        // virtual_thread_scale — value (heap in GB) must be <= max_heap_gb
        checkMaxHeap(baseline, "virtual_thread_scale", violations);

        // api_latency_p99_ms — value must be <= target * (1 + tolerance_pct/100)
        checkMaxLatency(baseline, "api_latency_p99_ms", violations);

        if (violations.isEmpty()) {
            System.out.println("[BenchmarkResults] All SLO checks PASSED.");
        } else {
            System.err.println("[BenchmarkResults] SLO VIOLATIONS DETECTED:");
            violations.forEach(v -> System.err.println("  FAIL: " + v));
            System.exit(1);
        }
    }

    // -------------------------------------------------------------------------
    // Private SLO check helpers
    // -------------------------------------------------------------------------

    private void checkMinThroughput(JsonNode baseline, String key, List<String> violations) {
        BenchmarkResult result = results.get(key);
        JsonNode node = baseline.get(key);
        if (result == null || node == null) return;

        double target       = node.path("target").asDouble();
        double tolerancePct = node.path("tolerance_pct").asDouble();
        double floor        = target * (1.0 - tolerancePct / 100.0);

        if (result.getValue() < floor) {
            violations.add(String.format(
                    "%s: measured %.1f %s < floor %.1f (target=%.0f, tolerance=%.0f%%)",
                    key, result.getValue(), result.getUnit(), floor, target, tolerancePct));
        } else {
            System.out.printf("[BenchmarkResults] PASS %s: %.1f %s >= floor %.1f%n",
                    key, result.getValue(), result.getUnit(), floor);
        }
    }

    private void checkMaxHeap(JsonNode baseline, String key, List<String> violations) {
        BenchmarkResult result = results.get(key);
        JsonNode node = baseline.get(key);
        if (result == null || node == null) return;

        double maxHeapGb = node.path("max_heap_gb").asDouble();

        if (result.getValue() > maxHeapGb) {
            violations.add(String.format(
                    "%s: measured %.2f %s > max_heap_gb %.1f",
                    key, result.getValue(), result.getUnit(), maxHeapGb));
        } else {
            System.out.printf("[BenchmarkResults] PASS %s: %.2f %s <= max %.1f GB%n",
                    key, result.getValue(), result.getUnit(), maxHeapGb);
        }
    }

    private void checkMaxLatency(JsonNode baseline, String key, List<String> violations) {
        BenchmarkResult result = results.get(key);
        JsonNode node = baseline.get(key);
        if (result == null || node == null) return;

        double target       = node.path("target").asDouble();
        double tolerancePct = node.path("tolerance_pct").asDouble();
        double ceiling      = target * (1.0 + tolerancePct / 100.0);

        if (result.getValue() > ceiling) {
            violations.add(String.format(
                    "%s: measured %.1f %s > ceiling %.1f (target=%.0f, tolerance=%.0f%%)",
                    key, result.getValue(), result.getUnit(), ceiling, target, tolerancePct));
        } else {
            System.out.printf("[BenchmarkResults] PASS %s: %.1f %s <= ceiling %.1f%n",
                    key, result.getValue(), result.getUnit(), ceiling);
        }
    }
}
