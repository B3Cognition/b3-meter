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
import java.util.Objects;

/**
 * A load shape that linearly ramps from a start user count to an end user count
 * over a specified duration.
 *
 * <p>The user count is interpolated linearly:
 * {@code users = startUsers + (endUsers - startUsers) * (elapsed / rampDuration)}.
 * The spawn rate is constant at {@code |endUsers - startUsers| / rampDuration.toSeconds()}.
 *
 * <p>Supports both ramp-up ({@code endUsers > startUsers}) and ramp-down
 * ({@code endUsers < startUsers}).
 *
 * <p>Example: ramp from 0 to 200 users over 2 minutes:
 * <pre>{@code
 * new RampShape(0, 200, Duration.ofMinutes(2))
 * }</pre>
 */
public final class RampShape implements LoadShape {

    private final int startUsers;
    private final int endUsers;
    private final Duration rampDuration;
    private final double spawnRate;

    /**
     * Constructs a linear ramp shape.
     *
     * @param startUsers   initial user count; must be {@code >= 0}
     * @param endUsers     target user count at end of ramp; must be {@code >= 0}
     * @param rampDuration how long the ramp takes; must not be {@code null}, negative, or zero
     * @throws IllegalArgumentException if user counts are negative or duration is non-positive
     * @throws NullPointerException     if {@code rampDuration} is {@code null}
     */
    public RampShape(int startUsers, int endUsers, Duration rampDuration) {
        if (startUsers < 0) throw new IllegalArgumentException("startUsers must be >= 0");
        if (endUsers < 0) throw new IllegalArgumentException("endUsers must be >= 0");
        Objects.requireNonNull(rampDuration, "rampDuration must not be null");
        if (rampDuration.isNegative() || rampDuration.isZero()) {
            throw new IllegalArgumentException("rampDuration must be positive");
        }
        this.startUsers = startUsers;
        this.endUsers = endUsers;
        this.rampDuration = rampDuration;
        this.spawnRate = Math.abs(endUsers - startUsers) / (double) rampDuration.toSeconds();
    }

    /**
     * Returns a tick with the linearly interpolated user count, or {@code null}
     * after the ramp duration elapses.
     *
     * @param elapsed      time since test start
     * @param currentUsers currently active virtual user count (unused)
     * @return shape tick with interpolated users, or {@code null} when ramp completes
     */
    @Override
    public ShapeTick tick(Duration elapsed, int currentUsers) {
        if (elapsed.compareTo(rampDuration) > 0) {
            return null;
        }
        double progress = (double) elapsed.toMillis() / rampDuration.toMillis();
        progress = Math.min(progress, 1.0);
        int users = (int) Math.round(startUsers + (endUsers - startUsers) * progress);
        return new ShapeTick(users, spawnRate);
    }
}
