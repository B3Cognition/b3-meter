package com.jmeternext.web.api.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link JdbcTestRunRepository} against in-memory H2.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JdbcTestRunRepositoryTest {

    @Autowired
    TestRunRepository runRepository;

    @Autowired
    TestPlanRepository planRepository;

    @Autowired
    JdbcTemplate jdbc;

    /** A plan that all runs in this class can reference. */
    private String sharedPlanId;

    @BeforeEach
    void insertSharedPlan() {
        sharedPlanId = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO test_plans (id, name, tree_data) VALUES (?, ?, ?)",
                sharedPlanId, "Shared Plan", "{}"
        );
    }

    // -------------------------------------------------------------------------
    // create + findById
    // -------------------------------------------------------------------------

    @Test
    void createAndFindById_roundTrip() {
        TestRunEntity run = newRun(sharedPlanId, "CREATED");
        runRepository.create(run);

        Optional<TestRunEntity> found = runRepository.findById(run.id());

        assertTrue(found.isPresent(), "Created run must be retrievable");
        assertEquals(run.id(), found.get().id());
        assertEquals(run.planId(), found.get().planId());
        assertEquals("CREATED", found.get().status());
        assertEquals(0L, found.get().totalSamples());
        assertEquals(0L, found.get().errorCount());
    }

    @Test
    void findById_unknownId_returnsEmpty() {
        Optional<TestRunEntity> found = runRepository.findById("no-such-run");
        assertFalse(found.isPresent());
    }

    // -------------------------------------------------------------------------
    // updateStatus
    // -------------------------------------------------------------------------

    @Test
    void updateStatus_changesStatusOnly() {
        TestRunEntity run = newRun(sharedPlanId, "CREATED");
        runRepository.create(run);

        runRepository.updateStatus(run.id(), "RUNNING");

        TestRunEntity found = runRepository.findById(run.id()).orElseThrow();
        assertEquals("RUNNING", found.status());
        assertEquals(0L, found.totalSamples(), "totalSamples must remain unchanged");
    }

    @Test
    void updateStatus_unknownId_isNoOp() {
        // Must not throw; JDBC UPDATE of 0 rows is not an error.
        assertDoesNotThrow(() -> runRepository.updateStatus("no-such-run", "RUNNING"));
    }

    // -------------------------------------------------------------------------
    // updateCompletion
    // -------------------------------------------------------------------------

    @Test
    void updateCompletion_setsStatusSamplesErrorsAndEndedAt() {
        TestRunEntity run = newRun(sharedPlanId, "RUNNING");
        runRepository.create(run);

        runRepository.updateCompletion(run.id(), "COMPLETED", 5_000L, 42L);

        TestRunEntity found = runRepository.findById(run.id()).orElseThrow();
        assertEquals("COMPLETED", found.status());
        assertEquals(5_000L, found.totalSamples());
        assertEquals(42L, found.errorCount());
        assertNotNull(found.endedAt(), "endedAt must be set after completion");
    }

    // -------------------------------------------------------------------------
    // findByPlanId
    // -------------------------------------------------------------------------

    @Test
    void findByPlanId_returnsAllRunsForPlan() {
        TestRunEntity run1 = newRun(sharedPlanId, "CREATED");
        TestRunEntity run2 = newRun(sharedPlanId, "RUNNING");
        runRepository.create(run1);
        runRepository.create(run2);

        List<TestRunEntity> runs = runRepository.findByPlanId(sharedPlanId);

        assertTrue(runs.size() >= 2, "Must return at least 2 runs");
        assertTrue(runs.stream().allMatch(r -> r.planId().equals(sharedPlanId)));
    }

    @Test
    void findByPlanId_unknownPlan_returnsEmpty() {
        List<TestRunEntity> runs = runRepository.findByPlanId("no-such-plan");
        assertTrue(runs.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private TestRunEntity newRun(String planId, String status) {
        return new TestRunEntity(
                UUID.randomUUID().toString(), planId, status,
                null, null, 0L, 0L, null
        );
    }
}
