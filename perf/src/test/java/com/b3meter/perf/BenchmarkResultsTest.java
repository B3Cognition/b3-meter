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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkResultsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void addAndAllRoundTrip() {
        var results = new BenchmarkResults();
        results.add("foo", new BenchmarkResult("foo", 42.0, "ops"));
        results.add("bar", new BenchmarkResult("bar", 7.0,  "ms"));

        assertEquals(2, results.all().size());
        assertEquals(42.0, results.all().get("foo").getValue(), 0.001);
    }

    @Test
    void saveWritesValidJson(@TempDir Path tempDir) throws Exception {
        var results = new BenchmarkResults();
        results.add("engine_throughput", new BenchmarkResult("engine_throughput", 2200.0, "samples/sec"));

        String path = tempDir.resolve("results.json").toString();
        results.save(path);

        JsonNode root = MAPPER.readTree(new File(path));
        assertTrue(root.has("timestamp"), "results.json must have timestamp field");
        assertTrue(root.has("results"),   "results.json must have results field");

        JsonNode et = root.path("results").path("engine_throughput");
        assertEquals(2200.0, et.path("value").asDouble(), 0.001);
        assertEquals("samples/sec", et.path("unit").asText());
    }

    @Test
    void compareBaselinePassesWhenResultsMeetSlo(@TempDir Path tempDir) throws Exception {
        // Write baseline
        String baselineJson = """
            {
              "engine_throughput":    { "target": 2000, "tolerance_pct": 5 },
              "virtual_thread_scale": { "max_heap_gb": 4 },
              "api_latency_p99_ms":   { "target": 200, "tolerance_pct": 10 }
            }
            """;
        File baselineFile = tempDir.resolve(".baseline.json").toFile();
        baselineFile.getParentFile().mkdirs();
        MAPPER.readTree(baselineJson); // validate JSON before writing
        java.nio.file.Files.writeString(baselineFile.toPath(), baselineJson);

        var results = new BenchmarkResults();
        results.add("engine_throughput",    new BenchmarkResult("engine_throughput",    2100.0, "samples/sec"));
        results.add("virtual_thread_scale", new BenchmarkResult("virtual_thread_scale", 2.5,   "GB"));
        results.add("api_latency_p99_ms",   new BenchmarkResult("api_latency_p99_ms",   150.0, "ms"));

        // Should not throw or call System.exit — all results are above/below their targets
        assertDoesNotThrow(() -> results.compareBaseline(baselineFile.toString()));
    }

    @Test
    void compareBaselineSkipsWhenFileAbsent(@TempDir Path tempDir) {
        var results = new BenchmarkResults();
        results.add("x", new BenchmarkResult("x", 1.0, "u"));

        // Non-existent baseline path — should silently skip, not throw
        assertDoesNotThrow(() ->
                results.compareBaseline(tempDir.resolve("nonexistent.json").toString()));
    }
}
