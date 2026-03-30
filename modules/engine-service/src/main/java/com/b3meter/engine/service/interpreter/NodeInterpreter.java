package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.SampleBucket;
import com.jmeternext.engine.service.SampleStreamBroker;
import com.jmeternext.engine.service.TestRunContext;
import com.jmeternext.engine.service.TestRunResult;
import com.jmeternext.engine.service.VirtualUserExecutor;
import com.jmeternext.engine.service.http.HttpClientFactory;
import com.jmeternext.engine.service.plan.PlanNode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Core tree-walking interpreter that executes a parsed JMX {@link PlanNode} tree.
 *
 * <p>The interpreter walks the plan tree top-down, dispatching each node to the
 * appropriate executor sub-component:
 * <ul>
 *   <li>{@code TestPlan} — root node; iterates direct children to find {@code ThreadGroup}s.</li>
 *   <li>{@code ThreadGroup} — spawns N virtual users; each VU walks the ThreadGroup's children.</li>
 *   <li>{@code HTTPSamplerProxy}, {@code HTTPSampler} → {@link HttpSamplerExecutor}</li>
 *   <li>{@code WebSocketSampler} → {@link WebSocketSamplerExecutor}</li>
 *   <li>{@code SSESampler} → {@link SSESamplerExecutor}</li>
 *   <li>{@code MQTTSampler} → {@link MQTTSamplerExecutor}</li>
 *   <li>{@code GrpcSampler} → {@link GrpcSamplerExecutor}</li>
 *   <li>{@code HLSSampler} → {@link HLSSamplerExecutor}</li>
 *   <li>{@code WebRTCSampler} → {@link WebRTCSamplerExecutor}</li>
 *   <li>{@code DASHSampler} → {@link DASHSamplerExecutor}</li>
 *   <li>{@code FTPSampler} → {@link FTPSamplerExecutor}</li>
 *   <li>{@code LDAPSampler} → {@link LDAPSamplerExecutor}</li>
 *   <li>{@code JSR223Sampler} → {@link JSR223SamplerExecutor}</li>
 *   <li>{@code BeanShellSampler} → {@link BeanShellSamplerExecutor}</li>
 *   <li>{@code OSProcessSampler} → {@link OSProcessSamplerExecutor}</li>
 *   <li>{@code DebugSampler} → {@link DebugSamplerExecutor}</li>
 *   <li>{@code PublisherSampler} → {@link JMSSamplerExecutor} (stub)</li>
 *   <li>{@code SubscriberSampler} → {@link JMSSamplerExecutor} (stub)</li>
 *   <li>{@code SOAPSampler}, {@code SOAPSampler2} → {@link SOAPSamplerExecutor}</li>
 *   <li>{@code MailReaderSampler} → {@link MailReaderSamplerExecutor} (stub)</li>
 *   <li>{@code BSFSampler} → {@link BSFSamplerExecutor} (delegates to JSR223)</li>
 *   <li>{@code AccessLogSampler} → {@link AccessLogSamplerExecutor}</li>
 *   <li>{@code AjpSampler} → {@link AJPSamplerExecutor} (stub)</li>
 *   <li>{@code JUnitSampler} → {@link JUnitSamplerExecutor}</li>
 *   <li>{@code LoopController} → {@link LoopControllerExecutor}</li>
 *   <li>{@code IfController} → {@link IfControllerExecutor}</li>
 *   <li>{@code TransactionController} — transparent wrapper; children are executed in sequence.</li>
 *   <li>{@code ResponseAssertion} → {@link AssertionExecutor}</li>
 *   <li>{@code ConstantTimer}, {@code GaussianRandomTimer}, {@code UniformRandomTimer} → {@link TimerExecutor}</li>
 *   <li>{@code RegexExtractor}, {@code JSONPostProcessor}, {@code JSONPathExtractor} → {@link ExtractorExecutor}</li>
 * </ul>
 *
 * <p>Each VU maintains its own mutable variable map ({@code Map<String,String>}) that is
 * populated by extractors and read by subsequent samplers and controllers.
 *
 * <p>Collected {@link SampleResult}s are published as {@link SampleBucket}s to the
 * {@link SampleStreamBroker} after each ThreadGroup completes.
 *
 * <p>Only JDK types are used — no Spring, no external libraries
 * (Constitution Principle I: framework-free).
 */
public final class NodeInterpreter {

    private static final Logger LOG = Logger.getLogger(NodeInterpreter.class.getName());

    /** Default timeout factor used when no explicit duration is set (5s per VU-iteration). */
    private static final long TIMEOUT_PER_VU_ITER_S = 5L;

    private final HttpClientFactory httpClientFactory;
    private final SampleStreamBroker broker;
    private final TimerExecutor timerExecutor;
    private final HttpSamplerExecutor httpExecutor;

