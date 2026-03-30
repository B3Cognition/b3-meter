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

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkResultTest {

    @Test
    void constructorStoresFields() {
        var result = new BenchmarkResult("engine_throughput", 2150.0, "samples/sec");
        assertEquals("engine_throughput", result.getName());
        assertEquals(2150.0, result.getValue(), 0.001);
        assertEquals("samples/sec", result.getUnit());
    }

    @Test
    void nullNameThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new BenchmarkResult(null, 1.0, "ms"));
    }

    @Test
    void blankNameThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new BenchmarkResult("  ", 1.0, "ms"));
    }

    @Test
    void nullUnitBecomesEmptyString() {
        var result = new BenchmarkResult("x", 1.0, null);
        assertEquals("", result.getUnit());
    }

    @Test
    void toStringContainsNameAndValue() {
        var result = new BenchmarkResult("api_latency_p99_ms", 87.3, "ms");
        String s = result.toString();
        assertTrue(s.contains("api_latency_p99_ms"), "toString should contain name");
        assertTrue(s.contains("87.3"),               "toString should contain value");
    }
}
