package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code BeanShellPreProcessor} {@link PlanNode}.
 *
 * <p>Delegates to {@link JSR223PreProcessorExecutor} with {@code scriptLanguage}
 * set to {@code "beanshell"}. If no BeanShell ScriptEngine is available, falls back
 * to {@code "javascript"}.
 *
 * <p>Reads the following properties:
 * <ul>
 *   <li>{@code BeanShellPreProcessor.script} — inline BeanShell script</li>
 *   <li>{@code BeanShellPreProcessor.filename} — external script file</li>
 *   <li>{@code BeanShellPreProcessor.parameters} — parameters string</li>
 *   <li>{@code BeanShellPreProcessor.resetInterpreter} — boolean, reset between calls</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class BeanShellPreProcessorExecutor {

    private static final Logger LOG = Logger.getLogger(BeanShellPreProcessorExecutor.class.getName());

    private BeanShellPreProcessorExecutor() {
        // utility class — not instantiable
    }

    /**
     * Executes the BeanShell pre-processor script described by {@code node}.
     *
     * @param node      the BeanShellPreProcessor node; must not be {@code null}
     * @param variables current VU variable scope; must not be {@code null}
     */
    public static void execute(PlanNode node, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String script = node.getStringProp("BeanShellPreProcessor.script", "");
        String filename = node.getStringProp("BeanShellPreProcessor.filename", "");
        String parameters = node.getStringProp("BeanShellPreProcessor.parameters", "");

        // Determine language: try "beanshell" first
        String language = "beanshell";
        javax.script.ScriptEngineManager manager = new javax.script.ScriptEngineManager();
        if (manager.getEngineByName("beanshell") == null
                && manager.getEngineByName("bsh") == null) {
            LOG.log(Level.INFO,
                    "BeanShellPreProcessorExecutor: BeanShell engine not found, falling back to javascript");
            language = "javascript";
        }

        // Build a synthetic JSR223PreProcessor node with mapped properties
        PlanNode jsr223Node = PlanNode.builder("JSR223PreProcessor", node.getTestName())
                .property("script", script)
                .property("filename", filename)
                .property("scriptLanguage", language)
                .property("parameters", parameters)
                .build();

        JSR223PreProcessorExecutor.execute(jsr223Node, variables);
    }
}
