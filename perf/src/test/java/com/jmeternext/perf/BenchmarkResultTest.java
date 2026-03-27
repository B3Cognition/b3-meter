package com.jmeternext.perf;

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
