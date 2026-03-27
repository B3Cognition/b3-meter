package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code BSFSampler} {@link PlanNode} by delegating to {@link JSR223SamplerExecutor}.
 *
 * <p>BSF (Bean Scripting Framework) is deprecated in JMeter 5.x in favour of JSR223.
 * This executor maps BSF properties to JSR223 properties and delegates execution.
 *
 * <p>Reads the following sampler properties:
 * <ul>
 *   <li>{@code BSFSampler.language} — scripting language name</li>
 *   <li>{@code BSFSampler.filename} — external script file path</li>
 *   <li>{@code BSFSampler.parameters} — parameters string</li>
 *   <li>{@code BSFSampler.script} — inline script text</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class BSFSamplerExecutor {

    private static final Logger LOG = Logger.getLogger(BSFSamplerExecutor.class.getName());

    private BSFSamplerExecutor() {
        // utility class -- not instantiable
    }

    /**
     * Executes the BSF script by delegating to JSR223.
     *
     * @param node      the BSFSampler node; must not be {@code null}
     * @param result    the sample result to populate; must not be {@code null}
     * @param variables current VU variable scope
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String language = node.getStringProp("BSFSampler.language", "javascript");
        String filename = node.getStringProp("BSFSampler.filename", "");
        String parameters = node.getStringProp("BSFSampler.parameters", "");
        String script = node.getStringProp("BSFSampler.script", "");

        LOG.log(Level.INFO,
                "BSFSamplerExecutor: BSF is deprecated -- delegating to JSR223 with language={0}",
                language);

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
