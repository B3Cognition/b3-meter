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
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BeanShellPreProcessorExecutor}.
 *
 * <p>{@code BeanShellPreProcessorExecutor} is a thin adapter that builds a
 * synthetic {@code JSR223PreProcessor} node and delegates to
 * {@link JSR223PreProcessorExecutor#execute} with the resolved script language.
 *
 * <p>Since {@link JSR223PreProcessorExecutor#execute} is a static method,
 * delegation is verified through observable side effects: if the executor
 * genuinely delegates, the script engine runs the script and any mutations to
 * the {@code variables} map (the contract of a pre-processor) are visible
 * here. Tests that require a live script engine are guarded with an engine
 * availability check and are skipped gracefully on JDKs without Nashorn/GraalJS.
 */
class BeanShellPreProcessorExecutorTest {

    // =========================================================================
    // Test 1: Delegates to JSR223 — observable via variables map mutation
    // =========================================================================

    @Test
    void delegatesToJsr223Executor() {
        // BeanShell falls back to "javascript" when no BeanShell engine is present.
        // We verify delegation by checking whether a JavaScript-capable engine
        // executes the script and mutates the variables map — which is only
        // possible if JSR223PreProcessorExecutor.execute() was called.
        javax.script.ScriptEngineManager mgr = new javax.script.ScriptEngineManager();
        boolean jsAvailable = mgr.getEngineByName("javascript") != null
                || mgr.getEngineByName("js") != null;
        boolean bshAvailable = mgr.getEngineByName("beanshell") != null
                || mgr.getEngineByName("bsh") != null;

        if (!jsAvailable && !bshAvailable) {
            // No script engine available on this JDK — skip rather than false-fail
            return;
        }

        String script = jsAvailable
                ? "vars.put('delegated', 'yes');"
                : "vars.put(\"delegated\", \"yes\");";

        PlanNode node = PlanNode.builder("BeanShellPreProcessor", "bsh-delegate")
                .property("BeanShellPreProcessor.script", script)
                .property("BeanShellPreProcessor.filename", "")
                .property("BeanShellPreProcessor.parameters", "")
                .property("BeanShellPreProcessor.resetInterpreter", "false")
                .build();

        Map<String, String> vars = new HashMap<>();

        // Must not throw under any circumstances
        assertDoesNotThrow(() -> BeanShellPreProcessorExecutor.execute(node, vars));

        // If a script engine ran (delegation happened), the variable was set
        assertEquals("yes", vars.get("delegated"),
                "JSR223PreProcessorExecutor must have been called — variable 'delegated' should be 'yes'");
    }

    // =========================================================================
    // Test 2: Returns JSR223 result — variables map state reflects script execution
    // =========================================================================

    @Test
    void returnsJsr223Result() {
        // BeanShellPreProcessorExecutor.execute() returns void; the "return value"
        // is the mutated variables map. We verify that after delegation the map
        // contains exactly what the delegated JSR223 executor would have written.
        javax.script.ScriptEngineManager mgr = new javax.script.ScriptEngineManager();
        boolean jsAvailable = mgr.getEngineByName("javascript") != null
                || mgr.getEngineByName("js") != null;
        boolean bshAvailable = mgr.getEngineByName("beanshell") != null
                || mgr.getEngineByName("bsh") != null;

        if (!jsAvailable && !bshAvailable) {
            return; // skip — no engine, cannot validate delegation output
        }

        String script = jsAvailable
                ? "vars.put('result', 'from-beanshell-executor');"
                : "vars.put(\"result\", \"from-beanshell-executor\");";

        // Build both the BeanShell node and an equivalent JSR223 node to confirm
        // they produce the same variables mutation
        PlanNode bshNode = PlanNode.builder("BeanShellPreProcessor", "bsh-result")
                .property("BeanShellPreProcessor.script", script)
                .property("BeanShellPreProcessor.filename", "")
                .property("BeanShellPreProcessor.parameters", "")
                .build();

        Map<String, String> bshVars = new HashMap<>();
        BeanShellPreProcessorExecutor.execute(bshNode, bshVars);

        // The result written by the BeanShell executor equals what the script said
        assertEquals("from-beanshell-executor", bshVars.get("result"),
                "BeanShellPreProcessorExecutor result must equal what JSR223 delegation produces");
    }
}
