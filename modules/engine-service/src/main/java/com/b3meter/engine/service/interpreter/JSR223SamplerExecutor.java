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
 * Executes a {@code JSR223Sampler} {@link PlanNode} using the {@code javax.script} API.
 *
 * <p>Reads the following sampler properties:
 * <ul>
 *   <li>{@code JSR223Sampler.script} — inline script text</li>
 *   <li>{@code JSR223Sampler.scriptLanguage} — engine name (default {@code "javascript"})</li>
 *   <li>{@code JSR223Sampler.scriptFile} — path to external script file (alternative to inline)</li>
 *   <li>{@code JSR223Sampler.parameters} — space-separated parameters</li>
 * </ul>
 *
 * <p>The script has access to the following bindings:
 * <ul>
 *   <li>{@code SampleResult} — the {@link SampleResult} to populate</li>
 *   <li>{@code vars} — VU variable map (read/write)</li>
 *   <li>{@code props} — system properties</li>
 *   <li>{@code log} — a {@link Logger}</li>
 *   <li>{@code Label} — the sampler label</li>
 *   <li>{@code Parameters} — the raw parameters string</li>
 *   <li>{@code args} — parameters split into {@code String[]}</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class JSR223SamplerExecutor {

    private static final Logger LOG = Logger.getLogger(JSR223SamplerExecutor.class.getName());

    private JSR223SamplerExecutor() {
        // utility class — not instantiable
    }

    /**
     * Executes the script described by {@code node}.
     *
     * @param node      the JSR223Sampler node; must not be {@code null}
     * @param result    the sample result to populate; must not be {@code null}
     * @param variables current VU variable scope; must not be {@code null}
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String language = resolve(node.getStringProp("JSR223Sampler.scriptLanguage", "javascript"), variables);
        String scriptText = resolve(node.getStringProp("JSR223Sampler.script", ""), variables);
        String scriptFile = resolve(node.getStringProp("JSR223Sampler.scriptFile", ""), variables);
        String parameters = resolve(node.getStringProp("JSR223Sampler.parameters", ""), variables);

        // If a script file is specified and no inline script, read from file
        if (scriptText.isBlank() && !scriptFile.isBlank()) {
            try {
                scriptText = Files.readString(Path.of(scriptFile), StandardCharsets.UTF_8);
            } catch (IOException e) {
                result.setFailureMessage("Failed to read script file: " + scriptFile + " — " + e.getMessage());
                LOG.log(Level.WARNING, "JSR223SamplerExecutor: cannot read script file " + scriptFile, e);
                return;
            }
        }

        if (scriptText.isBlank()) {
            result.setFailureMessage("JSR223Sampler: no script text or script file provided");
            return;
        }

        // Resolve engine name aliases
        String engineName = resolveEngineName(language);

        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName(engineName);

        if (engine == null) {
            // Try the raw language name as fallback
            engine = manager.getEngineByName(language);
        }

        if (engine == null) {
            result.setFailureMessage("JSR223Sampler: no ScriptEngine found for language '" + language
                    + "'. Ensure the appropriate engine JAR is on the classpath.");
            return;
        }

        // Set up bindings
        Bindings bindings = engine.createBindings();
        bindings.put("SampleResult", result);
        bindings.put("vars", variables);
        bindings.put("props", System.getProperties());
        bindings.put("log", LOG);
        bindings.put("Label", node.getTestName());
        bindings.put("Parameters", parameters);
        bindings.put("args", parameters.isBlank() ? new String[0] : parameters.split("\\s+"));

        long start = System.currentTimeMillis();
        try {
            Object returnValue = engine.eval(scriptText, bindings);
            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);

            // If the script returned a value and did not set responseBody, use the return value
            if (result.getResponseBody().isEmpty() && returnValue != null) {
                result.setResponseBody(returnValue.toString());
            }

            // If no status code was set, default to 200 for success
            if (result.getStatusCode() == 0 && result.isSuccess()) {
                result.setStatusCode(200);
            }

        } catch (ScriptException e) {
            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);
            result.setFailureMessage("JSR223 script error: " + e.getMessage());
            LOG.log(Level.WARNING, "JSR223SamplerExecutor: script execution failed", e);
        } catch (Exception e) {
            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);
            result.setFailureMessage("JSR223 script error: " + e.getMessage());
            LOG.log(Level.WARNING, "JSR223SamplerExecutor: unexpected error", e);
        }
    }

    /**
     * Maps common language names to ScriptEngine names.
     */
    static String resolveEngineName(String language) {
        if (language == null) return "javascript";
        return switch (language.toLowerCase().trim()) {
            case "groovy" -> "groovy";
            case "javascript", "js", "ecmascript" -> "javascript";
            case "python", "jython" -> "python";
            case "beanshell", "bsh" -> "beanshell";
            case "java" -> "java";
            default -> language;
        };
    }

    private static String resolve(String value, Map<String, String> variables) {
        if (value == null) return "";
        return VariableResolver.resolve(value, variables);
    }
}
