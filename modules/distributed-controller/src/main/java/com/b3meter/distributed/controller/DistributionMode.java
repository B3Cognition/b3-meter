package com.jmeternext.distributed.controller;

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
