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

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fast unit-level test for {@link EngineThroughputBenchmark}.
 *
 * <p>The full 60-second benchmark is not run in the test suite; instead we use a
 * minimal custom task and a very short measurement window via the package-private
 * constructor, verifying that the benchmark produces a positive throughput value.
 */
class EngineThroughputBenchmarkTest {

    /**
     * Verifies that the benchmark harness produces a positive throughput value
     * when driven by a fast in-process task.
     *
     * <p>Timeout is set generously to avoid flakiness on slow CI agents while
     * still guarding against deadlocks.
     */
    @Test
    @Timeout(120)
    void benchmarkProducesPositiveThroughput() {
        AtomicLong callCount = new AtomicLong();
        var benchmark = new EngineThroughputBenchmark(callCount::incrementAndGet);

        BenchmarkResult result = benchmark.run();

        assertNotNull(result);
        assertEquals("engine_throughput", result.getName());
        assertEquals("samples/sec", result.getUnit());
        assertTrue(result.getValue() > 0,
                "Expected positive throughput, got: " + result.getValue());
    }

    @Test
    void constantsHaveExpectedValues() {
        assertEquals(100,  EngineThroughputBenchmark.VIRTUAL_USERS);
        assertEquals(5,    EngineThroughputBenchmark.WARMUP_SECONDS);
        assertEquals(60,   EngineThroughputBenchmark.MEASURE_SECONDS);
    }
}