    // Lazily initialised to avoid passing 'this' in the constructor (JDK 21 this-escape warning)
    private LoopControllerExecutor       loopExecutor;
    private IfControllerExecutor         ifExecutor;
    private ForEachControllerExecutor    forEachExecutor;
    private ThroughputControllerExecutor throughputExecutor;
    private OnceOnlyControllerExecutor   onceOnlyExecutor;
    private RecordingControllerExecutor  recordingExecutor;
    private RuntimeControllerExecutor    runtimeExecutor;
    private SwitchControllerExecutor     switchExecutor;
    private RandomControllerExecutor     randomCtrlExecutor;
    private InterleaveControllerExecutor interleaveExecutor;
    private RandomOrderControllerExecutor randomOrderExecutor;
    private ModuleControllerExecutor     moduleExecutor;
    private IncludeControllerExecutor    includeExecutor;

    /** Root node of the current test plan — set during {@link #execute} for ModuleController path resolution. */
    private PlanNode planRoot;

    /**
     * Constructs a {@code NodeInterpreter}.
     *
     * @param httpClientFactory HTTP client for real sampler execution; must not be {@code null}
     * @param broker            sample stream broker for publishing results; must not be {@code null}
     * @throws NullPointerException if either argument is {@code null}
     */
    public NodeInterpreter(HttpClientFactory httpClientFactory, SampleStreamBroker broker) {
        this.httpClientFactory = Objects.requireNonNull(
                httpClientFactory, "httpClientFactory must not be null");
        this.broker = Objects.requireNonNull(broker, "broker must not be null");
        this.timerExecutor  = new TimerExecutor();
        this.httpExecutor   = new HttpSamplerExecutor(httpClientFactory);
        // loopExecutor and ifExecutor are lazily created on first use to avoid
        // passing 'this' before the constructor completes (this-escape warning)
    }

    private LoopControllerExecutor loopExecutor() {
        if (loopExecutor == null) loopExecutor = new LoopControllerExecutor(this);
        return loopExecutor;
    }

    private IfControllerExecutor ifExecutor() {
        if (ifExecutor == null) ifExecutor = new IfControllerExecutor(this);
        return ifExecutor;
    }

    private ForEachControllerExecutor forEachExecutor() {
        if (forEachExecutor == null) forEachExecutor = new ForEachControllerExecutor(this);
        return forEachExecutor;
    }

    private ThroughputControllerExecutor throughputExecutor() {
        if (throughputExecutor == null) throughputExecutor = new ThroughputControllerExecutor(this);
        return throughputExecutor;
    }

    private OnceOnlyControllerExecutor onceOnlyExecutor() {
        if (onceOnlyExecutor == null) onceOnlyExecutor = new OnceOnlyControllerExecutor(this);
        return onceOnlyExecutor;
    }

    private RecordingControllerExecutor recordingExecutor() {
        if (recordingExecutor == null) recordingExecutor = new RecordingControllerExecutor(this);
        return recordingExecutor;
    }

    private RuntimeControllerExecutor runtimeExecutor() {
        if (runtimeExecutor == null) runtimeExecutor = new RuntimeControllerExecutor(this);
        return runtimeExecutor;
    }

    private SwitchControllerExecutor switchExecutor() {
        if (switchExecutor == null) switchExecutor = new SwitchControllerExecutor(this);
        return switchExecutor;
    }

    private RandomControllerExecutor randomCtrlExecutor() {
        if (randomCtrlExecutor == null) randomCtrlExecutor = new RandomControllerExecutor(this);
        return randomCtrlExecutor;
    }

    private InterleaveControllerExecutor interleaveExecutor() {
        if (interleaveExecutor == null) interleaveExecutor = new InterleaveControllerExecutor(this);
        return interleaveExecutor;
    }

    private RandomOrderControllerExecutor randomOrderExecutor() {
        if (randomOrderExecutor == null) randomOrderExecutor = new RandomOrderControllerExecutor(this);
        return randomOrderExecutor;
    }

    private ModuleControllerExecutor moduleExecutor() {
        if (moduleExecutor == null) moduleExecutor = new ModuleControllerExecutor(this);
        return moduleExecutor;
    }

