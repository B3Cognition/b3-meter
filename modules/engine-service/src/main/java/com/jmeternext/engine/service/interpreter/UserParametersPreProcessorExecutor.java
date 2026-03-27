package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code UserParameters} {@link PlanNode} pre-processor.
 *
 * <p>User Parameters define per-thread parameter values inline in the test plan.
 * Each invocation sets variables for the current VU, cycling through value sets
 * in round-robin fashion across iterations.
 *
 * <p>Reads the following properties:
 * <ul>
 *   <li>{@code UserParameters.names} — collectionProp containing parameter names</li>
 *   <li>{@code UserParameters.thread_values} — collectionProp of collectionProps,
 *       each inner collection containing the values for one parameter set</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class UserParametersPreProcessorExecutor {

    private static final Logger LOG = Logger.getLogger(UserParametersPreProcessorExecutor.class.getName());

    /**
     * Shared iteration counters keyed by node testName.
     * Used for round-robin cycling through value sets.
     */
    private static final ConcurrentHashMap<String, AtomicInteger> ITERATION_COUNTERS =
            new ConcurrentHashMap<>();

    private UserParametersPreProcessorExecutor() {
        // utility class — not instantiable
    }

    /**
     * Executes the UserParameters pre-processor, setting variables from the
     * appropriate value set based on the current round-robin index.
     *
     * @param node      the UserParameters node; must not be {@code null}
     * @param variables current VU variable scope; must not be {@code null}
     */
    public static void execute(PlanNode node, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        List<Object> names = node.getCollectionProp("UserParameters.names");
        List<Object> threadValues = node.getCollectionProp("UserParameters.thread_values");

        if (names.isEmpty() || threadValues.isEmpty()) {
            LOG.log(Level.FINE,
                    "UserParametersPreProcessorExecutor [{0}]: no names or values — skipping",
                    node.getTestName());
            return;
        }

        // Determine the value set index via round-robin
        int numValueSets = threadValues.size();
        AtomicInteger counter = ITERATION_COUNTERS.computeIfAbsent(
                node.getTestName(), k -> new AtomicInteger(0));
        int iterationIndex = counter.getAndIncrement() % numValueSets;

        // Get the value set for this iteration
        Object valueSetObj = threadValues.get(iterationIndex);

        List<Object> valueSet;
        if (valueSetObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> cast = (List<Object>) valueSetObj;
            valueSet = cast;
        } else {
            LOG.log(Level.WARNING,
                    "UserParametersPreProcessorExecutor [{0}]: value set at index {1} is not a list",
                    new Object[]{node.getTestName(), iterationIndex});
            return;
        }

        // Set each name = value pair
        for (int i = 0; i < names.size(); i++) {
            String name = String.valueOf(names.get(i));
            String value = (i < valueSet.size()) ? String.valueOf(valueSet.get(i)) : "";
            variables.put(name, value);

            LOG.log(Level.FINE,
                    "UserParametersPreProcessorExecutor [{0}]: set {1} = {2}",
                    new Object[]{node.getTestName(), name, value});
        }
    }

    /**
     * Resets all iteration counters. Intended for testing only.
     */
    static void resetCounters() {
        ITERATION_COUNTERS.clear();
    }
}
