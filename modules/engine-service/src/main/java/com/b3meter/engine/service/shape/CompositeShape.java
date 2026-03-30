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
package com.b3meter.engine.service.shape;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * A load shape that chains multiple shapes sequentially.
 *
 * <p>Runs {@code shapes[0]} for {@code durations[0]}, then {@code shapes[1]} for
 * {@code durations[1]}, and so on. Each inner shape receives elapsed time relative
 * to its own start (i.e., reset to zero at the boundary). Returns {@code null}
 * after all shapes have completed their durations.
 *
 * <p>If an inner shape returns {@code null} before its allocated duration, the
 * composite advances to the next shape immediately.
 *
 * <p>Example: ramp up, then hold steady:
 * <pre>{@code
 * new CompositeShape(
 *     List.of(
 *         new RampShape(0, 100, Duration.ofMinutes(2)),
 *         new ConstantShape(100, Duration.ofMinutes(5))
 *     ),
 *     List.of(Duration.ofMinutes(2), Duration.ofMinutes(5))
 * )
 * }</pre>
 */
public final class CompositeShape implements LoadShape {

    private final List<LoadShape> shapes;
    private final List<Duration> durations;
    private final long[] cumulativeMillis;

    /**
     * Constructs a composite shape.
     *
     * @param shapes    the ordered list of shapes to chain; must not be {@code null} or empty
     * @param durations the duration to allocate to each shape; must match {@code shapes} in size
     * @throws IllegalArgumentException if lists are empty or different sizes
     * @throws NullPointerException     if either list is {@code null}
     */
    public CompositeShape(List<LoadShape> shapes, List<Duration> durations) {
        Objects.requireNonNull(shapes, "shapes must not be null");
        Objects.requireNonNull(durations, "durations must not be null");
        if (shapes.isEmpty()) throw new IllegalArgumentException("shapes must not be empty");
        if (shapes.size() != durations.size()) {
            throw new IllegalArgumentException("shapes and durations must have the same size");
        }
        this.shapes = List.copyOf(shapes);
        this.durations = List.copyOf(durations);

        this.cumulativeMillis = new long[this.shapes.size()];
        long cumulative = 0;
        for (int i = 0; i < this.durations.size(); i++) {
            Duration d = this.durations.get(i);
            Objects.requireNonNull(d, "duration at index " + i + " must not be null");
            if (d.isNegative() || d.isZero()) {
                throw new IllegalArgumentException("duration at index " + i + " must be positive");
            }
            cumulative += d.toMillis();
            cumulativeMillis[i] = cumulative;
        }
    }

    /**
     * Delegates to the active inner shape based on elapsed time, or returns
     * {@code null} after all shapes have completed.
     *
     * @param elapsed      time since the composite test start
     * @param currentUsers currently active virtual user count
     * @return shape tick from the active inner shape, or {@code null} when all complete
     */
    @Override
    public ShapeTick tick(Duration elapsed, int currentUsers) {
        long elapsedMillis = elapsed.toMillis();
        long segmentStart = 0;

        for (int i = 0; i < shapes.size(); i++) {
            if (elapsedMillis <= cumulativeMillis[i]) {
                Duration innerElapsed = Duration.ofMillis(elapsedMillis - segmentStart);
                return shapes.get(i).tick(innerElapsed, currentUsers);
            }
            segmentStart = cumulativeMillis[i];
        }
        return null;
    }
}
