package com.jmeternext.engine.adapter;

import com.jmeternext.engine.service.SampleBucket;
import com.jmeternext.engine.service.SampleStreamBroker;
import com.jmeternext.engine.service.TestRunContext;
import com.jmeternext.engine.service.VirtualUserExecutor;
import com.jmeternext.engine.service.http.HttpClientFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orchestrates execution of a test plan tree.
 *
 * <p>Given a {@code treeData} map (the JMX-equivalent object model) it extracts
 * Thread Groups, creates the configured number of virtual users via
 * {@link VirtualUserExecutor}, runs each virtual user through its sampler loop,
 * and publishes aggregated {@link SampleBucket} results to the
 * {@link SampleStreamBroker} on a per-second cadence.
 *
 * <h2>Plan tree format</h2>
 * The {@code treeData} map is expected to optionally carry:
 * <ul>
 *   <li>{@code "threadGroups"} — a {@code List<Map<String,Object>>} where each
 *       entry may define {@code "threads"} (int), {@code "iterations"} (int),
 *       {@code "targetUrl"} (String), and {@code "label"} (String).</li>
 * </ul>
 * If the map is empty or contains no thread groups, a single-thread default
 * thread group is synthesised so a run always produces at least some activity.
 *
 * <p>Thread-safety: instances are single-use. Create a new {@code TestPlanExecutor}
 * for each test run.
 */
public final class TestPlanExecutor {

    private static final Logger LOG = Logger.getLogger(TestPlanExecutor.class.getName());

    /** Default target URL when the plan tree carries no explicit URL. */
    static final String DEFAULT_TARGET_URL = "http://localhost:8080/";

    /** Default sampler label used when the plan tree carries no explicit label. */
    static final String DEFAULT_SAMPLER_LABEL = "HTTP Request";

    /** Default number of iterations per virtual user when not specified in the plan. */
    static final int DEFAULT_ITERATIONS = 5;

    /** Minimum simulated response time in ms (floor for offline/simulation mode). */
    private static final long MIN_SIMULATED_RESPONSE_MS = 10L;

    /** Maximum simulated response time in ms when no real HTTP client is provided. */
    private static final long MAX_SIMULATED_RESPONSE_MS = 80L;

    private final TestRunContext context;
    private final SampleStreamBroker broker;
    private final HttpClientFactory httpClientFactory;

    /** Collected raw sample results, accumulated across all virtual users. */
    private final CopyOnWriteArrayList<SimulatedSampler.SampleResult> rawResults =
            new CopyOnWriteArrayList<>();

    /**
     * Constructs a {@code TestPlanExecutor}.
     *
     * @param context           the run context for this execution; must not be {@code null}
     * @param broker            the broker to publish {@link SampleBucket} results to; must not be {@code null}
     * @param httpClientFactory the HTTP client to use for real samplers; may be {@code null}
     *                          to enable pure-simulation mode (no network calls)
     * @throws NullPointerException if {@code context} or {@code broker} is {@code null}
     */
    public TestPlanExecutor(TestRunContext context,
                            SampleStreamBroker broker,
                            HttpClientFactory httpClientFactory) {
        this.context           = Objects.requireNonNull(context, "context must not be null");
        this.broker            = Objects.requireNonNull(broker,  "broker must not be null");
        this.httpClientFactory = httpClientFactory; // nullable — null triggers simulation mode
    }

