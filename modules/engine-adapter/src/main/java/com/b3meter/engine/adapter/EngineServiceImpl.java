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
package com.b3meter.engine.adapter;

import com.b3meter.engine.service.EngineService;
import com.b3meter.engine.service.SampleStreamBroker;
import com.b3meter.engine.service.TestRunContext;
import com.b3meter.engine.service.TestRunContextRegistry;
import com.b3meter.engine.service.TestRunHandle;
import com.b3meter.engine.service.TestRunResult;
import com.b3meter.engine.service.UIBridge;
import com.b3meter.engine.service.http.HttpClientFactory;
import com.b3meter.engine.service.interpreter.NodeInterpreter;
import com.b3meter.engine.service.output.CsvMetricsOutput;
import com.b3meter.engine.service.output.InfluxDbMetricsOutput;
import com.b3meter.engine.service.output.JsonMetricsOutput;
import com.b3meter.engine.service.output.MetricsOutputManager;
import com.b3meter.engine.service.output.PrometheusMetricsOutput;
import com.b3meter.engine.service.plan.JmxParseException;
import com.b3meter.engine.service.plan.JmxTreeWalker;
import com.b3meter.engine.service.plan.PlanNode;
import com.b3meter.engine.service.shape.LoadShape;
import com.b3meter.engine.service.shape.LoadShapeController;
import com.b3meter.engine.service.shape.ShapeParser;
import com.b3meter.engine.service.VirtualUserExecutor;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Real implementation of {@link EngineService} that executes test plans end-to-end.
 *
 * <p>Wires together the execution pipeline:
 * <ol>
 *   <li>{@link TestRunContext} is constructed from the plan ID and overrides, then
 *       registered in {@link TestRunContextRegistry}.</li>
 *   <li>A {@link TestPlanExecutor} is created for the run.</li>
 *   <li>Virtual users are submitted via {@link TestPlanExecutor#execute}, which
 *       internally uses {@link com.b3meter.engine.service.VirtualUserExecutor}
 *       backed by virtual threads.</li>
 *   <li>Each virtual user runs the sampler loop, producing
 *       {@link SimulatedSampler.SampleResult} instances that are accumulated and
 *       then published to the {@link SampleStreamBroker}.</li>
 *   <li>On completion or stop, the context is removed from the registry and the
 *       {@link CompletableFuture} in the returned {@link TestRunHandle} is resolved.</li>
 * </ol>
 *
 * <h2>Stop / StopNow</h2>
 * <ul>
 *   <li>{@link #stopRun} sets {@code context.running = false}; the sampler loop
 *       checks {@link TestRunContext#isRunning()} and finishes naturally after the
 *       current iteration of each virtual user.</li>
 *   <li>{@link #stopRunNow} additionally interrupts the run's thread, causing the
 *       executor to shut down immediately.</li>
 * </ul>
 *
 * <h2>Configuration via Properties overrides</h2>
 * <ul>
 *   <li>{@code jmeter.threads} — number of virtual users (default: 1)</li>
 *   <li>{@code jmeter.duration} — run duration in seconds; 0 = run until plan completes (default: 0)</li>
 *   <li>{@code jmeter.shape} — load shape spec for dynamic VU adjustment (e.g., "ramp:0:100:60s")</li>
 * </ul>
 *
 * <p>Thread-safety: all public methods are thread-safe. Concurrent calls to
 * {@link #startRun} produce independent runs with separate contexts.
 */
public final class EngineServiceImpl implements EngineService {

    private static final Logger LOG = Logger.getLogger(EngineServiceImpl.class.getName());

    /** Property key for overriding the virtual-user count. */
    public static final String PROP_THREADS = "jmeter.threads";

    /** Property key for overriding the run duration in seconds. */
    public static final String PROP_DURATION = "jmeter.duration";

    /** Property key for configuring active metrics outputs (comma-separated: csv,json,influxdb,prometheus). */
    public static final String PROP_OUTPUTS = "jmeter.outputs";

    /**
     * Property key for specifying a load shape that dynamically adjusts VU count.
     *
     * <p>Format: {@code "type:params"} — see {@link ShapeParser} for details.
     * Examples:
     * <ul>
     *   <li>{@code "ramp:0:100:60s"} — ramp from 0 to 100 users over 60 seconds</li>
     *   <li>{@code "stages:0:10:30s,10:50:60s,50:0:30s"} — multi-stage profile</li>
     *   <li>{@code "constant:50:5m"} — hold 50 users for 5 minutes</li>
     *   <li>{@code "step:10:15s:100:120s"} — add 10 users every 15s up to 100</li>
     *   <li>{@code "sine:10:100:30s:120s"} — oscillate between 10-100 users</li>
     * </ul>
     *
     * <p>When set, the shape controller runs alongside the normal execution and
     * dynamically adjusts the VU count. The {@code jmeter.threads} property sets
     * the initial VU count but the shape overrides it as the test progresses.
     */
    public static final String PROP_SHAPE = "jmeter.shape";

    private static final int DEFAULT_THREADS  = 1;
    private static final long DEFAULT_DURATION = 0L;

    private final SampleStreamBroker broker;
    private final HttpClientFactory httpClientFactory;
    private final UIBridge uiBridge;

    /**
     * Executor that drives the per-run {@link TestPlanExecutor} on a background thread,
     * freeing the caller of {@link #startRun} immediately.
     */
    private final ExecutorService runExecutor;

    /**
     * Constructs an {@code EngineServiceImpl} with real HTTP execution support.
     *
     * @param broker            the broker to publish sample buckets to; must not be {@code null}
     * @param httpClientFactory the HTTP client for real sampler calls; may be {@code null}
     *                          to fall back to simulation mode (no network I/O)
     * @param uiBridge          UI bridge for engine → UI callbacks; must not be {@code null}
     */
    public EngineServiceImpl(SampleStreamBroker broker,
                             HttpClientFactory httpClientFactory,
                             UIBridge uiBridge) {
        this.broker            = Objects.requireNonNull(broker,    "broker must not be null");
        this.httpClientFactory = httpClientFactory; // nullable
        this.uiBridge          = Objects.requireNonNull(uiBridge,  "uiBridge must not be null");
        this.runExecutor       = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "engine-run-" + UUID.randomUUID().toString().substring(0, 8));
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Convenience constructor that uses {@link NoOpUIBridge} and simulation mode (no HTTP client).
     *
     * @param broker the broker to publish sample buckets to; must not be {@code null}
     */
    public EngineServiceImpl(SampleStreamBroker broker) {
        this(broker, null, NoOpUIBridge.INSTANCE);
    }

    // =========================================================================
    // EngineService
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * <p>Creates and registers a {@link TestRunContext}, submits the
     * {@link TestPlanExecutor} to a background thread, and returns a
     * {@link TestRunHandle} immediately. The returned {@code completion} future
     * resolves with a {@link TestRunResult} when the run finishes.
     */
    @Override
    public TestRunHandle startRun(String planId, Map<String, Object> treeData, Properties overrides) {
        if (planId == null || planId.isBlank()) {
            throw new IllegalArgumentException("planId must not be null or blank");
        }

        String runId = UUID.randomUUID().toString();
        Properties effectiveOverrides = overrides != null ? overrides : new Properties();
        int  virtualUsers    = intProperty(effectiveOverrides, PROP_THREADS,  DEFAULT_THREADS);
        long durationSeconds = longProperty(effectiveOverrides, PROP_DURATION, DEFAULT_DURATION);

        Instant startedAt = Instant.now();

        TestRunContext context = TestRunContext.builder()
                .runId(runId)
                .planPath(planId)
                .virtualUsers(Math.max(1, virtualUsers))
                .durationSeconds(Math.max(0L, durationSeconds))
                .uiBridge(uiBridge)
                .runProperties(effectiveOverrides)
                .startedAt(startedAt)
                .build();

        TestRunContextRegistry.register(context);
        context.setStatus(TestRunContext.TestRunStatus.RUNNING);
        context.setRunning(true);

        CompletableFuture<TestRunResult> completion = new CompletableFuture<>();

        runExecutor.submit(() -> executeRun(context, treeData, startedAt, completion));

        LOG.log(Level.INFO, "EngineServiceImpl: started run {0} for plan {1} with {2} virtual user(s)",
                new Object[]{runId, planId, virtualUsers});

        return new TestRunHandle(runId, startedAt, completion);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Signals the run to stop after the current sampler iteration of each
     * virtual user completes. Transitions status to {@code STOPPING}.
     */
    @Override
    public void stopRun(String runId) {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be null or blank");
        }
        TestRunContext ctx = TestRunContextRegistry.get(runId);
        if (ctx == null) {
            LOG.log(Level.WARNING, "stopRun: no active run found for id {0}", runId);
            return;
        }
        LOG.log(Level.INFO, "EngineServiceImpl: graceful stop requested for run {0}", runId);
        ctx.setStatus(TestRunContext.TestRunStatus.STOPPING);
        ctx.setRunning(false);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Interrupts all virtual-user threads immediately and transitions the run
     * to {@code STOPPED}.
     */
    @Override
    public void stopRunNow(String runId) {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be null or blank");
        }
        TestRunContext ctx = TestRunContextRegistry.get(runId);
        if (ctx == null) {
            LOG.log(Level.WARNING, "stopRunNow: no active run found for id {0}", runId);
            return;
        }
        LOG.log(Level.INFO, "EngineServiceImpl: immediate stop requested for run {0}", runId);
        ctx.setStatus(TestRunContext.TestRunStatus.STOPPING);
        ctx.setRunning(false);
        // Signal via run state so the background thread can interrupt itself
        ctx.putState("__stopNow__", Boolean.TRUE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestRunContext.TestRunStatus getRunStatus(String runId) {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be null or blank");
        }
        TestRunContext ctx = TestRunContextRegistry.get(runId);
        return ctx != null ? ctx.getStatus() : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<TestRunContext> activeRuns() {
        return TestRunContextRegistry.activeRuns();
    }

    // =========================================================================
    // Internal execution
    // =========================================================================

    private void executeRun(TestRunContext context,
                            Map<String, Object> treeData,
                            Instant startedAt,
                            CompletableFuture<TestRunResult> completion) {
        String runId = context.getRunId();
        AtomicLong totalSamples = new AtomicLong(0);
        AtomicLong errorCount   = new AtomicLong(0);

        // Subscribe a counter consumer to track totals for the result
        var countingConsumer = new CountingConsumer(totalSamples, errorCount);
        broker.subscribe(runId, countingConsumer);

        // Create and start the metrics output pipeline
        MetricsOutputManager outputManager = createOutputManager(context.getRunProperties());
        Map<String, String> outputConfig = buildOutputConfig(context.getRunProperties());
        outputManager.start(runId, outputConfig);
        broker.subscribe(runId, outputManager);

        // Parse load shape if configured
        LoadShapeController shapeController = null;
        String shapeSpec = context.getRunProperties().getProperty(PROP_SHAPE);

        try {
            // If treeData carries JMX content, use the NodeInterpreter pipeline.
            // Otherwise, fall back to the legacy TestPlanExecutor (map-based tree).
            String jmxContent = extractJmxContent(treeData);
            if (jmxContent != null && httpClientFactory != null) {
                PlanNode root = parseJmx(jmxContent, runId);
                if (root != null) {
                    NodeInterpreter interpreter = new NodeInterpreter(httpClientFactory, broker);
                    TestRunResult jmxResult = interpreter.execute(root, context);
                    // Mirror the jmxResult stats into the completion future
                    context.setStatus(TestRunContext.TestRunStatus.STOPPED);
                    context.setRunning(false);
                    completion.complete(jmxResult);
                    return;
                }
            }

            TestPlanExecutor executor = new TestPlanExecutor(context, broker, httpClientFactory);

            // If a load shape is specified, create and start the shape controller.
            // The shape controller runs alongside the normal execution, dynamically
            // adjusting VU count via VirtualUserExecutor.
            if (shapeSpec != null && !shapeSpec.isBlank()) {
                try {
                    LoadShape shape = ShapeParser.parse(shapeSpec);
                    VirtualUserExecutor vuExecutor = new VirtualUserExecutor(context);
                    shapeController = new LoadShapeController(shape, vuExecutor, () -> {
                        // Each shape-managed VU runs a single sampler iteration.
                        // The LoadShapeController wraps this in a while(!cancelled) loop.
                        return () -> {
                            if (!context.isRunning()) return;
                            executor.executeSingleSample(treeData);
                        };
                    });
                    shapeController.start();
                    LOG.log(Level.INFO,
                            "EngineServiceImpl: load shape controller started for run {0} with spec ''{1}''",
                            new Object[]{runId, shapeSpec});
                } catch (IllegalArgumentException e) {
                    LOG.log(Level.WARNING,
                            "EngineServiceImpl: invalid shape spec ''{0}'' for run {1}: {2} — ignoring shape",
                            new Object[]{shapeSpec, runId, e.getMessage()});
                    shapeController = null;
                }
            }

            executor.execute(treeData);

            Instant endedAt = Instant.now();
            TestRunContext.TestRunStatus finalStatus =
                    context.isRunning()
                            ? TestRunContext.TestRunStatus.STOPPED
                            : context.getStatus() == TestRunContext.TestRunStatus.STOPPING
                                    ? TestRunContext.TestRunStatus.STOPPED
                                    : context.getStatus();

            context.setStatus(TestRunContext.TestRunStatus.STOPPED);
            context.setRunning(false);

            TestRunResult result = new TestRunResult(
                    runId,
                    finalStatus == null ? TestRunContext.TestRunStatus.STOPPED : finalStatus,
                    startedAt,
                    endedAt,
                    totalSamples.get(),
                    errorCount.get(),
                    Duration.between(startedAt, endedAt)
            );

            LOG.log(Level.INFO,
                    "EngineServiceImpl: run {0} finished — status={1}, samples={2}, errors={3}",
                    new Object[]{runId, result.finalStatus(), result.totalSamples(), result.errorCount()});

            completion.complete(result);

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOG.log(Level.WARNING, "EngineServiceImpl: run {0} interrupted", runId);
            finishWithError(context, startedAt, totalSamples, errorCount, completion, ex);
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "EngineServiceImpl: run " + runId + " failed with exception", ex);
            finishWithError(context, startedAt, totalSamples, errorCount, completion, ex);
        } finally {
            if (shapeController != null) {
                shapeController.close();
                LOG.log(Level.INFO, "EngineServiceImpl: load shape controller stopped for run {0}", runId);
            }
            outputManager.stop();
            broker.unsubscribe(runId, outputManager);
            broker.unsubscribe(runId, countingConsumer);
            TestRunContextRegistry.remove(runId);
        }
    }

    private void finishWithError(TestRunContext context,
                                 Instant startedAt,
                                 AtomicLong totalSamples,
                                 AtomicLong errorCount,
                                 CompletableFuture<TestRunResult> completion,
                                 Exception cause) {
        Instant endedAt = Instant.now();
        context.setStatus(TestRunContext.TestRunStatus.ERROR);
        context.setRunning(false);
        long samples = totalSamples.get();
        long errors  = Math.min(errorCount.get(), samples);
        TestRunResult result = new TestRunResult(
                context.getRunId(),
                TestRunContext.TestRunStatus.ERROR,
                startedAt,
                endedAt,
                samples,
                errors,
                Duration.between(startedAt, endedAt)
        );
        completion.complete(result);
    }

    // =========================================================================
    // Metrics output pipeline
    // =========================================================================

    /**
     * Creates a {@link MetricsOutputManager} and registers the outputs specified
     * in the {@code jmeter.outputs} property.
     *
     * <p>The property value is a comma-separated list of output names:
     * {@code "csv"}, {@code "json"}, {@code "influxdb"}, {@code "prometheus"}.
     * Unknown names are logged and skipped. If the property is absent or empty,
     * no outputs are registered (the manager is still returned but acts as a
     * no-op consumer).
     *
     * @param overrides run properties containing {@code jmeter.outputs} and
     *                  backend-specific keys ({@code csv.file}, {@code influxdb.url}, etc.)
     * @return a new, unstarted {@link MetricsOutputManager}
     */
    private MetricsOutputManager createOutputManager(Properties overrides) {
        MetricsOutputManager manager = new MetricsOutputManager();
        String outputsValue = overrides.getProperty(PROP_OUTPUTS, "");
        if (outputsValue.isBlank()) {
            return manager;
        }

        String[] names = outputsValue.split(",");
        for (String raw : names) {
            String name = raw.trim().toLowerCase();
            if (name.isEmpty()) continue;
            switch (name) {
                case "csv"        -> manager.addOutput(new CsvMetricsOutput());
                case "json"       -> manager.addOutput(new JsonMetricsOutput());
                case "influxdb"   -> manager.addOutput(new InfluxDbMetricsOutput());
                case "prometheus" -> manager.addOutput(new PrometheusMetricsOutput());
                default -> LOG.log(Level.WARNING,
                        "EngineServiceImpl: unknown metrics output ''{0}'' — skipped", name);
            }
        }

        LOG.log(Level.INFO, "EngineServiceImpl: configured {0} metrics output(s): {1}",
                new Object[]{manager.getOutputs().size(), outputsValue.trim()});

        return manager;
    }

    /**
     * Builds a flat configuration map for {@link MetricsOutputManager#start} from
     * the run's {@link Properties}.
     *
     * <p>Extracts all output-related keys ({@code csv.*}, {@code json.*},
     * {@code influxdb.*}, {@code prometheus.*}) into a {@code Map<String, String>}.
     *
     * @param overrides the run properties
     * @return a configuration map for the output backends
     */
    private static Map<String, String> buildOutputConfig(Properties overrides) {
        Map<String, String> config = new HashMap<>();
        for (String key : overrides.stringPropertyNames()) {
            if (key.startsWith("csv.") || key.startsWith("json.")
                    || key.startsWith("influxdb.") || key.startsWith("prometheus.")) {
                config.put(key, overrides.getProperty(key));
            }
        }
        return config;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Extracts JMX XML content from {@code treeData} if present.
     *
     * <p>Callers may pass JMX content by setting {@code treeData.put("jmxContent", xmlString)}.
     * This enables the {@link NodeInterpreter} pipeline to be used instead of the legacy
     * map-based {@link TestPlanExecutor}.
     *
     * @param treeData plan data map; may be {@code null}
     * @return the JMX XML string, or {@code null} if not present
     */
    private static String extractJmxContent(Map<String, Object> treeData) {
        if (treeData == null) return null;
        Object raw = treeData.get("jmxContent");
        return (raw instanceof String s && !s.isBlank()) ? s : null;
    }

    /**
     * Parses the JMX XML string and returns the root {@link PlanNode}.
     * Logs and returns {@code null} on parse failure so the caller can fall back.
     */
    private static PlanNode parseJmx(String jmxContent, String runId) {
        try {
            return JmxTreeWalker.parse(jmxContent);
        } catch (JmxParseException e) {
            LOG.log(Level.WARNING,
                    "EngineServiceImpl: JMX parse failed for run {0}, falling back to legacy executor: {1}",
                    new Object[]{runId, e.getMessage()});
            return null;
        }
    }

    private static int intProperty(Properties props, String key, int defaultValue) {
        String val = props.getProperty(key);
        if (val == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long longProperty(Properties props, String key, long defaultValue) {
        String val = props.getProperty(key);
        if (val == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // =========================================================================
    // Inner types
    // =========================================================================

    /**
     * Lightweight consumer that increments sample/error counters from broker deliveries.
     */
    private static final class CountingConsumer
            implements com.b3meter.engine.service.SampleBucketConsumer {

        private final AtomicLong totalSamples;
        private final AtomicLong errorCount;

        CountingConsumer(AtomicLong totalSamples, AtomicLong errorCount) {
            this.totalSamples = totalSamples;
            this.errorCount   = errorCount;
        }

        @Override
        public void onBucket(com.b3meter.engine.service.SampleBucket bucket) {
            totalSamples.addAndGet(bucket.sampleCount());
            errorCount.addAndGet(bucket.errorCount());
        }
    }
}
