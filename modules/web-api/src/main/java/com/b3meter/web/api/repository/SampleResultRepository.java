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

import java.time.Instant;
import java.util.List;

/**
 * Persistence contract for time-series sample result buckets.
 *
 * <p>Sample results are append-only; they are never updated or deleted.
 * The {@link #insertBatch} method is optimised for high-throughput bulk inserts.
 */
public interface SampleResultRepository {

    /**
     * Inserts all bucket rows in a single JDBC batch operation.
     *
     * @param runId   the test run these samples belong to; must not be null
     * @param buckets the rows to insert; must not be null; an empty list is a no-op
     */
    void insertBatch(String runId, List<SampleBucketRow> buckets);

    /**
     * Returns all sample buckets for the given run whose {@code timestamp} falls
     * within [{@code from}, {@code to}) (inclusive {@code from}, exclusive {@code to}).
     *
     * @param runId the test run identifier; must not be null
     * @param from  start of the time range (inclusive); must not be null
     * @param to    end of the time range (exclusive); must not be null
     * @return list of matching rows ordered by timestamp ascending, possibly empty
     */
    List<SampleBucketRow> findByRunId(String runId, Instant from, Instant to);
}
