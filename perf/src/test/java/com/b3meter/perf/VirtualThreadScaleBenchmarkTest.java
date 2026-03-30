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

/**
 * Unit tests for {@link VirtualThreadScaleBenchmark}.
 *
 * <p>The full 5 000-VU ramp is run (virtual threads are cheap), but with a
 * generous timeout to guard against CI resource limits.
 */
class VirtualThreadScaleBenchmarkTest {

    @Test
    @Timeout(120)
    void benchmarkProducesNonNegativeHeapValue() {
        var benchmark = new VirtualThreadScaleBenchmark();
        BenchmarkResult result = benchmark.run();

        assertNotNull(result);
        assertEquals("virtual_thread_scale", result.getName());
        assertEquals("GB", result.getUnit());
        assertTrue(result.getValue() >= 0,
                "Heap value must be non-negative, got: " + result.getValue());
    }

    @Test
    void sloConstantsMatchBaseline() {
        // Verify that the SLO constants in the benchmark class agree with the
        // values documented in .baseline.json (cross-check against spec).
        assertEquals(5_000, VirtualThreadScaleBenchmark.PEAK_VUS);
        assertTrue(VirtualThreadScaleBenchmark.MAX_HEAP_BYTES > 0);
        assertTrue(VirtualThreadScaleBenchmark.MAX_PLATFORM_THREADS > 0);

        // 4 GB expressed in bytes
        long fourGb = 4L * 1024 * 1024 * 1024;
        assertEquals(fourGb, VirtualThreadScaleBenchmark.MAX_HEAP_BYTES);
    }
}
