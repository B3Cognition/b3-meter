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
package com.b3meter.engine.service.interpreter;

import com.b3meter.engine.service.plan.PlanNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code ThroughputController} {@link PlanNode}.
 *
 * <p>Controls how often its children execute based on either a total execution
 * count or a percentage of parent loop iterations.
 *
 * <p>Reads the following properties:
 * <ul>
 *   <li>{@code ThroughputController.style} — 0 = Percent Executions, 1 = Total Executions</li>
 *   <li>{@code ThroughputController.perThread} — apply count/percent per thread (vs globally)</li>
 *   <li>{@code ThroughputController.maxThroughput} — total number of executions (style=1)</li>
 *   <li>{@code ThroughputController.percentThroughput} — percentage of iterations to execute (style=0)</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class ThroughputControllerExecutor {

    private static final Logger LOG = Logger.getLogger(ThroughputControllerExecutor.class.getName());

    /** Style constant: percentage-based execution. */
    private static final int STYLE_PERCENT = 0;

    /** Style constant: total-count-based execution. */
    private static final int STYLE_TOTAL = 1;

    /**
     * Shared execution counters for global (non-perThread) mode.
     * Key: node testName, Value: execution count.
     */
    private static final ConcurrentHashMap<String, AtomicInteger> GLOBAL_EXEC_COUNTS =
            new ConcurrentHashMap<>();

    /**
     * Shared invocation counters for percentage mode.
     * Key: node testName, Value: invocation count (denominator for percent calc).
     */
    private static final ConcurrentHashMap<String, AtomicInteger> GLOBAL_INVOKE_COUNTS =
            new ConcurrentHashMap<>();

    /** Thread-local execution count for perThread mode. */
    private static final ThreadLocal<ConcurrentHashMap<String, AtomicInteger>> PER_THREAD_EXEC =
            ThreadLocal.withInitial(ConcurrentHashMap::new);

    /** Thread-local invocation count for perThread percentage mode. */
    private static final ThreadLocal<ConcurrentHashMap<String, AtomicInteger>> PER_THREAD_INVOKE =
            ThreadLocal.withInitial(ConcurrentHashMap::new);

    private final NodeInterpreter interpreter;

    /**
     * Constructs a {@code ThroughputControllerExecutor}.
     *
     * @param interpreter the parent interpreter for executing child nodes; must not be {@code null}
     */
    public ThroughputControllerExecutor(NodeInterpreter interpreter) {
        this.interpreter = Objects.requireNonNull(interpreter, "interpreter must not be null");
    }

    /**
     * Evaluates whether children should execute based on the throughput policy,
     * and if so, executes them.
     *
     * @param node      the ThroughputController node; must not be {@code null}
     * @param variables mutable VU variable map
     * @return list of {@link SampleResult}s from children if executed; empty list otherwise
     */
    public List<SampleResult> execute(PlanNode node, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        int style = node.getIntProp("ThroughputController.style", STYLE_PERCENT);
        boolean perThread = node.getBoolProp("ThroughputController.perThread", false);
        int maxThroughput = node.getIntProp("ThroughputController.maxThroughput", 1);

        // percentThroughput may be stored as a double via FloatProperty or as a string
        double percentThroughput = node.getDoubleProp("ThroughputController.percentThroughput");
        if (percentThroughput == 0.0) {
            // Try reading as string (some JMX files store it differently)
            String pctStr = node.getStringProp("ThroughputController.percentThroughput", "100.0");
            try {
                percentThroughput = Double.parseDouble(pctStr);
            } catch (NumberFormatException e) {
                percentThroughput = 100.0;
            }
        }

        String key = node.getTestName();
        boolean shouldExecute;

        if (style == STYLE_TOTAL) {
            shouldExecute = evaluateTotalMode(key, maxThroughput, perThread);
        } else {
            shouldExecute = evaluatePercentMode(key, percentThroughput, perThread);
        }

        if (shouldExecute) {
            LOG.log(Level.FINE,
                    "ThroughputControllerExecutor [{0}]: executing children (style={1})",
                    new Object[]{node.getTestName(), style});
            return interpreter.executeChildren(node.getChildren(), variables);
        } else {
            LOG.log(Level.FINE,
                    "ThroughputControllerExecutor [{0}]: skipping children (style={1})",
                    new Object[]{node.getTestName(), style});
            return new ArrayList<>();
        }
    }

    /**
     * Total Executions mode: execute children exactly N times total.
     */
    private boolean evaluateTotalMode(String key, int maxThroughput, boolean perThread) {
        AtomicInteger execCount;
        if (perThread) {
            execCount = PER_THREAD_EXEC.get()
                    .computeIfAbsent(key, k -> new AtomicInteger(0));
        } else {
            execCount = GLOBAL_EXEC_COUNTS
                    .computeIfAbsent(key, k -> new AtomicInteger(0));
        }

        int current = execCount.get();
        if (current < maxThroughput) {
            execCount.incrementAndGet();
            return true;
        }
        return false;
    }

    /**
     * Percent Executions mode: execute children X% of invocations.
     */
    private boolean evaluatePercentMode(String key, double percent, boolean perThread) {
        AtomicInteger invokeCount;
        AtomicInteger execCount;

        if (perThread) {
            invokeCount = PER_THREAD_INVOKE.get()
                    .computeIfAbsent(key, k -> new AtomicInteger(0));
            execCount = PER_THREAD_EXEC.get()
                    .computeIfAbsent(key, k -> new AtomicInteger(0));
        } else {
            invokeCount = GLOBAL_INVOKE_COUNTS
                    .computeIfAbsent(key, k -> new AtomicInteger(0));
            execCount = GLOBAL_EXEC_COUNTS
                    .computeIfAbsent(key, k -> new AtomicInteger(0));
        }

        int invocations = invokeCount.incrementAndGet();
        double targetExecs = invocations * (percent / 100.0);

        if (execCount.get() < targetExecs) {
            execCount.incrementAndGet();
            return true;
        }
        return false;
    }

    /**
     * Resets all global and per-thread counters. Intended for testing only.
     */
    static void resetCounters() {
        GLOBAL_EXEC_COUNTS.clear();
        GLOBAL_INVOKE_COUNTS.clear();
        PER_THREAD_EXEC.remove();
        PER_THREAD_INVOKE.remove();
    }
}
