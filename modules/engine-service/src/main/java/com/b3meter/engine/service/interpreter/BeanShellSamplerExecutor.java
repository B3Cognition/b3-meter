package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code BeanShellSampler} {@link PlanNode}.
 *
 * <p>Reads the following sampler properties:
 * <ul>
 *   <li>{@code BeanShellSampler.script} — inline BeanShell/Java-like script</li>
 *   <li>{@code BeanShellSampler.filename} — external script file</li>
 *   <li>{@code BeanShellSampler.parameters} — parameters string</li>
 *   <li>{@code BeanShellSampler.resetInterpreter} — boolean, reset between calls</li>
 * </ul>
 *
 * <p>Implementation delegates to {@link JSR223SamplerExecutor} with language set to
 * {@code "beanshell"}. If no BeanShell ScriptEngine is available, falls back to
 * {@code "javascript"}.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class BeanShellSamplerExecutor {

    private static final Logger LOG = Logger.getLogger(BeanShellSamplerExecutor.class.getName());

    private BeanShellSamplerExecutor() {
        // utility class — not instantiable
    }

    /**
     * Executes the BeanShell script described by {@code node}.
     *
     * @param node      the BeanShellSampler node; must not be {@code null}
     * @param result    the sample result to populate; must not be {@code null}
     * @param variables current VU variable scope; must not be {@code null}
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        // Map BeanShell properties to JSR223 properties via a synthetic PlanNode
        String script = node.getStringProp("BeanShellSampler.script", "");
        String filename = node.getStringProp("BeanShellSampler.filename", "");
        String parameters = node.getStringProp("BeanShellSampler.parameters", "");

        // Determine language: try "beanshell" first; JSR223SamplerExecutor handles engine lookup
        // and will report if the engine is not found
        String language = "beanshell";

        // Check if BeanShell engine is available; if not, fall back to javascript
        javax.script.ScriptEngineManager manager = new javax.script.ScriptEngineManager();
        if (manager.getEngineByName("beanshell") == null && manager.getEngineByName("bsh") == null) {
            LOG.log(Level.INFO,
                    "BeanShellSamplerExecutor: BeanShell engine not found, falling back to javascript");
            language = "javascript";
        }

        // Build a synthetic JSR223 node with mapped properties
        PlanNode jsr223Node = PlanNode.builder("JSR223Sampler", node.getTestName())
                .property("JSR223Sampler.script", script)
                .property("JSR223Sampler.scriptFile", filename)
                .property("JSR223Sampler.scriptLanguage", language)
                .property("JSR223Sampler.parameters", parameters)
                .build();

        JSR223SamplerExecutor.execute(jsr223Node, result, variables);
    }
}
