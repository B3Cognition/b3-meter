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
 * Executes a {@code JSR223PostProcessor} {@link PlanNode} using the {@code javax.script} API.
 *
 * <p>Post-processors run <em>after</em> a sampler and can modify the VU variable map
 * based on the sample result. Unlike the pre-processor variant, this executor has
 * access to the previous {@link SampleResult} via the {@code prev} binding.
 *
 * <p>Script bindings:
 * <ul>
 *   <li>{@code prev} — the previous {@link SampleResult}</li>
 *   <li>{@code vars} — VU variable map (read/write)</li>
 *   <li>{@code props} — system properties</li>
 *   <li>{@code log} — a {@link Logger}</li>
 *   <li>{@code Label} — the node test name</li>
 *   <li>{@code Parameters} — parameters string</li>
 *   <li>{@code args} — parameters split by whitespace</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class JSR223PostProcessorExecutor {

    private static final Logger LOG = Logger.getLogger(JSR223PostProcessorExecutor.class.getName());

    private JSR223PostProcessorExecutor() {
        // utility class -- not instantiable
    }

    /**
     * Executes the post-processor script described by {@code node}, modifying
     * the {@code variables} map based on the sample result.
     *
     * @param node      the JSR223PostProcessor node; must not be {@code null}
     * @param result    the most recent sample result; must not be {@code null}
     * @param variables current VU variable scope; must not be {@code null}
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node,      "node must not be null");
        Objects.requireNonNull(result,    "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String language   = resolve(node.getStringProp("scriptLanguage", "groovy"), variables);
        String scriptText = resolve(node.getStringProp("script", ""), variables);
        String scriptFile = resolve(node.getStringProp("filename", ""), variables);
        String parameters = resolve(node.getStringProp("parameters", ""), variables);

        // If a script file is specified and no inline script, read from file
        if (scriptText.isBlank() && !scriptFile.isBlank()) {
            try {
                scriptText = Files.readString(Path.of(scriptFile), StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOG.log(Level.WARNING,
                        "JSR223PostProcessorExecutor: cannot read script file " + scriptFile, e);
                return;
            }
        }

        if (scriptText.isBlank()) {
            LOG.log(Level.FINE,
                    "JSR223PostProcessorExecutor [{0}]: no script text or file — skipping",
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
                    "JSR223PostProcessorExecutor: no ScriptEngine found for language ''{0}''",
                    language);
            return;
        }

        // Set up bindings — same as JSR223PreProcessor but with prev (SampleResult)
        Bindings bindings = engine.createBindings();
        bindings.put("prev", result);
        bindings.put("SampleResult", result);
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
                    "JSR223PostProcessorExecutor: script execution failed", e);
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "JSR223PostProcessorExecutor: unexpected error", e);
        }
    }

    private static String resolve(String value, Map<String, String> variables) {
        if (value == null) return "";
        return VariableResolver.resolve(value, variables);
    }
}
