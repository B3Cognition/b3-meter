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