    private IncludeControllerExecutor includeExecutor() {
        if (includeExecutor == null) includeExecutor = new IncludeControllerExecutor(this);
        return includeExecutor;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Executes the test plan rooted at {@code root} within the supplied {@code context}.
     *
     * <p>The root node is expected to be either {@code jmeterTestPlan} (the document root
     * produced by {@link com.jmeternext.engine.service.plan.JmxTreeWalker}) or
     * {@code TestPlan}.  The method walks direct children to find {@code ThreadGroup} nodes
     * and executes each one, then returns an aggregate {@link TestRunResult}.
     *
     * @param root    root node of the parsed plan tree; must not be {@code null}
     * @param context run context carrying VU count, duration, and run-id; must not be {@code null}
     * @return aggregate result; never {@code null}
     * @throws InterruptedException if the calling thread is interrupted while waiting for VUs
     */
    public TestRunResult execute(PlanNode root, TestRunContext context)
            throws InterruptedException {
        Objects.requireNonNull(root,    "root must not be null");
        Objects.requireNonNull(context, "context must not be null");

        // Store root for ModuleController path resolution
        this.planRoot = root;

        Instant startedAt = context.getStartedAt();
        CopyOnWriteArrayList<SampleResult> allResults = new CopyOnWriteArrayList<>();

        // The document root (jmeterTestPlan) contains TestPlan as its direct child;
        // TestPlan in turn contains ThreadGroups in its children list.
        List<PlanNode> topLevel = root.getChildren();

        // Walk to find TestPlan (or treat root's children directly as thread groups if root IS TestPlan)
        List<PlanNode> planChildren = resolveTestPlanChildren(root, topLevel);

        for (PlanNode node : planChildren) {
            if ("ThreadGroup".equals(node.getTestClass())) {
                executeThreadGroup(node, context, allResults);
            }
            // Other top-level elements (ConfigTestElement, etc.) are silently skipped
        }

        // Publish a final aggregate bucket for each unique sampler label
        publishBuckets(context.getRunId(), allResults);

        Instant endedAt = Instant.now();
        long totalSamples = allResults.size();
        long errorCount   = allResults.stream().filter(r -> !r.isSuccess()).count();

        return new TestRunResult(
                context.getRunId(),
                TestRunContext.TestRunStatus.STOPPED,
                startedAt,
                endedAt,
                totalSamples,
                errorCount,
                Duration.between(startedAt, endedAt)
        );
    }

    // =========================================================================
    // Package-visible: used by sub-executors
    // =========================================================================

    /**
     * Executes a list of nodes in sequence within a single VU variable scope.
     *
     * <p>Called by {@link LoopControllerExecutor} and {@link IfControllerExecutor}
     * to recurse into their child lists.
     *
     * @param children  ordered list of child nodes to execute
     * @param variables mutable VU variable map
     * @return list of all {@link SampleResult}s produced
     */
    List<SampleResult> executeChildren(List<PlanNode> children, Map<String, String> variables) {
        return executeChildren(children, variables, null);
    }

    List<SampleResult> executeChildren(List<PlanNode> children, Map<String, String> variables,
                                        String runId) {
        List<SampleResult> results = new ArrayList<>();
        SampleResult lastResult = null;

        for (PlanNode child : children) {
            if (Thread.currentThread().isInterrupted()) break;

            List<SampleResult> produced = dispatchNode(child, variables, lastResult);
            results.addAll(produced);
            if (!produced.isEmpty()) {
                lastResult = produced.get(produced.size() - 1);
                // Publish each sample immediately for real-time metrics
                if (runId != null) {
                    for (SampleResult sr : produced) {
                        SampleBucket bucket = new SampleBucket(
                                Instant.now(),
                                sr.getLabel(),
                                1,
                                sr.isSuccess() ? 0 : 1,
                                sr.getTotalTimeMs(),
                                sr.getTotalTimeMs(),
                                sr.getTotalTimeMs(),
                                sr.getTotalTimeMs(),
                                sr.getTotalTimeMs(),
                                sr.getTotalTimeMs(),
                                1
                        );
                        broker.publish(runId, bucket);
                    }
                }
            }
        }
        return results;
    }

    // =========================================================================
    // ThreadGroup execution
    // =========================================================================

    private void executeThreadGroup(PlanNode tg,
                                     TestRunContext context,
                                     CopyOnWriteArrayList<SampleResult> collector)
            throws InterruptedException {

        int numThreads = tg.getIntProp("ThreadGroup.num_threads", context.getVirtualUsers());
        numThreads = Math.max(1, numThreads);

        // Read loop count from the ThreadGroup's embedded LoopController
        int loops = extractThreadGroupLoops(tg);

        LOG.log(Level.INFO, "NodeInterpreter: ThreadGroup [{0}] — {1} VU(s), {2} loop(s)",
                new Object[]{tg.getTestName(), numThreads, loops});

        CountDownLatch latch = new CountDownLatch(numThreads);

        try (VirtualUserExecutor vuExec = new VirtualUserExecutor(context)) {
            for (int i = 0; i < numThreads; i++) {
                final int vuIdx = i;
                vuExec.submitVirtualUser(() -> {
                    try {
                        long durationMs = context.getDurationSeconds() > 0
                                ? context.getDurationSeconds() * 1000L : 0;
                        List<SampleResult> vuResults = runVirtualUser(tg, loops, durationMs, context.getRunId());
                        collector.addAll(vuResults);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            long timeoutS = computeTimeout(context, numThreads, loops);
            boolean done = latch.await(timeoutS, TimeUnit.SECONDS);
            if (!done) {
                LOG.log(Level.WARNING,
                        "NodeInterpreter: ThreadGroup [{0}] timed out after {1}s",
                        new Object[]{tg.getTestName(), timeoutS});
                vuExec.shutdownNow();
            } else {
                vuExec.shutdownGracefully(5, TimeUnit.SECONDS);
            }
        }
    }

    private List<SampleResult> runVirtualUser(PlanNode tg, int loops, long durationMs) {
        return runVirtualUser(tg, loops, durationMs, null);
    }

    private List<SampleResult> runVirtualUser(PlanNode tg, int loops, long durationMs, String runId) {
        List<SampleResult> results = new ArrayList<>();
        Map<String, String> variables = new HashMap<>();
        long startTime = System.currentTimeMillis();

        for (int loop = 0; loop < loops; loop++) {
            if (Thread.currentThread().isInterrupted()) break;
            // Duration-based stop: if durationSeconds is set, stop after elapsed time
            if (durationMs > 0 && (System.currentTimeMillis() - startTime) >= durationMs) break;
            List<SampleResult> iterResults = executeChildren(tg.getChildren(), variables, runId);
            results.addAll(iterResults);
        }
        return results;
    }

    // =========================================================================
    // Node dispatch
    // =========================================================================

    /**
     * Dispatches a single node to the appropriate executor and returns produced results.
     *
     * <p>Timers and extractors do not produce a {@link SampleResult} of their own but
     * are executed for their side effects (delay, variable mutation). They return an
     * empty list.
     *
     * @param node       the node to execute
     * @param variables  mutable VU variable map
     * @param lastResult the most recent sample result (needed by assertions / extractors)
     * @return list of produced {@link SampleResult}s; never {@code null}
     */
    private List<SampleResult> dispatchNode(PlanNode node,
                                             Map<String, String> variables,
                                             SampleResult lastResult) {
        String testClass = node.getTestClass();

        return switch (testClass) {
            case "HTTPSamplerProxy",
                 "HTTPSampler" -> {
                applyPreProcessors(node.getChildren(), variables, lastResult);
                SampleResult r = httpExecutor.execute(node, variables);
                applyPostProcessors(node.getChildren(), r, variables);
                yield List.of(r);
            }

            case "WebSocketSampler" -> {
                applyPreProcessors(node.getChildren(), variables, lastResult);
                SampleResult r = new SampleResult(node.getTestName());
                WebSocketSamplerExecutor.execute(node, r, variables);
                applyPostProcessors(node.getChildren(), r, variables);
                yield List.of(r);
            }

            case "SSESampler" -> {
                applyPreProcessors(node.getChildren(), variables, lastResult);
                SampleResult r = new SampleResult(node.getTestName());
                SSESamplerExecutor.execute(node, r, variables, httpClientFactory);
                applyPostProcessors(node.getChildren(), r, variables);
                yield List.of(r);
            }

            case "MQTTSampler" -> {
                applyPreProcessors(node.getChildren(), variables, lastResult);
                SampleResult r = new SampleResult(node.getTestName());
                MQTTSamplerExecutor.execute(node, r, variables);
                applyPostProcessors(node.getChildren(), r, variables);
                yield List.of(r);
            }

            case "GrpcSampler" -> {
                applyPreProcessors(node.getChildren(), variables, lastResult);
                SampleResult r = new SampleResult(node.getTestName());
                GrpcSamplerExecutor.execute(node, r, variables, httpClientFactory);
                applyPostProcessors(node.getChildren(), r, variables);
                yield List.of(r);
            }

            case "HLSSampler" -> {
                applyPreProcessors(node.getChildren(), variables, lastResult);
                SampleResult r = new SampleResult(node.getTestName());
                HLSSamplerExecutor.execute(node, r, variables);
                applyPostProcessors(node.getChildren(), r, variables);
                yield List.of(r);
            }

            case "WebRTCSampler" -> {
                applyPreProcessors(node.getChildren(), variables, lastResult);
                SampleResult r = new SampleResult(node.getTestName());
                WebRTCSamplerExecutor.execute(node, r, variables);
                applyPostProcessors(node.getChildren(), r, variables);
                yield List.of(r);
            }

            case "DASHSampler" -> {
                applyPreProcessors(node.getChildren(), variables, lastResult);
                SampleResult r = new SampleResult(node.getTestName());
                DASHSamplerExecutor.execute(node, r, variables);
                applyPostProcessors(node.getChildren(), r, variables);
                yield List.of(r);
            }

            case "FTPSampler" -> {
                applyPreProcessors(node.getChildren(), variables, lastResult);
                SampleResult r = new SampleResult(node.getTestName());
                FTPSamplerExecutor.execute(node, r, variables);
                applyPostProcessors(node.getChildren(), r, variables);
                yield List.of(r);
            }

            case "LDAPSampler" -> {
                applyPreProcessors(node.getChildren(), variables, lastResult);
                SampleResult r = new SampleResult(node.getTestName());
                LDAPSamplerExecutor.execute(node, r, variables);
                applyPostProcessors(node.getChildren(), r, variables);
                yield List.of(r);
            }

            case "JSR223Sampler" -> {
                applyPreProcessors(node.getChildren(), variables, lastResult);
                SampleResult r = new SampleResult(node.getTestName());
                JSR223SamplerExecutor.execute(node, r, variables);
                applyPostProcessors(node.getChildren(), r, variables);
                yield List.of(r);
            }

            case "BeanShellSampler" -> {
                applyPreProcessors(node.getChildren(), variables, lastResult);
                SampleResult r = new SampleResult(node.getTestName());
                BeanShellSamplerExecutor.execute(node, r, variables);
                applyPostProcessors(node.getChildren(), r, variables);
                yield List.of(r);
            }

            case "OSProcessSampler" -> {
                applyPreProcessors(node.getChildren(), variables, lastResult);
                SampleResult r = new SampleResult(node.getTestName());
                OSProcessSamplerExecutor.execute(node, r, variables);
                applyPostProcessors(node.getChildren(), r, variables);
                yield List.of(r);
            }

            case "DebugSampler" -> {
                applyPreProcessors(node.getChildren(), variables, lastResult);
                SampleResult r = new SampleResult(node.getTestName());
                DebugSamplerExecutor.execute(node, r, variables);
                applyPostProcessors(node.getChildren(), r, variables);
                yield List.of(r);
            }

            case "PublisherSampler" -> {
                applyPreProcessors(node.getChildren(), variables, lastResult);
                SampleResult r = new SampleResult(node.getTestName());
                JMSSamplerExecutor.executePublisher(node, r, variables);
                applyPostProcessors(node.getChildren(), r, variables);
                yield List.of(r);
            }

            case "SubscriberSampler" -> {
                applyPreProcessors(node.getChildren(), variables, lastResult);
                SampleResult r = new SampleResult(node.getTestName());
                JMSSamplerExecutor.executeSubscriber(node, r, variables);
                applyPostProcessors(node.getChildren(), r, variables);
                yield List.of(r);
            }

            case "SOAPSampler",
                 "SOAPSampler2" -> {
                applyPreProcessors(node.getChildren(), variables, lastResult);
                SampleResult r = new SampleResult(node.getTestName());
                SOAPSamplerExecutor.execute(node, r, variables, httpClientFactory);
                applyPostProcessors(node.getChildren(), r, variables);
                yield List.of(r);
            }

            case "MailReaderSampler" -> {
                applyPreProcessors(node.getChildren(), variables, lastResult);
                SampleResult r = new SampleResult(node.getTestName());
                MailReaderSamplerExecutor.execute(node, r, variables);
                applyPostProcessors(node.getChildren(), r, variables);
                yield List.of(r);
            }

            case "BSFSampler" -> {
                applyPreProcessors(node.getChildren(), variables, lastResult);
                SampleResult r = new SampleResult(node.getTestName());
                BSFSamplerExecutor.execute(node, r, variables);
                applyPostProcessors(node.getChildren(), r, variables);
                yield List.of(r);
            }

            case "AccessLogSampler" -> {
                applyPreProcessors(node.getChildren(), variables, lastResult);
                SampleResult r = new SampleResult(node.getTestName());
                AccessLogSamplerExecutor.execute(node, r, variables, httpClientFactory);
                applyPostProcessors(node.getChildren(), r, variables);
                yield List.of(r);
            }

            case "AjpSampler" -> {
                applyPreProcessors(node.getChildren(), variables, lastResult);
                SampleResult r = new SampleResult(node.getTestName());
                AJPSamplerExecutor.execute(node, r, variables);
                applyPostProcessors(node.getChildren(), r, variables);
                yield List.of(r);
            }

            case "JUnitSampler" -> {
                applyPreProcessors(node.getChildren(), variables, lastResult);
                SampleResult r = new SampleResult(node.getTestName());
                JUnitSamplerExecutor.execute(node, r, variables);
                applyPostProcessors(node.getChildren(), r, variables);
                yield List.of(r);
            }

            case "LoopController" -> loopExecutor().execute(node, variables);

            case "IfController" -> ifExecutor().execute(node, variables);

            case "ForeachController" -> forEachExecutor().execute(node, variables);

            case "ThroughputController" -> throughputExecutor().execute(node, variables);

            case "OnceOnlyController" -> onceOnlyExecutor().execute(node, variables);

            case "RecordingController" -> recordingExecutor().execute(node, variables);

            case "RuntimeController" -> runtimeExecutor().execute(node, variables);

            case "SwitchController" -> switchExecutor().execute(node, variables);

            case "RandomController" -> randomCtrlExecutor().execute(node, variables);

            case "InterleaveControl" -> interleaveExecutor().execute(node, variables);

            case "RandomOrderController" -> randomOrderExecutor().execute(node, variables);

            case "ModuleController" -> moduleExecutor().execute(node, variables, planRoot);

            case "IncludeController" -> includeExecutor().execute(node, variables);

            case "CacheManager" -> {
                HTTPCacheManagerExecutor.configure(node, variables);
                yield List.of();
            }

            case "AuthManager" -> {
                HTTPAuthManagerExecutor.configure(node, variables);
                yield List.of();
            }

            case "CounterConfig" -> {
                CounterConfigExecutor.execute(node, variables);
                yield List.of();
            }

            case "RandomVariableConfig" -> {
                RandomVariableConfigExecutor.execute(node, variables);
                yield List.of();
            }

            case "JDBCDataSource" -> {
                JDBCConnectionConfigExecutor.configure(node, variables);
                yield List.of();
            }

            case "DNSCacheManager" -> {
                DNSCacheManagerExecutor.configure(node, variables);
                yield List.of();
            }

            case "KeystoreConfig" -> {
                KeystoreConfigExecutor.configure(node, variables);
                yield List.of();
            }

            case "LoginConfig" -> {
                LoginConfigExecutor.configure(node, variables);
                yield List.of();
            }

            case "LDAPSamplerBase" -> {
                LDAPDefaultsExecutor.configure(node, variables);
                yield List.of();
            }

            case "ConfigTestElement" -> {
                // Simple Config Element — sets properties as variables
                for (var entry : node.getProperties().entrySet()) {
                    if (entry.getValue() instanceof String strVal) {
                        variables.put(entry.getKey(), strVal);
                    }
                }
                yield List.of();
            }

            case "TransactionController",
                 "GenericSampler" -> {
                // Transparent wrapper — execute children and collect their results
                yield executeChildren(node.getChildren(), variables);
            }

            case "ResponseAssertion" -> {
                if (lastResult != null) {
                    AssertionExecutor.execute(node, lastResult, variables);
                }
                yield List.of();
            }

            case "JSONPathAssertion",
                 "JSONPathAssertion2" -> {
                if (lastResult != null) {
                    JSONAssertionExecutor.execute(node, lastResult, variables);
                }
                yield List.of();
            }

            case "XPathAssertion" -> {
                if (lastResult != null) {
                    XPathAssertionExecutor.execute(node, lastResult, variables);
                }
                yield List.of();
            }

            case "XPath2Assertion" -> {
                if (lastResult != null) {
                    XPath2AssertionExecutor.execute(node, lastResult, variables);
                }
                yield List.of();
            }

            case "XMLAssertion" -> {
                if (lastResult != null) {
                    XMLAssertionExecutor.execute(node, lastResult, variables);
                }
                yield List.of();
            }

            case "JSR223Assertion" -> {
                if (lastResult != null) {
                    JSR223AssertionExecutor.execute(node, lastResult, variables);
                }
                yield List.of();
            }

            case "HTMLAssertion" -> {
                if (lastResult != null) {
                    HTMLAssertionExecutor.execute(node, lastResult, variables);
                }
                yield List.of();
            }

            case "BeanShellAssertion" -> {
                if (lastResult != null) {
                    BeanShellAssertionExecutor.execute(node, lastResult, variables);
                }
                yield List.of();
            }

            case "CompareAssertion" -> {
                if (lastResult != null) {
                    CompareAssertionExecutor.execute(node, lastResult, variables);
                }
                yield List.of();
            }

            case "ConstantTimer",
                 "GaussianRandomTimer",
                 "UniformRandomTimer" -> {
                timerExecutor.execute(node);
                yield List.of();
            }

            case "ConstantThroughputTimer" -> {
                ConstantThroughputTimerExecutor.execute(node);
                yield List.of();
            }

            case "SynchronizingTimer" -> {
                SynchronizingTimerExecutor.execute(node);
                yield List.of();
            }

            case "PoissonRandomTimer" -> {
                PoissonRandomTimerExecutor.execute(node, timerExecutor.getRandom());
                yield List.of();
            }

            case "BeanShellTimer" -> {
                BeanShellTimerExecutor.execute(node, variables);
                yield List.of();
            }

            case "JSR223Timer" -> {
                JSR223TimerExecutor.execute(node, variables);
                yield List.of();
            }

            case "BackendListener" -> {
                // BackendListener is a no-op in dispatchNode; lifecycle is managed externally
                LOG.log(Level.FINE,
                        "NodeInterpreter: BackendListener [{0}] — lifecycle managed externally",
                        node.getTestName());
                yield List.of();
            }

            case "ResultCollector" -> {
                // Simple Data Writer / View Results Tree — no-op at dispatch level
                LOG.log(Level.FINE,
                        "NodeInterpreter: ResultCollector [{0}] — skipping",
                        node.getTestName());
                yield List.of();
            }

            case "RegexExtractor",
                 "JSONPostProcessor",
                 "JSONPathExtractor" -> {
                if (lastResult != null) {
                    ExtractorExecutor.execute(node, lastResult, variables);
                }
                yield List.of();
            }

            case "XPathExtractor" -> {
                if (lastResult != null) {
                    XPathExtractorExecutor.execute(node, lastResult, variables);
                }
                yield List.of();
            }

            case "HtmlExtractor" -> {
                if (lastResult != null) {
                    CssExtractorExecutor.execute(node, lastResult, variables);
                }
                yield List.of();
            }

            case "BoundaryExtractor" -> {
                if (lastResult != null) {
                    BoundaryExtractorExecutor.execute(node, lastResult, variables);
                }
                yield List.of();
            }

            case "DebugPostProcessor" -> {
                if (lastResult != null) {
                    DebugPostProcessorExecutor.execute(node, lastResult, variables);
                }
                yield List.of();
            }

            // Pre-processors at the ThreadGroup/controller level are no-ops
            // (they are executed via applyPreProcessors when nested under a sampler)
            case "JSR223PreProcessor",
                 "BeanShellPreProcessor",
                 "UserParameters",
                 "RegExUserParameters",
                 "HTMLLinkParser",
                 "HTTPURLRewritingModifier" -> {
                yield List.of();
            }

            // Post-processors at the ThreadGroup/controller level are no-ops
            case "JSR223PostProcessor",
                 "BeanShellPostProcessor" -> {
                yield List.of();
            }

            default -> {
                LOG.log(Level.FINE, "NodeInterpreter: skipping unsupported node [{0}]", testClass);
                yield List.of();
            }
        };
    }

    /**
     * Applies pre-processors that are direct children of a sampler node.
     * Pre-processors run <em>before</em> the sampler and modify the VU variable map.
     *
     * <p>Execution order: PreProcessors -> Sampler -> PostProcessors -> Assertions
     */
    private void applyPreProcessors(List<PlanNode> children,
                                     Map<String, String> variables,
                                     SampleResult lastResult) {
        for (PlanNode child : children) {
            switch (child.getTestClass()) {
                case "JSR223PreProcessor" ->
                        JSR223PreProcessorExecutor.execute(child, variables);
                case "BeanShellPreProcessor" ->
                        BeanShellPreProcessorExecutor.execute(child, variables);
                case "UserParameters" ->
                        UserParametersPreProcessorExecutor.execute(child, variables);
                case "RegExUserParameters" ->
                        RegExUserParametersPreProcessorExecutor.execute(child, lastResult, variables);
                case "HTMLLinkParser" ->
                        HTMLLinkParserExecutor.execute(child, lastResult, variables);
                case "HTTPURLRewritingModifier" ->
                        URLRewritingModifierExecutor.execute(child, lastResult, variables);
                default -> {
                    // Not a pre-processor — skip (will be handled by applyPostProcessors)
                }
            }
        }
    }

    /**
     * Applies post-processors (assertions, extractors, timers) that are direct children
     * of a sampler node to the sampler's result.
     */
    private void applyPostProcessors(List<PlanNode> children,
                                      SampleResult samplerResult,
                                      Map<String, String> variables) {
        for (PlanNode child : children) {
            switch (child.getTestClass()) {
                case "ResponseAssertion" ->
                        AssertionExecutor.execute(child, samplerResult, variables);
                case "JSONPathAssertion",
                     "JSONPathAssertion2" ->
                        JSONAssertionExecutor.execute(child, samplerResult, variables);
                case "XPathAssertion" ->
                        XPathAssertionExecutor.execute(child, samplerResult, variables);
                case "XPath2Assertion" ->
                        XPath2AssertionExecutor.execute(child, samplerResult, variables);
                case "XMLAssertion" ->
                        XMLAssertionExecutor.execute(child, samplerResult, variables);
                case "JSR223Assertion" ->
                        JSR223AssertionExecutor.execute(child, samplerResult, variables);
                case "HTMLAssertion" ->
                        HTMLAssertionExecutor.execute(child, samplerResult, variables);
                case "BeanShellAssertion" ->
                        BeanShellAssertionExecutor.execute(child, samplerResult, variables);
                case "CompareAssertion" ->
                        CompareAssertionExecutor.execute(child, samplerResult, variables);
                case "RegexExtractor",
                     "JSONPostProcessor",
                     "JSONPathExtractor" ->
                        ExtractorExecutor.execute(child, samplerResult, variables);
                case "XPathExtractor" ->
                        XPathExtractorExecutor.execute(child, samplerResult, variables);
                case "HtmlExtractor" ->
                        CssExtractorExecutor.execute(child, samplerResult, variables);
                case "BoundaryExtractor" ->
                        BoundaryExtractorExecutor.execute(child, samplerResult, variables);
                case "DebugPostProcessor" ->
                        DebugPostProcessorExecutor.execute(child, samplerResult, variables);
                case "JSR223PostProcessor" ->
                        JSR223PostProcessorExecutor.execute(child, samplerResult, variables);
                case "BeanShellPostProcessor" ->
                        BeanShellPostProcessorExecutor.execute(child, samplerResult, variables);
                case "ConstantTimer",
                     "GaussianRandomTimer",
                     "UniformRandomTimer" ->
                        timerExecutor.execute(child);
                case "ConstantThroughputTimer" ->
                        ConstantThroughputTimerExecutor.execute(child);
                case "SynchronizingTimer" ->
                        SynchronizingTimerExecutor.execute(child);
                case "PoissonRandomTimer" ->
                        PoissonRandomTimerExecutor.execute(child, timerExecutor.getRandom());
                case "BeanShellTimer" ->
                        BeanShellTimerExecutor.execute(child, variables);
                case "JSR223Timer" ->
                        JSR223TimerExecutor.execute(child, variables);
                default -> LOG.log(Level.FINE,
                        "NodeInterpreter: sampler child [{0}] not a post-processor — skipping",
                        child.getTestClass());
            }
        }
    }

    // =========================================================================
    // Bucket publication
    // =========================================================================

    private void publishBuckets(String runId, List<SampleResult> results) {
        if (results.isEmpty()) return;

        // Group by label
        results.stream()
                .map(SampleResult::getLabel)
                .distinct()
                .forEach(label -> publishBucketForLabel(runId, label, results));
    }

    private void publishBucketForLabel(String runId, String label,
                                        List<SampleResult> allResults) {
        List<Long> times = allResults.stream()
                .filter(r -> label.equals(r.getLabel()))
                .map(SampleResult::getTotalTimeMs)
                .sorted()
                .toList();
        if (times.isEmpty()) return;

        long errorCount = allResults.stream()
                .filter(r -> label.equals(r.getLabel()) && !r.isSuccess())
                .count();

        double avg = times.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double min = times.get(0);
        double max = times.get(times.size() - 1);

        SampleBucket bucket = new SampleBucket(
                Instant.now(),
                label,
                times.size(),
                errorCount,
                avg,
                min,
                max,
                percentile(times, 90),
                percentile(times, 95),
                percentile(times, 99),
                times.size()
        );
        broker.publish(runId, bucket);
    }

    private static double percentile(List<Long> sorted, int pct) {
        if (sorted.isEmpty()) return 0.0;
        int idx = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        idx = Math.max(0, Math.min(idx, sorted.size() - 1));
        return sorted.get(idx);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Resolves the children that contain ThreadGroup nodes.
     *
     * <p>If the root is the document root ({@code jmeterTestPlan}), its direct children
     * are {@code TestPlan} nodes; each TestPlan's children contain the ThreadGroups.
     * If the root is already a {@code TestPlan}, its direct children contain ThreadGroups.
     */
    private static List<PlanNode> resolveTestPlanChildren(PlanNode root,
                                                            List<PlanNode> topLevel) {
        // If any direct child is a TestPlan, descend into its children
        for (PlanNode child : topLevel) {
            if ("TestPlan".equals(child.getTestClass())) {
                return child.getChildren();
            }
        }
        // root itself may be a TestPlan
        if ("TestPlan".equals(root.getTestClass())) {
            return root.getChildren();
        }
        // Otherwise treat topLevel directly (root is already at ThreadGroup level)
        return topLevel;
    }

    /** Extracts the loop count from the ThreadGroup's embedded LoopController element property. */
    private static int extractThreadGroupLoops(PlanNode tg) {
        PlanNode mainCtrl = tg.getElementProp("ThreadGroup.main_controller");
        if (mainCtrl != null) {
            int loops = mainCtrl.getIntProp("LoopController.loops", 1);
            return loops == -1 ? Integer.MAX_VALUE : Math.max(1, loops);
        }
        // Fallback: look at a direct LoopController child
        for (PlanNode child : tg.getChildren()) {
            if ("LoopController".equals(child.getTestClass())) {
                int loops = child.getIntProp("LoopController.loops", 1);
                return loops == -1 ? Integer.MAX_VALUE : Math.max(1, loops);
            }
        }
        return 1;
    }

    private static long computeTimeout(TestRunContext context, int threads, int loops) {
        if (context.getDurationSeconds() > 0) {
            return context.getDurationSeconds() + 30;
        }
        long safeLoops = (loops == Integer.MAX_VALUE) ? 10 : loops;
        return threads * safeLoops * TIMEOUT_PER_VU_ITER_S + 30;
    }
}
