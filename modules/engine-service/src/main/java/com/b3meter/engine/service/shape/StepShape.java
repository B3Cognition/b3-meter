package com.jmeternext.engine.service.shape;

import java.time.Duration;
import java.util.Objects;

/**
 * A load shape that increases users in discrete steps at regular intervals.
 *
 * <p>Every {@code stepInterval}, the target user count increases by {@code stepSize},
 * up to {@code maxUsers}. This creates a staircase load pattern useful for
 * finding system capacity limits.
 *
 * <p>Example: add 10 users every 30 seconds, up to 200, for 10 minutes:
 * <pre>{@code
 * new StepShape(10, Duration.ofSeconds(30), 200, Duration.ofMinutes(10))
 * }</pre>
 */
public final class StepShape implements LoadShape {

    private final int stepSize;
    private final Duration stepInterval;
    private final int maxUsers;
    private final Duration totalDuration;

    /**
     * Constructs a step load shape.
     *
     * @param stepSize      number of users to add per step; must be {@code > 0}
     * @param stepInterval  time between steps; must not be {@code null}, negative, or zero
     * @param maxUsers      upper bound on user count; must be {@code > 0}
     * @param totalDuration total test duration; must not be {@code null} or negative
     * @throws IllegalArgumentException if constraints are violated
     * @throws NullPointerException     if {@code stepInterval} or {@code totalDuration} is {@code null}
     */
    public StepShape(int stepSize, Duration stepInterval, int maxUsers, Duration totalDuration) {
        if (stepSize <= 0) throw new IllegalArgumentException("stepSize must be > 0");
        Objects.requireNonNull(stepInterval, "stepInterval must not be null");
        if (stepInterval.isNegative() || stepInterval.isZero()) {
            throw new IllegalArgumentException("stepInterval must be positive");
        }
        if (maxUsers <= 0) throw new IllegalArgumentException("maxUsers must be > 0");
        Objects.requireNonNull(totalDuration, "totalDuration must not be null");
        if (totalDuration.isNegative()) {
            throw new IllegalArgumentException("totalDuration must not be negative");
        }
        this.stepSize = stepSize;
        this.stepInterval = stepInterval;
        this.maxUsers = maxUsers;
        this.totalDuration = totalDuration;
    }

    /**
     * Returns a tick with the step-computed user count, or {@code null} after
     * the total duration elapses.
     *
     * <p>The current step is determined by {@code floor(elapsed / stepInterval) + 1},
     * so the first step begins immediately at time zero.
     *
     * @param elapsed      time since test start
     * @param currentUsers currently active virtual user count (unused)
     * @return shape tick, or {@code null} when the total duration has elapsed
     */
    @Override
    public ShapeTick tick(Duration elapsed, int currentUsers) {
        if (elapsed.compareTo(totalDuration) > 0) {
            return null;
        }
        long currentStep = (elapsed.toMillis() / stepInterval.toMillis()) + 1;
        int users = (int) Math.min(currentStep * stepSize, maxUsers);
        double spawnRate = stepSize / (double) stepInterval.toSeconds();
        return new ShapeTick(users, spawnRate);
    }
}
