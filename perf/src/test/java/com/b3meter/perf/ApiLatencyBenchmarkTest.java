package com.jmeternext.perf;

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
