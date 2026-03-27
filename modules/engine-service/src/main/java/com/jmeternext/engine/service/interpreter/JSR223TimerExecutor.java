package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code JSR223Timer} {@link PlanNode} using the {@code javax.script} API.
 *
 * <p>The script should return the delay in milliseconds. The return value is
 * interpreted as a numeric delay. Properties:
 * <ul>
 *   <li>{@code JSR223Timer.script} — inline script text</li>
 *   <li>{@code JSR223Timer.scriptLanguage} — engine name (default {@code "javascript"})</li>
 *   <li>{@code JSR223Timer.scriptFile} — path to external script file</li>
 *   <li>{@code JSR223Timer.parameters} — space-separated parameters</li>
 * </ul>
 *
 * <p>Bindings available to the script:
 * <ul>
 *   <li>{@code vars} — VU variable map (read/write)</li>
 *   <li>{@code props} — system properties</li>
 *   <li>{@code log} — a {@link Logger}</li>
 *   <li>{@code Label} — the timer label</li>
 *   <li>{@code Parameters} — the raw parameters string</li>
 *   <li>{@code args} — parameters split into {@code String[]}</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class JSR223TimerExecutor {

    private static final Logger LOG = Logger.getLogger(JSR223TimerExecutor.class.getName());

    private JSR223TimerExecutor() {}

    /**
     * Executes the script and sleeps for the returned delay.
     *
     * @param node      the JSR223Timer node; must not be {@code null}
     * @param variables current VU variable scope; must not be {@code null}
     */
    public static void execute(PlanNode node, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String language = resolve(node.getStringProp("JSR223Timer.scriptLanguage", "javascript"), variables);
        String scriptText = resolve(node.getStringProp("JSR223Timer.script", ""), variables);
        String scriptFile = resolve(node.getStringProp("JSR223Timer.scriptFile", ""), variables);
        String parameters = resolve(node.getStringProp("JSR223Timer.parameters", ""), variables);

        // If a script file is specified and no inline script, read from file
        if (scriptText.isBlank() && !scriptFile.isBlank()) {
            try {
                scriptText = Files.readString(Path.of(scriptFile), StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOG.log(Level.WARNING,
                        "JSR223TimerExecutor: cannot read script file " + scriptFile, e);
                return;
            }
        }

        if (scriptText.isBlank()) {
            LOG.log(Level.FINE, "JSR223TimerExecutor [{0}]: no script — no delay",
                    node.getTestName());
            return;
        }

        String engineName = JSR223SamplerExecutor.resolveEngineName(language);
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName(engineName);
        if (engine == null) {
            engine = manager.getEngineByName(language);
        }
        if (engine == null) {
            LOG.log(Level.WARNING,
                    "JSR223TimerExecutor: no ScriptEngine found for language ''{0}''", language);
            return;
        }

        Bindings bindings = engine.createBindings();
        bindings.put("vars", variables);
        bindings.put("props", System.getProperties());
        bindings.put("log", LOG);
        bindings.put("Label", node.getTestName());
        bindings.put("Parameters", parameters);
        bindings.put("args", parameters.isBlank() ? new String[0] : parameters.split("\\s+"));

        try {
            Object returnValue = engine.eval(scriptText, bindings);
            long delayMs = parseDelay(returnValue);

            if (delayMs > 0) {
                LOG.log(Level.FINE,
                        "JSR223TimerExecutor [{0}]: sleeping {1} ms",
                        new Object[]{node.getTestName(), delayMs});
                Thread.sleep(delayMs);
            }
        } catch (ScriptException e) {
            LOG.log(Level.WARNING, "JSR223TimerExecutor: script error", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "JSR223TimerExecutor: unexpected error", e);
        }
    }

    /**
     * Parses the script return value as a delay in milliseconds.
     *
     * @param returnValue the value returned by the script
     * @return delay in ms (non-negative); 0 if unparseable or null
     */
    static long parseDelay(Object returnValue) {
        if (returnValue == null) return 0;
        if (returnValue instanceof Number) {
            return Math.max(0, ((Number) returnValue).longValue());
        }
        try {
            return Math.max(0, Long.parseLong(returnValue.toString().trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String resolve(String value, Map<String, String> variables) {
        if (value == null) return "";
        return VariableResolver.resolve(value, variables);
    }
}
