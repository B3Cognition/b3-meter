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

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code JSR223Assertion} {@link PlanNode} against a prior {@link SampleResult}.
 *
 * <p>Same scripting pattern as {@link JSR223SamplerExecutor} but runs as an assertion.
 * The script can call {@code AssertionResult.setFailure(true)} and
 * {@code AssertionResult.setFailureMessage("...")} to fail the assertion.
 *
 * <p>Script bindings:
 * <ul>
 *   <li>{@code SampleResult} — the sample result being asserted</li>
 *   <li>{@code Response} — alias for response body string</li>
 *   <li>{@code prev} — alias for SampleResult (JMeter convention)</li>
 *   <li>{@code vars} — VU variable map (read/write)</li>
 *   <li>{@code props} — system properties</li>
 *   <li>{@code log} — a {@link Logger}</li>
 *   <li>{@code AssertionResult} — mutable assertion result holder</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class JSR223AssertionExecutor {

    private static final Logger LOG = Logger.getLogger(JSR223AssertionExecutor.class.getName());

    private JSR223AssertionExecutor() {
        // utility class -- not instantiable
    }

    /**
     * Applies the JSR223 assertion described by {@code node} to {@code result}.
     *
     * @param node      the JSR223Assertion node; must not be {@code null}
     * @param result    the most recent sample result to validate; must not be {@code null}
     * @param variables current VU variable scope
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node,      "node must not be null");
        Objects.requireNonNull(result,    "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String language   = resolve(node.getStringProp("scriptLanguage", "javascript"), variables);
        String scriptText = resolve(node.getStringProp("script", ""), variables);
        String scriptFile = resolve(node.getStringProp("filename", ""), variables);
        String parameters = resolve(node.getStringProp("parameters", ""), variables);

        // If script file specified and no inline script, read from file
        if (scriptText.isBlank() && !scriptFile.isBlank()) {
            try {
                scriptText = Files.readString(Path.of(scriptFile), StandardCharsets.UTF_8);
            } catch (IOException e) {
                result.setFailureMessage("JSR223Assertion [" + node.getTestName()
                        + "]: failed to read script file: " + scriptFile + " — " + e.getMessage());
                return;
            }
        }

        if (scriptText.isBlank()) {
            result.setFailureMessage("JSR223Assertion [" + node.getTestName()
                    + "]: no script text or script file provided");
            return;
        }

        String engineName = JSR223SamplerExecutor.resolveEngineName(language);

        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName(engineName);
        if (engine == null) {
            engine = manager.getEngineByName(language);
        }
        if (engine == null) {
            result.setFailureMessage("JSR223Assertion [" + node.getTestName()
                    + "]: no ScriptEngine found for language '" + language + "'");
            return;
        }

        // Create assertion result holder
        AssertionResultHolder assertionResult = new AssertionResultHolder();

        Bindings bindings = engine.createBindings();
        bindings.put("SampleResult", result);
        bindings.put("prev", result);
        bindings.put("Response", result.getResponseBody());
        bindings.put("vars", variables);
        bindings.put("props", System.getProperties());
        bindings.put("log", LOG);
        bindings.put("AssertionResult", assertionResult);
        bindings.put("Label", node.getTestName());
        bindings.put("Parameters", parameters);
        bindings.put("args", parameters.isBlank() ? new String[0] : parameters.split("\\s+"));

        try {
            engine.eval(scriptText, bindings);

            // Apply assertion result
            if (assertionResult.isFailure()) {
                String msg = assertionResult.getFailureMessage();
                if (msg == null || msg.isBlank()) {
                    msg = "JSR223 assertion script marked failure";
                }
                result.setFailureMessage("JSR223Assertion [" + node.getTestName() + "] FAILED: " + msg);
            }
        } catch (Exception e) {
            result.setFailureMessage("JSR223Assertion [" + node.getTestName()
                    + "] script error: " + e.getMessage());
            LOG.log(Level.WARNING, "JSR223AssertionExecutor: script execution failed", e);
        }
    }

    private static String resolve(String value, Map<String, String> variables) {
        if (value == null) return "";
        return VariableResolver.resolve(value, variables);
    }

    /**
     * Mutable assertion result holder exposed to scripts as {@code AssertionResult}.
     * Scripts call {@code setFailure(true)} and {@code setFailureMessage("...")} on this object.
     */
    public static final class AssertionResultHolder {
        private boolean failure;
        private String failureMessage = "";

        public boolean isFailure() {
            return failure;
        }

        public void setFailure(boolean failure) {
            this.failure = failure;
        }

        public String getFailureMessage() {
            return failureMessage;
        }

        public void setFailureMessage(String failureMessage) {
            this.failureMessage = failureMessage != null ? failureMessage : "";
        }
    }
}
