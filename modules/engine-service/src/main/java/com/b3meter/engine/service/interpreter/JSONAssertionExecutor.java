package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Executes a {@code JSONPathAssertion} {@link PlanNode} against a prior {@link SampleResult}.
 *
 * <p>Reads the standard JMeter JSONPathAssertion properties:
 * <ul>
 *   <li>{@code JSON_PATH} — JSONPath expression to evaluate</li>
 *   <li>{@code EXPECTED_VALUE} — expected value to match</li>
 *   <li>{@code JSONVALIDATION} — if true, validate the extracted value against EXPECTED_VALUE</li>
 *   <li>{@code EXPECT_NULL} — if true, expect a null value</li>
 *   <li>{@code INVERT} — if true, invert the assertion result</li>
 *   <li>{@code ISREGEX} — if true, treat EXPECTED_VALUE as a regex pattern</li>
 * </ul>
 *
 * <p>Reuses the JSONPath evaluator from {@link ExtractorExecutor}.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class JSONAssertionExecutor {

    private static final Logger LOG = Logger.getLogger(JSONAssertionExecutor.class.getName());

    private JSONAssertionExecutor() {
        // utility class -- not instantiable
    }

    /**
     * Applies the JSON assertion described by {@code node} to {@code result}.
     *
     * @param node      the JSONPathAssertion node; must not be {@code null}
     * @param result    the most recent sample result to validate; must not be {@code null}
     * @param variables current VU variable scope for value substitution
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node,      "node must not be null");
        Objects.requireNonNull(result,    "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String jsonPath       = VariableResolver.resolve(node.getStringProp("JSON_PATH", ""), variables);
        String expectedValue  = VariableResolver.resolve(node.getStringProp("EXPECTED_VALUE", ""), variables);
        boolean jsonValidation = node.getBoolProp("JSONVALIDATION", true);
        boolean expectNull    = node.getBoolProp("EXPECT_NULL");
        boolean invert        = node.getBoolProp("INVERT");
        boolean isRegex       = node.getBoolProp("ISREGEX");

        if (jsonPath.isBlank()) {
            result.setFailureMessage("JSONPathAssertion [" + node.getTestName() + "]: JSON_PATH is empty");
            return;
        }

        String body = result.getResponseBody();
        if (body == null || body.isBlank()) {
            applyResult(result, node.getTestName(), false, invert,
                    "Response body is empty — cannot evaluate JSONPath");
            return;
        }

        // Evaluate JSONPath using the shared ExtractorExecutor utility
        String extracted;
        try {
            extracted = ExtractorExecutor.extractJsonPath(body, jsonPath, null);
        } catch (Exception e) {
            applyResult(result, node.getTestName(), false, invert,
                    "JSONPath evaluation error: " + e.getMessage());
            return;
        }

        // Check path existence
        if (extracted == null) {
            if (expectNull) {
                applyResult(result, node.getTestName(), true, invert, null);
            } else {
                applyResult(result, node.getTestName(), false, invert,
                        "JSONPath '" + jsonPath + "' not found in response");
            }
            return;
        }

        // If we expect null but got a value
        if (expectNull) {
            boolean isNull = "null".equals(extracted);
            applyResult(result, node.getTestName(), isNull, invert,
                    isNull ? null : "Expected null but got '" + truncate(extracted) + "'");
            return;
        }

        // If no value validation requested, just check path exists
        if (!jsonValidation) {
            applyResult(result, node.getTestName(), true, invert, null);
            return;
        }

        // Compare values
        boolean matched;
        if (isRegex) {
            try {
                matched = Pattern.compile(expectedValue, Pattern.DOTALL).matcher(extracted).find();
            } catch (Exception e) {
                applyResult(result, node.getTestName(), false, invert,
                        "Invalid regex pattern: " + expectedValue);
                return;
            }
        } else {
            matched = expectedValue.equals(extracted);
        }

        applyResult(result, node.getTestName(), matched, invert,
                matched ? null : "Expected '" + expectedValue + "' but got '" + truncate(extracted)
                        + "' for JSONPath '" + jsonPath + "'");
    }

    private static void applyResult(SampleResult result, String testName,
                                     boolean passed, boolean invert, String message) {
        boolean finalResult = invert ? !passed : passed;
        if (!finalResult) {
            String prefix = invert ? " (inverted)" : "";
            String msg = "JSONPathAssertion [" + testName + "]" + prefix + " FAILED"
                    + (message != null ? ": " + message : "");
            result.setFailureMessage(msg);
            LOG.log(Level.FINE, msg);
        }
    }

    private static String truncate(String value) {
        if (value != null && value.length() > 200) {
            return value.substring(0, 200) + "...";
        }
        return value;
    }
}
