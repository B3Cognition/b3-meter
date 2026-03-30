package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes an {@code OnceOnlyController} {@link PlanNode}.
 *
 * <p>Executes its children only on the first iteration of the parent loop
 * within each VU thread. On subsequent iterations, children are skipped.
 * This is commonly used for login or setup operations that should happen
 * only once per VU.
 *
 * <p>The "first iteration" flag is tracked via a well-known variable key
 * in the VU variable map, scoped to the controller's testName. This ensures
 * each OnceOnlyController instance tracks its own state independently.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class OnceOnlyControllerExecutor {

    private static final Logger LOG = Logger.getLogger(OnceOnlyControllerExecutor.class.getName());

    /** Prefix for the tracking variable stored in the VU variable map. */
    private static final String ONCE_FLAG_PREFIX = "__jmn_once_executed_";

    private final NodeInterpreter interpreter;

    /**
     * Constructs an {@code OnceOnlyControllerExecutor}.
     *
     * @param interpreter the parent interpreter for executing child nodes; must not be {@code null}
     */
    public OnceOnlyControllerExecutor(NodeInterpreter interpreter) {
        this.interpreter = Objects.requireNonNull(interpreter, "interpreter must not be null");
    }

    /**
     * Executes children only if this is the first time this controller has been
     * reached within the current VU's variable scope.
     *
     * @param node      the OnceOnlyController node; must not be {@code null}
     * @param variables mutable VU variable map
     * @return list of {@link SampleResult}s from children if this is the first iteration;
     *         empty list on subsequent iterations
     */
    public List<SampleResult> execute(PlanNode node, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String flagKey = ONCE_FLAG_PREFIX + node.getTestName();
        String alreadyExecuted = variables.get(flagKey);

        if ("true".equals(alreadyExecuted)) {
            LOG.log(Level.FINE,
                    "OnceOnlyControllerExecutor [{0}]: already executed — skipping",
                    node.getTestName());
            return new ArrayList<>();
        }

        // Mark as executed before running children
        variables.put(flagKey, "true");

        LOG.log(Level.FINE,
                "OnceOnlyControllerExecutor [{0}]: first iteration — executing children",
                node.getTestName());

        return interpreter.executeChildren(node.getChildren(), variables);
    }
}
