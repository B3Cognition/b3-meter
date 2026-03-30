package com.jmeternext.distributed.controller;

import com.google.protobuf.ByteString;
import com.jmeternext.engine.adapter.InMemorySampleStreamBroker;
import com.jmeternext.engine.service.HdrHistogramAccumulator;
import com.jmeternext.engine.service.SampleBucket;
import com.jmeternext.engine.service.SampleStreamBroker;
import com.jmeternext.worker.proto.SampleResultBatch;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Aggregates {@link SampleResultBatch} streams from multiple workers into a single
 * unified {@link SampleBucket} stream published to a {@link SampleStreamBroker}.
 *
 * <p>For each incoming proto batch from any worker the aggregator:
 * <ol>
 *   <li>Converts the proto {@link SampleResultBatch} to a domain {@link SampleBucket}.</li>
 *   <li>Publishes the bucket to the shared broker under the canonical {@code runId}.</li>
 * </ol>
 *
 * <p>Downstream consumers (SSE publisher, SLA evaluator, dashboard) subscribe to the
 * broker for the run and receive a merged stream — they need not know how many workers
 * contributed.
 *
 * <p>Accounting: the aggregator maintains per-run sample and error totals that accumulate
 * as batches arrive. These totals can be queried via {@link #totalSamples(String)} and
 * {@link #totalErrors(String)}.
 *
 * <p>Thread-safety: all public methods are thread-safe.
 */
public final class ResultAggregator {

    private static final Logger LOG = Logger.getLogger(ResultAggregator.class.getName());

    private final SampleStreamBroker broker;

    /** Accumulated sample count per runId. */
    private final ConcurrentHashMap<String, AtomicLong> sampleTotals = new ConcurrentHashMap<>();

    /** Accumulated error count per runId. */
    private final ConcurrentHashMap<String, AtomicLong> errorTotals = new ConcurrentHashMap<>();

    /** Active worker subscriptions per runId (for lifecycle tracking). */
    private final ConcurrentHashMap<String, List<String>> activeWorkers = new ConcurrentHashMap<>();

    /**
     * Per-run, per-label merged histograms for accurate percentile computation.
     * Outer key = runId, inner key = samplerLabel.
     * Synchronized via ConcurrentHashMap + HdrHistogramAccumulator's internal synchronization.
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, HdrHistogramAccumulator>>
            mergedHistograms = new ConcurrentHashMap<>();

    /**
     * Creates a {@link ResultAggregator} that publishes merged buckets to the given broker.
     *
     * @param broker the broker to publish aggregated buckets to; must not be {@code null}
     */
    public ResultAggregator(SampleStreamBroker broker) {
        this.broker = broker;
    }

    /**
     * Creates a {@link ResultAggregator} backed by a new {@link InMemorySampleStreamBroker}.
     * Convenient for standalone/test use.
     */
    public ResultAggregator() {
        this(new InMemorySampleStreamBroker());
    }

    // -------------------------------------------------------------------------
    // Subscription management
    // -------------------------------------------------------------------------

    /**
     * Subscribes to the result stream of {@code workerClient} for {@code runId}.
     *
     * <p>Once subscribed, every {@link SampleResultBatch} received from the worker is
     * translated to a {@link SampleBucket} and published to the broker.
     *
     * @param runId        the canonical run identifier
     * @param workerClient the worker whose results should be aggregated
     */
    public void subscribeWorker(String runId, WorkerClient workerClient) {
        String workerId = workerClient.getWorkerId();

        // Initialise counters for this run if needed
        sampleTotals.computeIfAbsent(runId, k -> new AtomicLong(0));
        errorTotals.computeIfAbsent(runId, k -> new AtomicLong(0));
        activeWorkers.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>()).add(workerId);

        LOG.log(Level.INFO, "ResultAggregator subscribing worker={0} for runId={1}",
                new Object[]{workerId, runId});

        workerClient.streamResults(
                runId,
                batch -> onBatchReceived(runId, workerId, batch),
                () -> onWorkerStreamComplete(runId, workerId),
                err -> onWorkerStreamError(runId, workerId, err));
    }

    // -------------------------------------------------------------------------
    // Accounting queries
    // -------------------------------------------------------------------------

    /**
     * Returns the total number of samples received from all workers for {@code runId}.
     *
     * @param runId the run to query
     * @return accumulated sample count; 0 if no batches received yet or run unknown
     */
    public long totalSamples(String runId) {
        AtomicLong counter = sampleTotals.get(runId);
        return counter == null ? 0L : counter.get();
    }

    /**
     * Returns the total number of error samples received from all workers for {@code runId}.
     *
     * @param runId the run to query
     * @return accumulated error count; 0 if no batches received yet or run unknown
     */
    public long totalErrors(String runId) {
        AtomicLong counter = errorTotals.get(runId);
        return counter == null ? 0L : counter.get();
    }

    /**
     * Returns the broker that receives aggregated buckets.
     *
     * @return the underlying {@link SampleStreamBroker}; never {@code null}
     */
    public SampleStreamBroker getBroker() {
        return broker;
    }

    /**
     * Clears all accounting state for the given run. Should be called after the run
     * ends and all consumers have deregistered.
     *
     * @param runId the run to clear
     */
    public void clearRun(String runId) {
        sampleTotals.remove(runId);
        errorTotals.remove(runId);
        activeWorkers.remove(runId);
        mergedHistograms.remove(runId);
    }

    // -------------------------------------------------------------------------
    // Internal callbacks
    // -------------------------------------------------------------------------

    private void onBatchReceived(String runId, String workerId, SampleResultBatch batch) {
        // Accumulate totals
        sampleTotals.computeIfAbsent(runId, k -> new AtomicLong(0))
                .addAndGet(batch.getSampleCount());
        errorTotals.computeIfAbsent(runId, k -> new AtomicLong(0))
                .addAndGet(batch.getErrorCount());

        // Convert proto timestamp → Instant
        Instant timestamp = batch.hasTimestamp()
                ? Instant.ofEpochSecond(
                        batch.getTimestamp().getSeconds(),
                        batch.getTimestamp().getNanos())
                : Instant.now();

        String label = batch.getSamplerLabel();
        double avg = batch.getAvgResponseTime();

        // Check for histogram data (new protocol) vs legacy percentiles
        ByteString histData = batch.getHdrHistogram();
        SampleBucket bucket;

        if (histData != null && !histData.isEmpty()) {
            // Merge the worker's histogram into the per-run, per-label accumulator
            HdrHistogramAccumulator workerHist =
                    HdrHistogramAccumulator.fromBytes(histData.toByteArray());

            ConcurrentHashMap<String, HdrHistogramAccumulator> labelMap =
                    mergedHistograms.computeIfAbsent(runId, k -> new ConcurrentHashMap<>());
            HdrHistogramAccumulator merged =
                    labelMap.computeIfAbsent(label, k -> new HdrHistogramAccumulator());
            merged.merge(workerHist);

            // Compute accurate percentiles from the merged histogram
            double p90 = merged.getPercentile(90.0);
            double p95 = merged.getPercentile(95.0);
            double p99 = merged.getPercentile(99.0);
            double min = workerHist.getMin();
            double max = workerHist.getMax();

            bucket = new SampleBucket(
                    timestamp,
                    label,
                    batch.getSampleCount(),
                    batch.getErrorCount(),
                    avg,
                    min,
                    max,
                    p90,
                    p95,
                    p99,
                    /* samplesPerSecond */ batch.getSampleCount()
            );
        } else {
            // Legacy fallback: use pre-computed percentiles from the proto
            Map<String, Double> pct = batch.getPercentilesMap();
            double p90 = pct.getOrDefault("p90", 0.0);
            double p95 = pct.getOrDefault("p95", 0.0);
            double p99 = pct.getOrDefault("p99", 0.0);

            bucket = new SampleBucket(
                    timestamp,
                    label,
                    batch.getSampleCount(),
                    batch.getErrorCount(),
                    avg,
                    /* minResponseTime */ avg,
                    /* maxResponseTime */ Math.max(avg, p99),
                    p90,
                    p95,
                    p99,
                    /* samplesPerSecond */ batch.getSampleCount()
            );
        }

        try {
            broker.publish(runId, bucket);
        } catch (Exception ex) {
            LOG.log(Level.WARNING,
                    "Failed to publish bucket to broker for runId={0}, worker={1}: {2}",
                    new Object[]{runId, workerId, ex.getMessage()});
        }
    }

    private void onWorkerStreamComplete(String runId, String workerId) {
        LOG.log(Level.INFO,
                "Worker={0} stream completed for runId={1}", new Object[]{workerId, runId});
        List<String> workers = activeWorkers.get(runId);
        if (workers != null) {
            workers.remove(workerId);
        }
    }

    private void onWorkerStreamError(String runId, String workerId, Throwable err) {
        LOG.log(Level.WARNING,
                "Worker={0} stream error for runId={1}: {2}",
                new Object[]{workerId, runId, err.getMessage()});
        List<String> workers = activeWorkers.get(runId);
        if (workers != null) {
            workers.remove(workerId);
        }
    }
}
