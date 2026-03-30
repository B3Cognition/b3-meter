package com.jmeternext.web.api.repository;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for {@link TestPlanEntity} and its revision history.
 *
 * <p>All find methods exclude soft-deleted plans (where {@code deleted_at IS NOT NULL}).
 * Soft-delete is performed by {@link #deleteById}, which sets {@code deleted_at} to now.
 */
public interface TestPlanRepository {

    /**
     * Persists a test plan. If a plan with the same {@code id} already exists, it is
     * updated (upsert semantics). The returned entity reflects the persisted state.
     *
     * @param plan the plan to save; must not be null
     * @return the saved entity
     */
    TestPlanEntity save(TestPlanEntity plan);

    /**
     * Returns the plan with the given {@code id}, or empty if it does not exist
     * or has been soft-deleted.
     *
     * @param id the plan identifier; must not be null
     * @return the plan, or empty
     */
    Optional<TestPlanEntity> findById(String id);

    /**
     * Returns all active plans owned by the given user.
     *
     * @param ownerId the owner's identifier; must not be null
     * @return list of matching plans, possibly empty
     */
    List<TestPlanEntity> findByOwnerId(String ownerId);

    /**
     * Returns all active plans.
     *
     * @return list of all active plans, possibly empty
     */
    List<TestPlanEntity> findAll();

    /**
     * Soft-deletes the plan with the given {@code id} by setting {@code deleted_at}
     * to the current timestamp. Subsequent calls to {@link #findById} will return empty.
     *
     * @param id the plan identifier; must not be null
     */
    void deleteById(String id);

    /**
     * Appends a revision record for an existing plan.
     *
     * @param revision the revision to store; must not be null
     */
    void saveRevision(TestPlanRevisionEntity revision);

    /**
     * Returns all revisions for the given plan, ordered by {@code revision_number} descending
     * (most recent first).
     *
     * @param planId the plan identifier; must not be null
     * @return list of revisions, possibly empty
     */
    List<TestPlanRevisionEntity> findRevisions(String planId);
}
