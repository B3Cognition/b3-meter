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
package com.b3meter.distributed.controller;

/**
 * Controls how virtual users are distributed across worker nodes.
 *
 * <p>Most modern load-testing tools (k6, Locust) default to DIVIDE mode,
 * where the total VU count is split evenly across workers. Legacy JMeter
 * uses MULTIPLY, where each worker runs the full VU count.
 */
public enum DistributionMode {

    /**
     * Each worker runs the full VU count (legacy JMeter behavior).
     * Total load = VUs x workers.
     */
    MULTIPLY,

    /**
     * Total VU count is divided evenly across workers.
     * Total load = VUs (specified count).
     * Any remainder is spread across the first {@code remainder} workers (+1 each).
     */
    DIVIDE;

    /** The default mode — matches k6 and Locust conventions. */
    public static final DistributionMode DEFAULT = DIVIDE;
}
