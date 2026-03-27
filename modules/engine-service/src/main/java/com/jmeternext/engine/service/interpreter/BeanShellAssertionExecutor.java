package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code BeanShellAssertion} {@link PlanNode} against a prior {@link SampleResult}.
 *
 * <p>Delegates to {@link JSR223AssertionExecutor} with {@code scriptLanguage}
 * set to {@code "beanshell"}. If no BeanShell ScriptEngine is available, falls back
 * to {@code "javascript"}.
 *
 * <p>Reads the following properties:
 * <ul>
 *   <li>{@code BeanShellAssertion.script} — inline BeanShell script</li>
 *   <li>{@code BeanShellAssertion.filename} — external script file</li>
 *   <li>{@code BeanShellAssertion.parameters} — parameters string</li>
 *   <li>{@code BeanShellAssertion.resetInterpreter} — boolean, reset between calls</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class BeanShellAssertionExecutor {

    private static final Logger LOG = Logger.getLogger(BeanShellAssertionExecutor.class.getName());

    private BeanShellAssertionExecutor() {
        // utility class -- not instantiable
    }

    /**
     * Applies the BeanShell assertion described by {@code node} to {@code result}.
     *
     * @param node      the BeanShellAssertion node; must not be {@code null}
     * @param result    the most recent sample result to validate; must not be {@code null}
     * @param variables current VU variable scope; must not be {@code null}
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node,      "node must not be null");
        Objects.requireNonNull(result,    "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String script     = node.getStringProp("BeanShellAssertion.script", "");
        String filename   = node.getStringProp("BeanShellAssertion.filename", "");
        String parameters = node.getStringProp("BeanShellAssertion.parameters", "");

        // Determine language: try "beanshell" first
        String language = "beanshell";
        javax.script.ScriptEngineManager manager = new javax.script.ScriptEngineManager();
        if (manager.getEngineByName("beanshell") == null
                && manager.getEngineByName("bsh") == null) {
            LOG.log(Level.INFO,
                    "BeanShellAssertionExecutor: BeanShell engine not found, falling back to javascript");
            language = "javascript";
        }

        // Build a synthetic JSR223Assertion node with mapped properties
        PlanNode jsr223Node = PlanNode.builder("JSR223Assertion", node.getTestName())
                .property("script", script)
                .property("filename", filename)
                .property("scriptLanguage", language)
                .property("parameters", parameters)
                .build();

        JSR223AssertionExecutor.execute(jsr223Node, result, variables);
    }
}
