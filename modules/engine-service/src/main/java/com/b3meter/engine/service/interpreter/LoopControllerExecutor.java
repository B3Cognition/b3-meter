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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code LoopController} {@link PlanNode} by iterating its children.
 *
 * <p>Reads the standard JMeter property:
 * <ul>
 *   <li>{@code LoopController.loops} — number of iterations; {@code -1} means loop forever
 *       (bounded by the VU's duration limit, enforced via the interrupted flag).</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class LoopControllerExecutor {

    private static final Logger LOG = Logger.getLogger(LoopControllerExecutor.class.getName());

    /**
     * Maximum safety cap for "forever" (-1) loops during a single VU iteration.
     * Prevents runaway loops in tests where no duration bound is set.
     */
    private static final int FOREVER_CAP = 1_000_000;

    private final NodeInterpreter interpreter;

    /**
     * Constructs a {@code LoopControllerExecutor}.
     *
     * @param interpreter the parent interpreter, used to execute child nodes recursively;
     *                    must not be {@code null}
     */
    public LoopControllerExecutor(NodeInterpreter interpreter) {
        this.interpreter = Objects.requireNonNull(interpreter, "interpreter must not be null");
    }

    /**
     * Executes the children of {@code node} the number of times specified by
     * {@code LoopController.loops}.
     *
     * @param node      the LoopController node; must not be {@code null}
     * @param variables mutable VU variable map; passed through to children
     * @return list of all {@link SampleResult}s produced across all iterations
     */
    public List<SampleResult> execute(PlanNode node, Map<String, String> variables) {
        Objects.requireNonNull(node,      "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        int loops = node.getIntProp("LoopController.loops", 1);
        boolean forever = (loops == -1);
        int iterations  = forever ? FOREVER_CAP : Math.max(0, loops);

        LOG.log(Level.FINE, "LoopControllerExecutor [{0}]: {1} iteration(s)",
                new Object[]{node.getTestName(), forever ? "∞ (capped at " + FOREVER_CAP + ")" : iterations});

        List<SampleResult> allResults = new ArrayList<>();

        for (int i = 0; i < iterations; i++) {
            if (Thread.currentThread().isInterrupted()) {
                LOG.log(Level.FINE, "LoopControllerExecutor [{0}]: interrupted at iteration {1}",
                        new Object[]{node.getTestName(), i});
                break;
            }

            List<SampleResult> iterResults = interpreter.executeChildren(node.getChildren(), variables);
            allResults.addAll(iterResults);
        }

        return allResults;
    }
}