    /**
     * Executes the test plan described by {@code treeData}.
     *
     * <p>For each thread group in the plan:
     * <ol>
     *   <li>Creates a {@link VirtualUserExecutor} scoped to this run context.</li>
     *   <li>Submits one virtual-user task per configured thread.</li>
     *   <li>Each virtual user executes {@code iterations} sampler calls.</li>
     *   <li>After all VUs complete, publishes a final aggregated {@link SampleBucket}.</li>
     * </ol>
     *
     * @param treeData plan element tree; may be {@code null} or empty (defaults apply)
     * @throws InterruptedException if the calling thread is interrupted while waiting
     *                              for virtual users to complete
     */
    public void execute(Map<String, Object> treeData) throws InterruptedException {
        List<ThreadGroupConfig> threadGroups = extractThreadGroups(treeData);
        LOG.log(Level.INFO, "TestPlanExecutor: executing {0} thread group(s) for run {1}",
                new Object[]{threadGroups.size(), context.getRunId()});

        context.getUiBridge().onTestStarted(context);

        for (ThreadGroupConfig tg : threadGroups) {
            executeThreadGroup(tg);
        }

        // Publish a final aggregate bucket covering all collected samples
        publishFinalBucket();

        context.getUiBridge().onTestEnded(context);
        LOG.log(Level.INFO, "TestPlanExecutor: run {0} completed — {1} total sample(s)",
                new Object[]{context.getRunId(), rawResults.size()});
    }

    /**
     * Returns the total number of raw sample results collected so far.
     * Useful for assertions in tests without subscribing to the broker.
     */
    public int sampleCount() {
        return rawResults.size();
    }

    // -------------------------------------------------------------------------
    // Thread group execution
    // -------------------------------------------------------------------------

    private void executeThreadGroup(ThreadGroupConfig tg) throws InterruptedException {
        LOG.log(Level.FINE, "Thread group [{0}]: {1} thread(s), {2} iteration(s), url={3}",
                new Object[]{tg.label, tg.threads, tg.iterations, tg.targetUrl});

        CountDownLatch latch = new CountDownLatch(tg.threads);
        List<Future<?>> futures = new ArrayList<>();

        try (VirtualUserExecutor vuExecutor = new VirtualUserExecutor(context)) {
            for (int i = 0; i < tg.threads; i++) {
                final int vuIndex = i;
                Future<?> f = vuExecutor.submitVirtualUser(() -> {
                    try {
                        runVirtualUser(tg, vuIndex);
                    } finally {
                        latch.countDown();
                    }
                });
                futures.add(f);
            }

            // Determine wait timeout: configured duration or a safe default
            long timeoutSeconds = context.getDurationSeconds() > 0
                    ? context.getDurationSeconds() + 30 // grace period
                    : (long) tg.threads * tg.iterations * 5 + 30;

            boolean completed = latch.await(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                LOG.log(Level.WARNING,
                        "Thread group [{0}] did not complete within {1}s timeout; shutting down now",
                        new Object[]{tg.label, timeoutSeconds});
                vuExecutor.shutdownNow();
            } else {
                vuExecutor.shutdownGracefully(5, TimeUnit.SECONDS);
            }
        }
    }

    private void runVirtualUser(ThreadGroupConfig tg, int vuIndex) {
        context.getUiBridge().onThreadStarted(
                context,
                "VU-" + tg.label + "-" + vuIndex,
                vuIndex + 1
        );

        for (int iter = 0; iter < tg.iterations; iter++) {
            if (Thread.currentThread().isInterrupted()) {
                LOG.log(Level.FINE, "VU {0} interrupted at iteration {1}", new Object[]{vuIndex, iter});
                break;
            }

            SimulatedSampler.SampleResult result = executeSample(tg);
            rawResults.add(result);

            context.getUiBridge().onSampleReceived(
                    context,
                    result.label(),
                    result.totalTimeMs(),
                    result.success()
            );
        }
    }

