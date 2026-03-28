package com.jmeternext.engine.service;

/**
 * Enumerates the available executor types for driving load in a test run.
 *
 * <p>Each type represents a distinct scheduling model for how virtual users
 * and iterations are orchestrated:
 *
 * <ul>
 *   <li><b>VU-based models</b> ({@link #CONSTANT_VUS}, {@link #RAMPING_VUS},
 *       {@link #PER_VU_ITERATIONS}, {@link #SHARED_ITERATIONS}) control load
 *       by managing the number of concurrent virtual users. The actual request
 *       rate is a side-effect of VU count and response times.</li>
 *   <li><b>Arrival-rate models</b> ({@link #CONSTANT_ARRIVAL_RATE},
 *       {@link #RAMPING_ARRIVAL_RATE}) control load by targeting a specific
 *       iteration rate (iterations per second). VUs are auto-scaled from a pool
 *       to sustain the target rate, making them independent of server response
 *       times.</li>
 * </ul>
 *
 * <p>The executor type is selected via the {@code executor_type} property on a
 * ThreadGroup {@link com.jmeternext.engine.service.plan.PlanNode PlanNode}.
 *
 * @see ArrivalRateExecutor
 * @see RampingArrivalRateExecutor
 * @see VirtualUserExecutor
 */
public enum ExecutorType {

    /**
     * Fixed number of VUs, each looping through the plan continuously.
     * This is the current default, implemented by {@link VirtualUserExecutor}.
     */
    CONSTANT_VUS,

    /**
     * VU count changes across time-based stages (ramp up, hold, ramp down).
     * Not yet implemented.
     */
    RAMPING_VUS,

    /**
     * Fixed iteration rate (iterations/second), VUs auto-scaled from a pool.
     * Implemented by {@link ArrivalRateExecutor}.
     */
    CONSTANT_ARRIVAL_RATE,

    /**
     * Iteration rate changes across time-based stages via linear interpolation.
     * Implemented by {@link RampingArrivalRateExecutor}.
     */
    RAMPING_ARRIVAL_RATE,

    /**
     * Each VU executes exactly N iterations, then stops.
     * Not yet implemented.
     */
    PER_VU_ITERATIONS,

    /**
     * N total iterations shared across all VUs; test ends when all are consumed.
     * Not yet implemented.
     */
    SHARED_ITERATIONS;

    /**
     * The default executor type used when none is explicitly specified.
     */
    public static final ExecutorType DEFAULT = CONSTANT_VUS;
}
