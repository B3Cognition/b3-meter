package com.jmeternext.web.api.repository;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link TestPlanRepository} backed by a {@link ConcurrentHashMap}.
 *
 * <p>Data is held in memory for the lifetime of the application process.
 * This removes the need for any external database while keeping the REST API fully functional.
 */
@Repository
public class JdbcTestPlanRepository implements TestPlanRepository {

    private final ConcurrentHashMap<String, TestPlanEntity> plans = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<TestPlanRevisionEntity>> revisions = new ConcurrentHashMap<>();

    @Override
    public TestPlanEntity save(TestPlanEntity plan) {
        Instant now = Instant.now();
        TestPlanEntity toStore = new TestPlanEntity(
                plan.id(),
                plan.name(),
                plan.ownerId(),
                plan.treeData(),
                plan.createdAt() != null ? plan.createdAt() : now,
                plan.updatedAt() != null ? plan.updatedAt() : now,
                plan.deletedAt()
        );
        plans.put(plan.id(), toStore);
        return toStore;
    }

    @Override
    public Optional<TestPlanEntity> findById(String id) {
        TestPlanEntity plan = plans.get(id);
        if (plan == null || plan.deletedAt() != null) {
            return Optional.empty();
        }
        return Optional.of(plan);
    }

    @Override
    public List<TestPlanEntity> findByOwnerId(String ownerId) {
        return plans.values().stream()
                .filter(p -> p.deletedAt() == null)
                .filter(p -> ownerId.equals(p.ownerId()))
                .sorted(Comparator.comparing(TestPlanEntity::createdAt, Comparator.reverseOrder()))
                .toList();
    }

    @Override
    public List<TestPlanEntity> findAll() {
        return plans.values().stream()
                .filter(p -> p.deletedAt() == null)
                .sorted(Comparator.comparing(TestPlanEntity::createdAt, Comparator.reverseOrder()))
                .toList();
    }

    @Override
    public void deleteById(String id) {
        plans.computeIfPresent(id, (k, existing) -> new TestPlanEntity(
                existing.id(),
                existing.name(),
                existing.ownerId(),
                existing.treeData(),
                existing.createdAt(),
                existing.updatedAt(),
                Instant.now()
        ));
    }

    @Override
    public void saveRevision(TestPlanRevisionEntity revision) {
        TestPlanRevisionEntity toStore = new TestPlanRevisionEntity(
                revision.id(),
                revision.planId(),
                revision.revisionNumber(),
                revision.treeData(),
                revision.createdAt() != null ? revision.createdAt() : Instant.now(),
                revision.author()
        );
        revisions.computeIfAbsent(revision.planId(), k -> new ArrayList<>()).add(toStore);
    }

    @Override
    public List<TestPlanRevisionEntity> findRevisions(String planId) {
        List<TestPlanRevisionEntity> list = revisions.getOrDefault(planId, List.of());
        return list.stream()
                .sorted(Comparator.comparingInt(TestPlanRevisionEntity::revisionNumber).reversed())
                .toList();
    }
}
