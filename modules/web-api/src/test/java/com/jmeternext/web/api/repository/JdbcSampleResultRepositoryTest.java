package com.jmeternext.web.api.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link JdbcSampleResultRepository} against in-memory H2.
 *
 * <p>Verifies bulk insert of 1 000 rows and time-range queries.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JdbcSampleResultRepositoryTest {

    @Autowired
    SampleResultRepository sampleRepository;

    @Autowired
    JdbcTemplate jdbc;

    private String planId;
    private String runId;

    @BeforeEach
    void insertParentRows() {
        planId = UUID.randomUUID().toString();
        runId  = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO test_plans (id, name, tree_data) VALUES (?, ?, ?)", planId, "Sample Plan", "{}");
        jdbc.update("INSERT INTO test_runs (id, plan_id, status) VALUES (?, ?, ?)", runId, planId, "RUNNING");
    }

    // -------------------------------------------------------------------------
    // insertBatch
    // -------------------------------------------------------------------------

    @Test
    void insertBatch_oneThousandRows_allPersisted() {
        List<SampleBucketRow> buckets = generateBuckets(runId, 1_000);

        assertDoesNotThrow(() -> sampleRepository.insertBatch(runId, buckets));

        int count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sample_results WHERE run_id = ?", Integer.class, runId
        );
        assertEquals(1_000, count, "All 1 000 rows must be persisted");
    }

    @Test
    void insertBatch_emptyList_isNoOp() {
        assertDoesNotThrow(() -> sampleRepository.insertBatch(runId, List.of()));

        int count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sample_results WHERE run_id = ?", Integer.class, runId
        );
        assertEquals(0, count, "Empty insert must leave table unchanged");
    }

    // -------------------------------------------------------------------------
    // findByRunId (time-series query)
    // -------------------------------------------------------------------------

    @Test
    void findByRunId_withTimeRange_returnsMatchingRows() {
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant t0 = base;
        Instant t1 = base.plusSeconds(1);
        Instant t2 = base.plusSeconds(2);
        Instant t3 = base.plusSeconds(3);

        List<SampleBucketRow> buckets = List.of(
                bucket(runId, t0, "label-a"),
                bucket(runId, t1, "label-b"),
                bucket(runId, t2, "label-c"),
                bucket(runId, t3, "label-d")
        );
        sampleRepository.insertBatch(runId, buckets);

        // Query [t1, t3) — should return t1 and t2, not t0 or t3
        List<SampleBucketRow> results = sampleRepository.findByRunId(runId, t1, t3);

        assertEquals(2, results.size(), "Should return rows at t1 and t2 only");
        assertEquals("label-b", results.get(0).samplerLabel());
        assertEquals("label-c", results.get(1).samplerLabel());
    }

    @Test
    void findByRunId_noRowsInRange_returnsEmpty() {
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        sampleRepository.insertBatch(runId, List.of(bucket(runId, base, "label-x")));

        Instant future = base.plusSeconds(100);
        List<SampleBucketRow> results = sampleRepository.findByRunId(runId, future, future.plusSeconds(10));

        assertTrue(results.isEmpty(), "Query outside data range must return empty list");
    }

    @Test
    void findByRunId_rowsOrderedByTimestampAscending() {
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        // Insert in reverse order to verify ORDER BY
        List<SampleBucketRow> buckets = List.of(
                bucket(runId, base.plusSeconds(3), "z"),
                bucket(runId, base.plusSeconds(1), "a"),
                bucket(runId, base.plusSeconds(2), "m")
        );
        sampleRepository.insertBatch(runId, buckets);

        List<SampleBucketRow> results = sampleRepository.findByRunId(
                runId, base, base.plusSeconds(10));

        assertEquals(3, results.size());
        assertTrue(
                results.get(0).timestamp().isBefore(results.get(1).timestamp()),
                "First row timestamp must precede second"
        );
        assertTrue(
                results.get(1).timestamp().isBefore(results.get(2).timestamp()),
                "Second row timestamp must precede third"
        );
    }

    @Test
    void findByRunId_isolatedByRunId() {
        String otherRunId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO test_runs (id, plan_id, status) VALUES (?, ?, ?)", otherRunId, planId, "RUNNING");

        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        sampleRepository.insertBatch(runId,      List.of(bucket(runId,      base, "mine")));
        sampleRepository.insertBatch(otherRunId, List.of(bucket(otherRunId, base, "theirs")));

        List<SampleBucketRow> results = sampleRepository.findByRunId(runId, base, base.plusSeconds(1));

        assertEquals(1, results.size());
        assertEquals("mine", results.get(0).samplerLabel());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static List<SampleBucketRow> generateBuckets(String runId, int count) {
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        List<SampleBucketRow> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new SampleBucketRow(
                    runId,
                    base.plusMillis(i),
                    "sampler-" + (i % 10),
                    100L + i,
                    i % 5,
                    200.0 + i,
                    50.0,
                    1500.0,
                    350.0,
                    400.0,
                    500.0,
                    10.5 + i
            ));
        }
        return rows;
    }

    private static SampleBucketRow bucket(String runId, Instant timestamp, String label) {
        return new SampleBucketRow(runId, timestamp, label, 50L, 2L, 120.0, 30.0, 800.0, 200.0, 250.0, 300.0, 5.0);
    }
}
