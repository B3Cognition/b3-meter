package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code ForeachController} {@link PlanNode}.
 *
 * <p>Iterates over variables named {@code prefix_1}, {@code prefix_2}, etc.
 * (or {@code prefix1}, {@code prefix2} when {@code useSeparator} is false)
 * and executes children for each found value, setting the output variable
 * to the current value.
 *
 * <p>Reads the following properties:
 * <ul>
 *   <li>{@code ForeachController.inputVal} — variable name prefix to iterate over</li>
 *   <li>{@code ForeachController.returnVal} — output variable name set on each iteration</li>
 *   <li>{@code ForeachController.startIndex} — start index (default 0; iteration begins at startIndex+1)</li>
 *   <li>{@code ForeachController.endIndex} — end index (exclusive); 0 or empty means no upper bound</li>
 *   <li>{@code ForeachController.useSeparator} — whether to use "_" between prefix and index (default true)</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class ForEachControllerExecutor {

    private static final Logger LOG = Logger.getLogger(ForEachControllerExecutor.class.getName());

    /** Safety cap to prevent runaway iteration when variables are very numerous. */
    private static final int MAX_ITERATIONS = 100_000;

    private final NodeInterpreter interpreter;

    /**
     * Constructs a {@code ForEachControllerExecutor}.
     *
     * @param interpreter the parent interpreter for executing child nodes; must not be {@code null}
     */
    public ForEachControllerExecutor(NodeInterpreter interpreter) {
        this.interpreter = Objects.requireNonNull(interpreter, "interpreter must not be null");
    }

    /**
     * Executes the ForEach controller: iterates over indexed variables and runs
     * children for each found value.
     *
     * @param node      the ForeachController node; must not be {@code null}
     * @param variables mutable VU variable map
     * @return list of all {@link SampleResult}s produced across all iterations
     */
    public List<SampleResult> execute(PlanNode node, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String inputVal = node.getStringProp("ForeachController.inputVal", "");
        String returnVal = node.getStringProp("ForeachController.returnVal", "");
        int startIndex = node.getIntProp("ForeachController.startIndex", 0);
        int endIndex = node.getIntProp("ForeachController.endIndex", 0);
        boolean useSeparator = node.getBoolProp("ForeachController.useSeparator", true);

        if (inputVal.isBlank() || returnVal.isBlank()) {
            LOG.log(Level.FINE,
                    "ForEachControllerExecutor [{0}]: inputVal or returnVal is blank — skipping",
                    node.getTestName());
            return List.of();
        }

        String separator = useSeparator ? "_" : "";

        List<SampleResult> allResults = new ArrayList<>();
        int iterCount = 0;

        // JMeter iterates from startIndex+1 upward
        for (int i = startIndex + 1; iterCount < MAX_ITERATIONS; i++) {
            if (Thread.currentThread().isInterrupted()) break;

            // Check endIndex bound (endIndex is exclusive, 0 means no bound)
            if (endIndex > 0 && i > endIndex) break;

            String varName = inputVal + separator + i;
            String value = variables.get(varName);

            if (value == null) {
                // No more variables — stop iterating
                break;
            }

            // Set the output variable for this iteration
            variables.put(returnVal, value);

            LOG.log(Level.FINE,
                    "ForEachControllerExecutor [{0}]: iteration {1}, {2} = {3}",
                    new Object[]{node.getTestName(), i, returnVal, value});

            List<SampleResult> iterResults = interpreter.executeChildren(
                    node.getChildren(), variables);
            allResults.addAll(iterResults);
            iterCount++;
        }

        LOG.log(Level.FINE,
                "ForEachControllerExecutor [{0}]: completed {1} iteration(s)",
                new Object[]{node.getTestName(), iterCount});

        return allResults;
    }
}
