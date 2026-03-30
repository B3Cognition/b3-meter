package com.jmeternext.web.api.repository;

import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link WorkerRepository} backed by a {@link ConcurrentHashMap}.
 *
 * <p>Data is held in memory for the lifetime of the application process.
 */
@Repository
public class JdbcWorkerRepository implements WorkerRepository {

    private final ConcurrentHashMap<String, WorkerEntity> workers = new ConcurrentHashMap<>();

    @Override
    public WorkerEntity save(WorkerEntity worker) {
        workers.put(worker.id(), worker);
        return worker;
    }

    @Override
    public Optional<WorkerEntity> findById(String id) {
        return Optional.ofNullable(workers.get(id));
    }

    @Override
    public List<WorkerEntity> findAll() {
        return workers.values().stream()
                .sorted(Comparator.comparing(
                        WorkerEntity::registeredAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Override
    public boolean deleteById(String id) {
        return workers.remove(id) != null;
    }

    @Override
    public void updateStatus(String id, String status) {
        workers.computeIfPresent(id, (k, existing) -> new WorkerEntity(
                existing.id(),
                existing.hostname(),
                existing.port(),
                status,
                existing.lastHeartbeat(),
                existing.registeredAt()
        ));
    }
}
