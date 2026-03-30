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

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for {@link WorkerEntity}.
 *
 * <p>Workers represent remote JMeter nodes that can participate in distributed
 * test runs. They are registered by an administrator and assigned to runs as
 * needed.
 */
public interface WorkerRepository {

    /**
     * Persists a new worker row and returns the stored entity.
     *
     * @param worker the worker to create; must not be null
     * @return the created entity
     */
    WorkerEntity save(WorkerEntity worker);

    /**
     * Returns the worker with the given {@code id}, or empty if it does not exist.
     *
     * @param id the worker identifier; must not be null
     * @return the worker, or empty
     */
    Optional<WorkerEntity> findById(String id);

    /**
     * Returns all registered workers ordered by registration time.
     *
     * @return list of all workers, possibly empty
     */
    List<WorkerEntity> findAll();

    /**
     * Removes the worker with the given {@code id}.
     *
     * @param id the worker identifier; must not be null
     * @return {@code true} if the row existed and was deleted, {@code false} if not found
     */
    boolean deleteById(String id);

    /**
     * Updates only the {@code status} column of the worker with the given {@code id}.
     *
     * @param id     the worker identifier; must not be null
     * @param status the new status value (AVAILABLE, BUSY, OFFLINE); must not be null
     */
    void updateStatus(String id, String status);
}
