package com.jmeternext.engine.service.shape;

import java.time.Duration;

/**
 * Defines a dynamic load profile as a function of elapsed time.
 *
 * <p>The engine calls {@link #tick} every second. The returned {@link ShapeTick}
 * controls how many virtual users should be running and at what spawn rate.
 * Returning {@code null} signals the test should stop.
 *
 * <p>Inspired by Locust's LoadTestShape and k6's scenario stages.
 */
public interface LoadShape {

    /**
     * Snapshot of the desired load state at a point in time.
     *
     * @param targetUsers the number of virtual users that should be running
     * @param spawnRate   the rate (users per second) at which to spawn or despawn users
     */
    record ShapeTick(int targetUsers, double spawnRate) {
        public ShapeTick {
            if (targetUsers < 0) throw new IllegalArgumentException("targetUsers must be >= 0");
            if (spawnRate < 0) throw new IllegalArgumentException("spawnRate must be >= 0");
        }
    }

    /**
     * Called once per second to determine the desired load state.
     *
     * @param elapsed      time since test start
     * @param currentUsers currently active virtual user count
     * @return desired state, or {@code null} to stop the test
     */
    ShapeTick tick(Duration elapsed, int currentUsers);
}
