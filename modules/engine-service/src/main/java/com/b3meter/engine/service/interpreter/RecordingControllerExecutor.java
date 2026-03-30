package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code RecordingController} {@link PlanNode}.
 *
 * <p>The Recording Controller is a container that holds HTTP requests recorded
 * by the JMeter Proxy Recorder. At execution time, it simply executes all its
 * children sequentially (same as {@code SimpleController}).
 *
 * <p>The actual recording functionality is handled by the ProxyRecorder — this
 * executor only handles runtime execution of the recorded children.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class RecordingControllerExecutor {

    private static final Logger LOG = Logger.getLogger(RecordingControllerExecutor.class.getName());

    private final NodeInterpreter interpreter;

    /**
     * Constructs a {@code RecordingControllerExecutor}.
     *
     * @param interpreter the parent interpreter for executing child nodes; must not be {@code null}
     */
    public RecordingControllerExecutor(NodeInterpreter interpreter) {
        this.interpreter = Objects.requireNonNull(interpreter, "interpreter must not be null");
    }

    /**
     * Executes all children of the recording controller sequentially.
     *
     * @param node      the RecordingController node; must not be {@code null}
     * @param variables mutable VU variable map
     * @return list of {@link SampleResult}s from all children
     */
    public List<SampleResult> execute(PlanNode node, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        LOG.log(Level.FINE,
                "RecordingControllerExecutor [{0}]: executing {1} children",
                new Object[]{node.getTestName(), node.getChildren().size()});

        return interpreter.executeChildren(node.getChildren(), variables);
    }
}
