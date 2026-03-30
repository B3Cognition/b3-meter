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
 * Executes a {@code JSR223PreProcessor} {@link PlanNode} using the {@code javax.script} API.
 *
 * <p>Pre-processors run <em>before</em> a sampler and modify the VU variable map
 * so that subsequent samplers can use the computed values.  Unlike a sampler executor,
 * a pre-processor does <strong>not</strong> produce a {@link SampleResult}.
 *
 * <p>Reads the following properties:
 * <ul>
 *   <li>{@code scriptLanguage} — engine name (default {@code "groovy"})</li>
 *   <li>{@code parameters} — space-separated parameters</li>
 *   <li>{@code cacheKey} — whether to cache the compiled script</li>
 *   <li>{@code script} — inline script text</li>
 *   <li>{@code filename} — path to external script file (alternative to inline)</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class JSR223PreProcessorExecutor {

    private static final Logger LOG = Logger.getLogger(JSR223PreProcessorExecutor.class.getName());

    private JSR223PreProcessorExecutor() {
        // utility class — not instantiable
    }

    /**
     * Executes the pre-processor script described by {@code node}, modifying
     * the {@code variables} map in place.
     *
     * @param node      the JSR223PreProcessor node; must not be {@code null}
     * @param variables current VU variable scope; must not be {@code null}
     */
    public static void execute(PlanNode node, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String language = resolve(node.getStringProp("scriptLanguage", "groovy"), variables);
        String scriptText = resolve(node.getStringProp("script", ""), variables);
        String scriptFile = resolve(node.getStringProp("filename", ""), variables);
        String parameters = resolve(node.getStringProp("parameters", ""), variables);

        // If a script file is specified and no inline script, read from file
        if (scriptText.isBlank() && !scriptFile.isBlank()) {
            try {
                scriptText = Files.readString(Path.of(scriptFile), StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOG.log(Level.WARNING,
                        "JSR223PreProcessorExecutor: cannot read script file " + scriptFile, e);
                return;
            }
        }

        if (scriptText.isBlank()) {
            LOG.log(Level.FINE,
                    "JSR223PreProcessorExecutor [{0}]: no script text or file — skipping",
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
                    "JSR223PreProcessorExecutor: no ScriptEngine found for language ''{0}''",
                    language);
            return;
        }

        // Set up bindings — same as JSR223Sampler but without SampleResult
        Bindings bindings = engine.createBindings();
        bindings.put("vars", variables);
        bindings.put("props", System.getProperties());
        bindings.put("log", LOG);
        bindings.put("Label", node.getTestName());
        bindings.put("Parameters", parameters);
        bindings.put("args", parameters.isBlank() ? new String[0] : parameters.split("\\s+"));

        try {
            engine.eval(scriptText, bindings);
        } catch (ScriptException e) {
            LOG.log(Level.WARNING,
                    "JSR223PreProcessorExecutor: script execution failed", e);
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "JSR223PreProcessorExecutor: unexpected error", e);
        }
    }

    private static String resolve(String value, Map<String, String> variables) {
        if (value == null) return "";
        return VariableResolver.resolve(value, variables);
    }
}
