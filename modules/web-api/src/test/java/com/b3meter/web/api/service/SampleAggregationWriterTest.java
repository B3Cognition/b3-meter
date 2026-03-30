package com.jmeternext.web.api.service;

import com.jmeternext.engine.service.SampleBucket;
import com.jmeternext.engine.service.SampleStreamBroker;
import com.jmeternext.web.api.repository.SampleBucketRow;
import com.jmeternext.web.api.repository.SampleResultRepository;
import com.jmeternext.web.api.repository.TestPlanEntity;
import com.jmeternext.web.api.repository.TestPlanRepository;
import com.jmeternext.web.api.repository.TestRunEntity;
import com.jmeternext.web.api.repository.TestRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link SampleAggregationWriter}.
 *
 * <p>Boots the full Spring context with in-memory repositories. Tests verify:
 * <ul>
 *   <li>Published buckets are written to the sample result repository</li>
 *   <li>A repository failure does not break the broker fan-out to other consumers</li>
 *   <li>Run completion updates the test run totals</li>
 *   <li>Intermediate flush is triggered when the buffer exceeds
 *       {@link SampleAggregationWriter#FLUSH_THRESHOLD}</li>
 *   <li>Calling {@code startWriting} twice is idempotent</li>
 *   <li>Calling {@code stopWriting} for an unknown runId is a no-op</li>
 * </ul>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SampleAggregationWriterTest {

    @Autowired
    SampleAggregationWriter writer;

    @Autowired
    SampleStreamBroker broker;

    @Autowired
    SampleResultRepository sampleRepository;

    @Autowired
    TestRunRepository runRepository;

    @Autowired
    TestPlanRepository planRepository;

    private String planId;
    private String runId;

    @BeforeEach
    void setUp() {
        planId = UUID.randomUUID().toString();
        runId  = UUID.randomUUID().toString();

        planRepository.save(new TestPlanEntity(planId, "Aggregation Test Plan", "default", "{}", Instant.now(), Instant.now(), null));
        runRepository.create(new TestRunEntity(runId, planId, "RUNNING", Instant.now(), null, 0L, 0L, "default"));
    }

    // -------------------------------------------------------------------------
    // startWriting + publish -> sample_results rows appear
    // -------------------------------------------------------------------------

    @Test
    void publishedBuckets_afterStartWriting_arePersistedToRepository() {
        writer.startWriting(runId);

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        broker.publish(runId, bucket("GET /api/users", now));
        broker.publish(runId, bucket("GET /api/items", now.plusSeconds(1)));

        writer.stopWriting(runId, "COMPLETED");

        int count = countSampleRows(runId);
        assertEquals(2, count, "Both published buckets must be persisted");
    }

    @Test
    void publishedBucket_hasCorrectFieldValues() {
        Instant ts = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        writer.startWriting(runId);

        SampleBucket b = new SampleBucket(ts, "POST /login", 50L, 5L,
                200.0, 80.0, 900.0, 350.0, 400.0, 500.0, 50.0);
        broker.publish(runId, b);
        writer.stopWriting(runId, "COMPLETED");

        List<SampleBucketRow> rows = sampleRepository.findByRunId(
                runId, ts, ts.plusSeconds(1));

        assertFalse(rows.isEmpty(), "At least one row must be stored");
        SampleBucketRow row = rows.get(0);
        assertEquals(runId,          row.runId());
        assertEquals("POST /login",  row.samplerLabel());
        assertEquals(50L,            row.sampleCount());
        assertEquals(5L,             row.errorCount());
        assertEquals(200.0,          row.avgResponseTime(), 0.001);
        assertEquals(80.0,           row.minResponseTime(), 0.001);
        assertEquals(900.0,          row.maxResponseTime(), 0.001);
        assertEquals(350.0,          row.percentile90(),    0.001);
        assertEquals(400.0,          row.percentile95(),    0.001);
        assertEquals(500.0,          row.percentile99(),    0.001);
        assertEquals(50.0,           row.samplesPerSecond(), 0.001);
    }

    // -------------------------------------------------------------------------
    // Repository failure does not break broker fan-out
    // -------------------------------------------------------------------------

    @Test
    void repoFailure_doesNotInterruptOtherBrokerConsumers() {
        // Use a broken repository that always throws.
        SampleResultRepository brokenRepo = new SampleResultRepository() {
            @Override
            public void insertBatch(String rid, java.util.List<SampleBucketRow> buckets) {
                throw new RuntimeException("Simulated repository failure");
            }

            @Override
            public java.util.List<SampleBucketRow> findByRunId(String rid,
                    java.time.Instant from, java.time.Instant to) {
                return java.util.List.of();
            }
        };

        // Manually construct a writer that uses the broken repo; register its session.
        SampleAggregationWriter faultyWriter = new SampleAggregationWriter(
                broker, brokenRepo, runRepository);
        faultyWriter.startWriting(runId);

        // Also register a healthy counter consumer directly on the broker.
        AtomicInteger receivedByHealthyConsumer = new AtomicInteger(0);
        broker.subscribe(runId, b -> receivedByHealthyConsumer.incrementAndGet());

        // Publishing must not throw even though the repo is broken.
        assertDoesNotThrow(() ->
                broker.publish(runId, bucket("label", Instant.now().truncatedTo(ChronoUnit.SECONDS))));

        // The healthy consumer still received the bucket.
        assertEquals(1, receivedByHealthyConsumer.get(),
                "Healthy consumer must still receive buckets even when writer fails");

        // Clean up
        assertDoesNotThrow(() -> faultyWriter.stopWriting(runId, "COMPLETED"));
    }

    // -------------------------------------------------------------------------
    // stopWriting updates test_runs totals
    // -------------------------------------------------------------------------

    @Test
    void stopWriting_updatesTestRunTotalsAndStatus() {
        writer.startWriting(runId);

        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        // 3 buckets: 100 samples each, 10 errors each
        broker.publish(runId, new SampleBucket(base,               "label", 100L, 10L,
                200.0, 50.0, 800.0, 300.0, 350.0, 450.0, 100.0));
        broker.publish(runId, new SampleBucket(base.plusSeconds(1), "label", 100L, 10L,
                200.0, 50.0, 800.0, 300.0, 350.0, 450.0, 100.0));
        broker.publish(runId, new SampleBucket(base.plusSeconds(2), "label", 100L, 10L,
                200.0, 50.0, 800.0, 300.0, 350.0, 450.0, 100.0));

        writer.stopWriting(runId, "COMPLETED");

        var runOpt = runRepository.findById(runId);
        assertTrue(runOpt.isPresent());
        var run = runOpt.get();
        assertEquals("COMPLETED", run.status(),       "Status must be COMPLETED");
        assertEquals(300L,        run.totalSamples(),  "total_samples must be sum of all bucket sampleCounts");
        assertEquals(30L,         run.errorCount(),    "error_count must be sum of all bucket errorCounts");
    }

    // -------------------------------------------------------------------------
    // Intermediate flush when buffer exceeds FLUSH_THRESHOLD
    // -------------------------------------------------------------------------

    @Test
    void bufferExceedingFlushThreshold_isWrittenBeforeStopWriting() {
        writer.startWriting(runId);

        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        int aboveThreshold = SampleAggregationWriter.FLUSH_THRESHOLD + 5;
        for (int i = 0; i < aboveThreshold; i++) {
            broker.publish(runId, bucket("label-" + i, base.plusSeconds(i)));
        }

        // At this point the buffer should have been flushed at least once (at threshold).
        Instant rangeEnd = base.plusSeconds(aboveThreshold + 1);
        int countBeforeStop = sampleRepository.findByRunId(runId, base, rangeEnd).size();
        assertTrue(countBeforeStop > 0,
                "Some rows must be flushed before stopWriting when threshold is exceeded");

        writer.stopWriting(runId, "COMPLETED");

        int countAfterStop = sampleRepository.findByRunId(runId, base, rangeEnd).size();
        assertEquals(aboveThreshold, countAfterStop,
                "All " + aboveThreshold + " rows must be stored after stopWriting");
    }

    // -------------------------------------------------------------------------
    // Idempotent startWriting
    // -------------------------------------------------------------------------

    @Test
    void startWriting_calledTwice_isIdempotent() {
        writer.startWriting(runId);
        writer.startWriting(runId); // second call must be a no-op

        Instant ts = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        broker.publish(runId, bucket("label", ts));
        writer.stopWriting(runId, "COMPLETED");

        // Exactly one row
        assertEquals(1, sampleRepository.findByRunId(runId, ts, ts.plusSeconds(1)).size(),
                "Duplicate startWriting must not cause duplicate rows");
    }

    // -------------------------------------------------------------------------
    // stopWriting for unknown runId is a no-op
    // -------------------------------------------------------------------------

    @Test
    void stopWriting_unknownRunId_isNoOp() {
        assertDoesNotThrow(() -> writer.stopWriting("run-does-not-exist", "COMPLETED"),
                "stopWriting for an unknown runId must not throw");
    }

    // -------------------------------------------------------------------------
    // No rows written if no buckets published
    // -------------------------------------------------------------------------

    @Test
    void noBucketsPublished_zeroRows() {
        writer.startWriting(runId);
        writer.stopWriting(runId, "COMPLETED");

        Instant now = Instant.now();
        assertEquals(0, sampleRepository.findByRunId(runId, now.minusSeconds(60), now.plusSeconds(60)).size(),
                "No rows should be written when no buckets are published");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int countSampleRows(String testRunId) {
        Instant now = Instant.now();
        return sampleRepository.findByRunId(testRunId, now.minusSeconds(60), now.plusSeconds(60)).size();
    }

    private static SampleBucket bucket(String label, Instant timestamp) {
        return new SampleBucket(timestamp, label, 50L, 2L,
                120.0, 30.0, 800.0, 200.0, 250.0, 300.0, 50.0);
    }
}
