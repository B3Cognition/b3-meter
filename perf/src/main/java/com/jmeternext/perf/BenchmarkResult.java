package com.jmeternext.perf;

/**
 * Immutable value object holding the numeric outcome of a single benchmark.
 *
 * <p>Each benchmark produces one {@code BenchmarkResult}. The {@link BenchmarkRunner}
 * aggregates these into a {@link BenchmarkResults} map, serialises it to
 * {@code perf/results.json}, and compares it against the {@code .baseline.json}.
 */
public final class BenchmarkResult {

    private final String name;
    private final double value;
    private final String unit;

    /**
     * Constructs a benchmark result.
     *
     * @param name  logical name of the benchmark (e.g. {@code "engine_throughput"})
     * @param value measured numeric value
     * @param unit  unit string for human display (e.g. {@code "samples/sec"}, {@code "GB"}, {@code "ms"})
     */
    public BenchmarkResult(String name, double value, String unit) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be null or blank");
        }
        this.name  = name;
        this.value = value;
        this.unit  = unit != null ? unit : "";
    }

    /** Logical name of this benchmark. */
    public String getName() {
        return name;
    }

    /** Measured value. */
    public double getValue() {
        return value;
    }

    /** Unit of measurement. */
    public String getUnit() {
        return unit;
    }

    @Override
    public String toString() {
        return name + "=" + value + " " + unit;
    }
}
