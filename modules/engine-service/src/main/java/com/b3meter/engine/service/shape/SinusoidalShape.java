package com.jmeternext.engine.service.shape;

import java.time.Duration;
import java.util.Objects;

/**
 * A load shape that varies the user count along a sinusoidal wave.
 *
 * <p>The user count oscillates between {@code minUsers} and {@code maxUsers}
 * with the given period:
 * {@code users = mid + amplitude * sin(2 * PI * elapsed / period)},
 * where {@code mid = (min + max) / 2} and {@code amplitude = (max - min) / 2}.
 *
 * <p>Useful for testing system behaviour under periodically varying load,
 * such as simulating daily traffic patterns.
 *
 * <p>Example: oscillate between 10 and 100 users with a 60-second period for 10 minutes:
 * <pre>{@code
 * new SinusoidalShape(10, 100, Duration.ofSeconds(60), Duration.ofMinutes(10))
 * }</pre>
 */
public final class SinusoidalShape implements LoadShape {

    private final double mid;
    private final double amplitude;
    private final Duration period;
    private final Duration totalDuration;

    /**
     * Constructs a sinusoidal load shape.
     *
     * @param minUsers      minimum user count; must be {@code >= 0}
     * @param maxUsers      maximum user count; must be {@code >= minUsers}
     * @param period        the wave period; must not be {@code null}, negative, or zero
     * @param totalDuration how long to run; must not be {@code null} or negative
     * @throws IllegalArgumentException if constraints are violated
     * @throws NullPointerException     if {@code period} or {@code totalDuration} is {@code null}
     */
    public SinusoidalShape(int minUsers, int maxUsers, Duration period, Duration totalDuration) {
        if (minUsers < 0) throw new IllegalArgumentException("minUsers must be >= 0");
        if (maxUsers < minUsers) throw new IllegalArgumentException("maxUsers must be >= minUsers");
        Objects.requireNonNull(period, "period must not be null");
        if (period.isNegative() || period.isZero()) {
            throw new IllegalArgumentException("period must be positive");
        }
        Objects.requireNonNull(totalDuration, "totalDuration must not be null");
        if (totalDuration.isNegative()) {
            throw new IllegalArgumentException("totalDuration must not be negative");
        }
        this.mid = (minUsers + maxUsers) / 2.0;
        this.amplitude = (maxUsers - minUsers) / 2.0;
        this.period = period;
        this.totalDuration = totalDuration;
    }

    /**
     * Returns a tick with the sinusoidally computed user count, or {@code null}
     * after the total duration elapses.
     *
     * <p>The spawn rate is computed as the absolute instantaneous derivative of
     * the sine function, clamped to a minimum of 1.0 to ensure responsiveness.
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
        double elapsedSec = elapsed.toMillis() / 1000.0;
        double periodSec = period.toMillis() / 1000.0;
        double angle = 2.0 * Math.PI * elapsedSec / periodSec;
        int users = (int) Math.round(mid + amplitude * Math.sin(angle));
        users = Math.max(users, 0);

        // Spawn rate from derivative: amplitude * 2*PI/period * |cos(angle)|
        double rate = amplitude * 2.0 * Math.PI / periodSec * Math.abs(Math.cos(angle));
        rate = Math.max(rate, 1.0);

        return new ShapeTick(users, rate);
    }
}
