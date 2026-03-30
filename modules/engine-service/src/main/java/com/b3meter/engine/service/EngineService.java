package com.jmeternext.engine.service;

import java.util.Collection;
import java.util.Map;
import java.util.Properties;

/**
 * Contract between the web layer and the test execution engine.
 *
 * <p>Implementations are responsible for starting, stopping, and monitoring test runs.
 * This interface is framework-free — it carries zero Spring or JMeter-internal dependencies —
 * so it can be satisfied by a local {@code StandardJMeterEngine} adapter, a distributed
 * worker-node proxy, or a stub in tests.
 *
 * <p>All methods on this interface must be thread-safe.
 */
public interface EngineService {

    /**
     * Starts a new test run from a previously saved plan.
     *
     * <p>The run is submitted asynchronously. The returned {@link TestRunHandle} contains
     * the run identifier and a {@link java.util.concurrent.CompletableFuture} that resolves
     * when the run completes (successfully or with an error).
     *
     * @param planId    identifier of the saved test plan
     * @param treeData  plan element tree as a structured map (JMX-equivalent object model)
     * @param overrides property overrides applied to this run only (e.g. thread count, host)
     * @return a handle for the newly started run; never {@code null}
     * @throws IllegalArgumentException if {@code planId} is null or blank
     * @throws IllegalStateException    if a run with the derived ID is already active
     */
    TestRunHandle startRun(String planId, Map<String, Object> treeData, Properties overrides);

    /**
     * Requests a graceful stop of the run identified by {@code runId}.
     *
     * <p>The engine is asked to stop after the current iteration of each virtual-user
     * thread completes. In-flight samplers are allowed to finish. The run transitions
     * to {@code STOPPING} and then {@code STOPPED} asynchronously.
     *
     * @param runId the run to stop; must not be {@code null}
     * @throws IllegalArgumentException if {@code runId} is null or blank
     */
    void stopRun(String runId);

    /**
     * Requests an immediate (forced) stop of the run identified by {@code runId}.
     *
     * <p>All virtual-user threads are interrupted immediately. In-flight samplers
     * may not complete. The run transitions to {@code STOPPED} (or {@code ERROR})
     * as soon as the threads are interrupted.
     *
     * @param runId the run to stop immediately; must not be {@code null}
     * @throws IllegalArgumentException if {@code runId} is null or blank
     */
    void stopRunNow(String runId);

    /**
     * Returns the current lifecycle status of the run identified by {@code runId}.
     *
     * @param runId the run identifier to query; must not be {@code null}
     * @return the current {@link TestRunContext.TestRunStatus}, or {@code null} if the run is not found
     * @throws IllegalArgumentException if {@code runId} is null or blank
     */
    TestRunContext.TestRunStatus getRunStatus(String runId);

    /**
     * Returns an unmodifiable view of all currently active (non-completed) run contexts.
     *
     * <p>The returned collection reflects the registry state at the time of the call.
     * Subsequent starts or completions are not reflected.
     *
     * @return a non-null, possibly empty collection of active {@link TestRunContext} instances
     */
    Collection<TestRunContext> activeRuns();
}