    private SimulatedSampler.SampleResult executeSample(ThreadGroupConfig tg) {
        if (httpClientFactory != null) {
            // Real HTTP execution
            SimulatedSampler sampler = new SimulatedSampler(httpClientFactory, tg.targetUrl, tg.label);
            return sampler.execute();
        } else {
            // Simulation mode: generate plausible timing without network I/O
            long responseMs = MIN_SIMULATED_RESPONSE_MS
                    + (long) (Math.random() * (MAX_SIMULATED_RESPONSE_MS - MIN_SIMULATED_RESPONSE_MS));
            try {
                Thread.sleep(responseMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new SimulatedSampler.SampleResult(
                    tg.label,
                    true,
                    200,
                    0L,
                    responseMs / 2,
                    responseMs,
                    512
            );
        }
    }

    // -------------------------------------------------------------------------
    // Bucket publication
    // -------------------------------------------------------------------------

    private void publishFinalBucket() {
        List<SimulatedSampler.SampleResult> snapshot = new ArrayList<>(rawResults);
        if (snapshot.isEmpty()) {
            return;
        }

        // Group by label and publish one bucket per label
        snapshot.stream()
                .map(SimulatedSampler.SampleResult::label)
                .distinct()
                .forEach(label -> publishBucketForLabel(label, snapshot));
    }

    private void publishBucketForLabel(String label,
                                       List<SimulatedSampler.SampleResult> allResults) {
        List<Long> times = allResults.stream()
                .filter(r -> label.equals(r.label()))
                .map(SimulatedSampler.SampleResult::totalTimeMs)
                .sorted()
                .toList();

        if (times.isEmpty()) {
            return;
        }

        long errorCount = allResults.stream()
                .filter(r -> label.equals(r.label()) && !r.success())
                .count();

        double avg = times.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double min = times.getFirst();
        double max = times.getLast();
        double p90 = percentile(times, 90);
        double p95 = percentile(times, 95);
        double p99 = percentile(times, 99);

        SampleBucket bucket = new SampleBucket(
                Instant.now(),
                label,
                times.size(),
                errorCount,
                avg,
                min,
                max,
                p90,
                p95,
                p99,
                times.size() // samplesPerSecond ≈ total for a terminal bucket
        );

        broker.publish(context.getRunId(), bucket);
        context.getUiBridge().onSample(context, times.size(), (double) errorCount / times.size() * 100.0);
    }

    private static double percentile(List<Long> sortedValues, int pct) {
        if (sortedValues.isEmpty()) {
            return 0.0;
        }
        int index = (int) Math.ceil(pct / 100.0 * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }

    // -------------------------------------------------------------------------
    // Plan parsing
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<ThreadGroupConfig> extractThreadGroups(Map<String, Object> treeData) {
        if (treeData == null || treeData.isEmpty()) {
            return List.of(defaultThreadGroup());
        }

        Object tgRaw = treeData.get("threadGroups");
        if (!(tgRaw instanceof List)) {
            return List.of(defaultThreadGroup());
        }

        List<?> tgList = (List<?>) tgRaw;
        if (tgList.isEmpty()) {
            return List.of(defaultThreadGroup());
        }

        List<ThreadGroupConfig> result = new ArrayList<>();
        for (Object item : tgList) {
            if (item instanceof Map) {
                result.add(parseThreadGroup((Map<String, Object>) item));
            }
        }
        return result.isEmpty() ? List.of(defaultThreadGroup()) : Collections.unmodifiableList(result);
    }

    private ThreadGroupConfig parseThreadGroup(Map<String, Object> map) {
        int threads = context.getVirtualUsers();
        Object rawThreads = map.get("threads");
        if (rawThreads instanceof Number) {
            threads = Math.max(1, ((Number) rawThreads).intValue());
        }

        int iterations = DEFAULT_ITERATIONS;
        Object rawIter = map.get("iterations");
        if (rawIter instanceof Number) {
            iterations = Math.max(1, ((Number) rawIter).intValue());
        }

        String url = DEFAULT_TARGET_URL;
        Object rawUrl = map.get("targetUrl");
        if (rawUrl instanceof String s && !s.isBlank()) {
            url = s;
        }

        String label = DEFAULT_SAMPLER_LABEL;
        Object rawLabel = map.get("label");
        if (rawLabel instanceof String s && !s.isBlank()) {
            label = s;
        }

        return new ThreadGroupConfig(threads, iterations, url, label);
    }

    private ThreadGroupConfig defaultThreadGroup() {
        return new ThreadGroupConfig(
                context.getVirtualUsers(),
                DEFAULT_ITERATIONS,
                DEFAULT_TARGET_URL,
                DEFAULT_SAMPLER_LABEL
        );
    }

    // -------------------------------------------------------------------------
    // Inner config record
    // -------------------------------------------------------------------------

    private record ThreadGroupConfig(
            int threads,
            int iterations,
            String targetUrl,
            String label
    ) {}
}
