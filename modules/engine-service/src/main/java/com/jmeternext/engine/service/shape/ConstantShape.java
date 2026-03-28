package com.jmeternext.engine.service.shape;

import java.time.Duration;
import java.util.Objects;

/**
 * A load shape that maintains a fixed number of virtual users for a given duration.
 *
 * <p>Returns {@link ShapeTick} with the configured user count until the duration
 * elapses, then returns {@code null} to signal the test should stop.
 *
 * <p>Example: hold 100 users steady for 5 minutes:
 * <pre>{@code
 * new ConstantShape(100, Duration.ofMinutes(5))
 * }</pre>
 */
public final class ConstantShape implements LoadShape {

    private final int users;
    private final Duration duration;

    /**
     * Constructs a constant load shape.
     *
     * @param users    the fixed number of virtual users; must be {@code >= 0}
     * @param duration how long to maintain the load; must not be {@code null} or negative
     * @throws IllegalArgumentException if {@code users < 0} or {@code duration} is negative
     * @throws NullPointerException     if {@code duration} is {@code null}
     */
    public ConstantShape(int users, Duration duration) {
        if (users < 0) throw new IllegalArgumentException("users must be >= 0");
        Objects.requireNonNull(duration, "duration must not be null");
        if (duration.isNegative()) throw new IllegalArgumentException("duration must not be negative");
        this.users = users;
        this.duration = duration;
    }

    /**
     * Returns a tick with the configured user count, or {@code null} after the duration elapses.
     *
     * <p>The spawn rate is set equal to the user count so the engine can ramp up
     * to the target as quickly as possible on the first tick.
     *
     * @param elapsed      time since test start
     * @param currentUsers currently active virtual user count (unused)
     * @return shape tick, or {@code null} when the duration has elapsed
     */
    @Override
    public ShapeTick tick(Duration elapsed, int currentUsers) {
        if (elapsed.compareTo(duration) > 0) {
            return null;
        }
        return new ShapeTick(users, users);
    }
}
