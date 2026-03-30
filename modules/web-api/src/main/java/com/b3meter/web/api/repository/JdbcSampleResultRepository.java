package com.jmeternext.web.api.repository;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link SampleResultRepository} backed by a {@link ConcurrentHashMap}.
 *
 * <p>Sample buckets are stored per run ID. Data is held in memory for the lifetime of the
 * application process.
 */
@Repository
public class JdbcSampleResultRepository implements SampleResultRepository {

    private final ConcurrentHashMap<String, List<SampleBucketRow>> store = new ConcurrentHashMap<>();

    @Override
    public void insertBatch(String runId, List<SampleBucketRow> buckets) {
        if (buckets.isEmpty()) {
            return;
        }
        store.computeIfAbsent(runId, k -> new ArrayList<>()).addAll(buckets);
    }

    @Override
    public List<SampleBucketRow> findByRunId(String runId, Instant from, Instant to) {
        List<SampleBucketRow> all = store.getOrDefault(runId, List.of());
        return all.stream()
                .filter(row -> !row.timestamp().isBefore(from) && row.timestamp().isBefore(to))
                .sorted((a, b) -> a.timestamp().compareTo(b.timestamp()))
                .toList();
    }
}
