package com.jmeternext.web.api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link JdbcTestPlanRepository} against in-memory H2.
 *
 * <p>Each test class gets a fresh Spring context via {@link DirtiesContext} so that
 * H2 state does not leak between test classes.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JdbcTestPlanRepositoryTest {

    @Autowired
    TestPlanRepository repository;

    // -------------------------------------------------------------------------
    // save + findById round-trip
    // -------------------------------------------------------------------------

    @Test
    void saveAndFindById_roundTrip() {
        TestPlanEntity plan = activePlan("round-trip-plan", "owner-1", "{\"nodes\":[]}");
        repository.save(plan);

        Optional<TestPlanEntity> found = repository.findById(plan.id());

        assertTrue(found.isPresent(), "Saved plan must be retrievable");
        assertEquals(plan.id(), found.get().id());
        assertEquals(plan.name(), found.get().name());
        assertEquals(plan.ownerId(), found.get().ownerId());
        assertEquals(plan.treeData(), found.get().treeData());
        assertNull(found.get().deletedAt(), "Active plan must not have deletedAt set");
    }

    @Test
    void findById_unknownId_returnsEmpty() {
        Optional<TestPlanEntity> found = repository.findById("does-not-exist");
        assertFalse(found.isPresent(), "Unknown id must return empty");
    }

    // -------------------------------------------------------------------------
    // upsert (update existing)
    // -------------------------------------------------------------------------

    @Test
    void save_existingId_updatesRecord() {
        TestPlanEntity original = activePlan("original-name", "owner-2", "{\"v\":1}");
        repository.save(original);

        TestPlanEntity updated = new TestPlanEntity(
                original.id(), "updated-name", original.ownerId(), "{\"v\":2}",
                original.createdAt(), Instant.now(), null
        );
        repository.save(updated);

        TestPlanEntity found = repository.findById(original.id()).orElseThrow();
        assertEquals("updated-name", found.name());
        assertEquals("{\"v\":2}", found.treeData());
    }

    // -------------------------------------------------------------------------
    // soft delete
    // -------------------------------------------------------------------------

    @Test
    void deleteById_softDeletesRecord_notReturnedByFindById() {
        TestPlanEntity plan = activePlan("to-delete", "owner-3", "{}");
        repository.save(plan);

        repository.deleteById(plan.id());

        Optional<TestPlanEntity> found = repository.findById(plan.id());
        assertFalse(found.isPresent(), "Soft-deleted plan must not be returned by findById");
    }

    @Test
    void deleteById_softDeletedPlan_notReturnedByFindAll() {
        TestPlanEntity plan = activePlan("to-delete-all", "owner-x", "{}");
        repository.save(plan);
        repository.deleteById(plan.id());

        List<TestPlanEntity> all = repository.findAll();
        boolean anyMatch = all.stream().anyMatch(p -> p.id().equals(plan.id()));
        assertFalse(anyMatch, "Soft-deleted plan must not appear in findAll");
    }

    // -------------------------------------------------------------------------
    // findByOwnerId
    // -------------------------------------------------------------------------

    @Test
    void findByOwnerId_returnsOnlyMatchingOwnerPlans() {
        String ownerId = UUID.randomUUID().toString();
        TestPlanEntity plan1 = activePlan("plan-a", ownerId, "{}");
        TestPlanEntity plan2 = activePlan("plan-b", ownerId, "{}");
        TestPlanEntity otherOwner = activePlan("plan-c", UUID.randomUUID().toString(), "{}");
        repository.save(plan1);
        repository.save(plan2);
        repository.save(otherOwner);

        List<TestPlanEntity> found = repository.findByOwnerId(ownerId);

        assertEquals(2, found.size(), "Should return exactly 2 plans for the given owner");
        assertTrue(found.stream().allMatch(p -> p.ownerId().equals(ownerId)));
    }

    @Test
    void findByOwnerId_noPlans_returnsEmpty() {
        List<TestPlanEntity> found = repository.findByOwnerId("nonexistent-owner");
        assertTrue(found.isEmpty());
    }

    // -------------------------------------------------------------------------
    // revision history
    // -------------------------------------------------------------------------

    @Test
    void saveRevision_andFindRevisions_orderedByRevisionNumberDesc() {
        TestPlanEntity plan = activePlan("revision-plan", "owner-rev", "{}");
        repository.save(plan);

        TestPlanRevisionEntity rev1 = revision(plan.id(), 1, "{\"v\":1}", "alice");
        TestPlanRevisionEntity rev2 = revision(plan.id(), 2, "{\"v\":2}", "bob");
        TestPlanRevisionEntity rev3 = revision(plan.id(), 3, "{\"v\":3}", "carol");
        repository.saveRevision(rev1);
        repository.saveRevision(rev2);
        repository.saveRevision(rev3);

        List<TestPlanRevisionEntity> revisions = repository.findRevisions(plan.id());

        assertEquals(3, revisions.size());
        assertEquals(3, revisions.get(0).revisionNumber(), "Most recent revision must be first");
        assertEquals(2, revisions.get(1).revisionNumber());
        assertEquals(1, revisions.get(2).revisionNumber());
    }

    @Test
    void findRevisions_unknownPlanId_returnsEmpty() {
        List<TestPlanRevisionEntity> revisions = repository.findRevisions("no-such-plan");
        assertTrue(revisions.isEmpty());
    }

    @Test
    void saveRevision_storesAuthorAndTreeData() {
        TestPlanEntity plan = activePlan("rev-author-plan", "owner-4", "{}");
        repository.save(plan);

        TestPlanRevisionEntity rev = revision(plan.id(), 1, "{\"nodes\":[{\"id\":\"n1\"}]}", "alice");
        repository.saveRevision(rev);

        TestPlanRevisionEntity stored = repository.findRevisions(plan.id()).get(0);
        assertEquals("alice", stored.author());
        assertEquals("{\"nodes\":[{\"id\":\"n1\"}]}", stored.treeData());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private TestPlanEntity activePlan(String name, String ownerId, String treeData) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        return new TestPlanEntity(UUID.randomUUID().toString(), name, ownerId, treeData, now, now, null);
    }

    private TestPlanRevisionEntity revision(String planId, int revNum, String treeData, String author) {
        return new TestPlanRevisionEntity(
                UUID.randomUUID().toString(), planId, revNum, treeData,
                Instant.now().truncatedTo(ChronoUnit.MILLIS), author
        );
    }
}
