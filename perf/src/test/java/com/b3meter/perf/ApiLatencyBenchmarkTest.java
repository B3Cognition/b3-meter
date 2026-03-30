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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

class ApiLatencyBenchmarkTest {

    @Test
    @Timeout(30)
    void benchmarkProducesNonNegativeP99() {
        var benchmark = new ApiLatencyBenchmark(() -> {
            // Fast mock: return 1 µs
            return 1_000L;
        });

        BenchmarkResult result = benchmark.run();

        assertNotNull(result);
        assertEquals("api_latency_p99_ms", result.getName());
        assertEquals("ms", result.getUnit());
        assertTrue(result.getValue() >= 0,
                "p99 latency must be non-negative, got: " + result.getValue());
    }

    @Test
    @Timeout(30)
    void benchmarkWithDefaultHandlerProducesResult() {
        // Smoke-test the default (non-injected) constructor path
        var benchmark = new ApiLatencyBenchmark();
        BenchmarkResult result = benchmark.run();

        assertNotNull(result);
        assertTrue(result.getValue() >= 0);
    }

    @Test
    void sampleCountConstant() {
        assertEquals(1_000, ApiLatencyBenchmark.SAMPLE_COUNT);
    }
}
