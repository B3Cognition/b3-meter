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
