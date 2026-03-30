package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Executes a {@code ResponseAssertion} {@link PlanNode} against a prior {@link SampleResult}.
 *
 * <p>Reads the standard JMeter ResponseAssertion properties:
 * <ul>
 *   <li>{@code Assertion.test_field} — field to test:
 *       {@code Assertion.response_data} (body, default),
 *       {@code Assertion.response_code} (status code),
 *       {@code Assertion.response_headers}</li>
 *   <li>{@code Assertion.test_type} — bitmask integer:
 *       {@code 1}=Substring (default), {@code 2}=Contains (regex), {@code 8}=Equals, {@code 16}=Matches</li>
 *   <li>{@code Assertion.assume_success} — if {@code true}, ignore current success state</li>
 *   <li>{@code Asserion.test_strings} — collectionProp of pattern strings to match</li>
 * </ul>
 *
 * <p>If any pattern fails to match, the sample result is marked as failed and a failure
 * message is appended.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class AssertionExecutor {

    private static final Logger LOG = Logger.getLogger(AssertionExecutor.class.getName());

    // test_type bitmask constants (mirrors JMeter's AssertionGui constants)
    private static final int TEST_MATCH     = 1;   // regex full-match
    private static final int TEST_CONTAINS  = 2;   // regex find
    private static final int TEST_EQUALS    = 8;   // exact string equality
    private static final int TEST_SUBSTRING = 16;  // simple substring

    private AssertionExecutor() {
        // utility class — not instantiable
    }

    /**
     * Applies the {@code ResponseAssertion} described by {@code node} to {@code result}.
     *
     * <p>If the assertion fails, {@link SampleResult#setFailureMessage} is called with a
     * descriptive message, which also flips {@link SampleResult#isSuccess()} to {@code false}.
     *
     * @param node      the ResponseAssertion node; must not be {@code null}
     * @param result    the most recent sample result to validate; must not be {@code null}
     * @param variables current VU variable scope for pattern substitution
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node,      "node must not be null");
        Objects.requireNonNull(result,    "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        boolean assumeSuccess = node.getBoolProp("Assertion.assume_success");
        if (assumeSuccess) {
            // Reset a prior failure before applying this assertion's logic
            result.setSuccess(true);
        }

        // Determine which field to test
        String testField = node.getStringProp("Assertion.test_field", "Assertion.response_data");
        String subject   = resolveSubject(testField, result);

        // test_type: JMeter stores this as an int (bitmask)
        int testType = node.getIntProp("Assertion.test_type", TEST_SUBSTRING);

        // Collect patterns from the collectionProp
        List<Object> patterns = node.getCollectionProp("Asserion.test_strings");
        if (patterns.isEmpty()) {
            // Some JMX files use the correct spelling
            patterns = node.getCollectionProp("Assertion.test_strings");
        }

        if (patterns.isEmpty()) {
            LOG.log(Level.FINE, "ResponseAssertion [{0}]: no test strings defined — skipping",
                    node.getTestName());
            return;
        }

        for (Object patternObj : patterns) {
            String pattern = patternObj instanceof String s ? s : String.valueOf(patternObj);
            pattern = VariableResolver.resolve(pattern, variables);

            boolean matched = testPattern(subject, pattern, testType);
            if (!matched) {
                String msg = buildFailureMessage(node.getTestName(), testField, testType, pattern, subject);
                result.setFailureMessage(msg);
                LOG.log(Level.FINE, "ResponseAssertion [{0}]: FAILED — {1}",
                        new Object[]{node.getTestName(), msg});
                // Continue checking remaining patterns — JMeter fails on first mismatch
                return;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private static String resolveSubject(String testField, SampleResult result) {
        return switch (testField) {
            case "Assertion.response_code"    -> String.valueOf(result.getStatusCode());
            case "Assertion.response_headers" -> ""; // headers not tracked in SampleResult
            default                            -> result.getResponseBody();
        };
    }

    private static boolean testPattern(String subject, String pattern, int testType) {
        if (subject == null) subject = "";

        // JMeter's bitmask: check highest-priority bits first
        if ((testType & TEST_EQUALS) != 0) {
            return subject.equals(pattern);
        }
        if ((testType & TEST_SUBSTRING) != 0) {
            return subject.contains(pattern);
        }
        if ((testType & TEST_MATCH) != 0) {
            // Full regex match (anchored)
            try {
                return Pattern.compile(pattern, Pattern.DOTALL).matcher(subject).matches();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "ResponseAssertion: invalid regex pattern: " + pattern, e);
                return false;
            }
        }
        if ((testType & TEST_CONTAINS) != 0) {
            // Regex find (un-anchored)
            try {
                return Pattern.compile(pattern, Pattern.DOTALL).matcher(subject).find();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "ResponseAssertion: invalid regex pattern: " + pattern, e);
                return false;
            }
        }

        // Default: substring
        return subject.contains(pattern);
    }

    private static String buildFailureMessage(String assertionName, String testField,
                                               int testType, String pattern, String subject) {
        String typeName;
        if ((testType & TEST_EQUALS) != 0)     typeName = "equals";
        else if ((testType & TEST_SUBSTRING) != 0) typeName = "contains substring";
        else if ((testType & TEST_MATCH) != 0)  typeName = "matches";
        else if ((testType & TEST_CONTAINS) != 0) typeName = "contains (regex)";
        else                                    typeName = "unknown";

        String shortSubject = subject != null && subject.length() > 200
                ? subject.substring(0, 200) + "…"
                : subject;

        return "Assertion [" + assertionName + "] FAILED: "
                + testField + " " + typeName + " '" + pattern + "'"
                + " — actual: '" + shortSubject + "'";
    }
}
