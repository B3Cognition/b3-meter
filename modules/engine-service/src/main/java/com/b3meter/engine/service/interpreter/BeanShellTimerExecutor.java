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
 * Executes a {@code BeanShellTimer} {@link PlanNode}.
 *
 * <p>Delegates to JSR223 with language set to {@code "beanshell"}. The script
 * should return the delay in milliseconds (as a long or numeric string).
 *
 * <p>Properties:
 * <ul>
 *   <li>{@code BeanShellTimer.script} — inline BeanShell script</li>
 *   <li>{@code BeanShellTimer.filename} — external script file</li>
 *   <li>{@code BeanShellTimer.parameters} — parameters string</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class BeanShellTimerExecutor {

    private static final Logger LOG = Logger.getLogger(BeanShellTimerExecutor.class.getName());

    private BeanShellTimerExecutor() {}

    /**
     * Executes the BeanShell timer script and sleeps for the returned delay.
     *
     * @param node      the BeanShellTimer node; must not be {@code null}
     * @param variables current VU variable scope; must not be {@code null}
     */
    public static void execute(PlanNode node, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        // Delegate to JSR223TimerExecutor with BeanShell properties mapped
        String script = node.getStringProp("BeanShellTimer.script", "");
        String filename = node.getStringProp("BeanShellTimer.filename", "");
        String parameters = node.getStringProp("BeanShellTimer.parameters", "");

        // Determine language
        String language = "beanshell";
        ScriptEngineManager manager = new ScriptEngineManager();
        if (manager.getEngineByName("beanshell") == null && manager.getEngineByName("bsh") == null) {
            LOG.log(Level.INFO,
                    "BeanShellTimerExecutor: BeanShell engine not found, falling back to javascript");
            language = "javascript";
        }

        // Build a synthetic JSR223Timer node
        PlanNode jsr223Node = PlanNode.builder("JSR223Timer", node.getTestName())
                .property("JSR223Timer.script", script)
                .property("JSR223Timer.scriptFile", filename)
                .property("JSR223Timer.scriptLanguage", language)
                .property("JSR223Timer.parameters", parameters)
                .build();

        JSR223TimerExecutor.execute(jsr223Node, variables);
    }
}
