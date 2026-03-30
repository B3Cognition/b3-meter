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
package com.b3meter.web.api.repository;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link TestRunRepository} backed by a {@link ConcurrentHashMap}.
 *
 * <p>Data is held in memory for the lifetime of the application process.
 */
@Repository
public class JdbcTestRunRepository implements TestRunRepository {

    private final ConcurrentHashMap<String, TestRunEntity> runs = new ConcurrentHashMap<>();

    @Override
    public TestRunEntity create(TestRunEntity run) {
        runs.put(run.id(), run);
        return run;
    }

    @Override
    public Optional<TestRunEntity> findById(String id) {
        return Optional.ofNullable(runs.get(id));
    }

    @Override
    public List<TestRunEntity> findAll() {
        return List.copyOf(runs.values());
    }

    @Override
    public List<TestRunEntity> findByPlanId(String planId) {
        return runs.values().stream()
                .filter(r -> planId.equals(r.planId()))
                .toList();
    }

    @Override
    public int countActive() {
        return (int) runs.values().stream()
                .filter(r -> "RUNNING".equals(r.status()) || "STOPPING".equals(r.status()))
                .count();
    }

    @Override
    public void updateStatus(String id, String status) {
        runs.computeIfPresent(id, (k, existing) -> new TestRunEntity(
                existing.id(),
                existing.planId(),
                status,
                existing.startedAt(),
                existing.endedAt(),
                existing.totalSamples(),
                existing.errorCount(),
                existing.ownerId()
        ));
    }

    @Override
    public void updateCompletion(String id, String status, long totalSamples, long errorCount) {
        runs.computeIfPresent(id, (k, existing) -> new TestRunEntity(
                existing.id(),
                existing.planId(),
                status,
                existing.startedAt(),
                Instant.now(),
                totalSamples,
                errorCount,
                existing.ownerId()
        ));
    }
}
