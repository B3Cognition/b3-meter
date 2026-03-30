package com.jmeternext.web.api.repository;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for {@link TestRunEntity}.
 *
 * <p>Test runs are never soft-deleted; they are retained for audit and reporting purposes.
 */
public interface TestRunRepository {

    /**
     * Persists a new test run row and returns the stored entity.
     *
     * @param run the run to create; must not be null
     * @return the created entity
     */
    TestRunEntity create(TestRunEntity run);

    /**
     * Returns the test run with the given {@code id}, or empty if it does not exist.
     *
     * @param id the run identifier; must not be null
     * @return the run, or empty
     */
    Optional<TestRunEntity> findById(String id);

    /**
     * Returns all test runs in the database, ordered by insertion order.
     *
     * @return list of all runs, possibly empty
     */
    List<TestRunEntity> findAll();

    /**
     * Returns all test runs associated with the given plan, ordered by creation time
     * (most recent first based on row insertion order).
     *
     * @param planId the plan identifier; must not be null
     * @return list of matching runs, possibly empty
     */
    List<TestRunEntity> findByPlanId(String planId);

    /**
     * Returns the number of test runs currently in an active state
     * ({@code RUNNING} or {@code STOPPING}).
     *
     * <p>Used as the supplier for the {@code jmeter_runs_active} Prometheus gauge.
     *
     * @return count of active runs; never negative
     */
    int countActive();

    /**
     * Updates only the {@code status} column of the run with the given {@code id}.
     *
     * @param id     the run identifier; must not be null
     * @param status the new status value; must not be null
     */
    void updateStatus(String id, String status);

    /**
     * Updates the {@code status}, {@code total_samples}, {@code error_count}, and
     * {@code ended_at} columns of the run with the given {@code id}.
     *
     * @param id           the run identifier; must not be null
     * @param status       the terminal status (e.g. {@code COMPLETED}, {@code FAILED})
     * @param totalSamples total number of samples collected
     * @param errorCount   number of failed samples
     */
    void updateCompletion(String id, String status, long totalSamples, long errorCount);
}
