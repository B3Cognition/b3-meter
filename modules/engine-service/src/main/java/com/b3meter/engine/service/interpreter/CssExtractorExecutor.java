package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes an {@code HtmlExtractor} (CSS/JQuery Extractor) {@link PlanNode} as a post-processor.
 *
 * <p>Reads the standard JMeter HtmlExtractor properties:
 * <ul>
 *   <li>{@code HtmlExtractor.expr} — CSS selector expression</li>
 *   <li>{@code HtmlExtractor.refname} — variable name to store result</li>
 *   <li>{@code HtmlExtractor.attribute} — attribute to extract (empty = text content)</li>
 *   <li>{@code HtmlExtractor.default} — default value if not found</li>
 *   <li>{@code HtmlExtractor.match_number} — which match to use (1-based)</li>
 * </ul>
 *
 * <p>Uses a simplified regex-based CSS selector matching approach. Supports basic tag, class,
 * and ID selectors. Full CSS selector support would require an HTML parser library.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class CssExtractorExecutor {

    private static final Logger LOG = Logger.getLogger(CssExtractorExecutor.class.getName());

    private CssExtractorExecutor() {
        // utility class -- not instantiable
    }

    /**
     * Extracts a value using a CSS selector from the response body and stores it in variables.
     *
     * @param node      the HtmlExtractor node; must not be {@code null}
     * @param result    the most recent sample result; must not be {@code null}
     * @param variables mutable VU variable map
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node,      "node must not be null");
        Objects.requireNonNull(result,    "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String expr        = node.getStringProp("HtmlExtractor.expr", "");
        String refName     = node.getStringProp("HtmlExtractor.refname", "");
        String attribute   = node.getStringProp("HtmlExtractor.attribute", "");
        String defaultVal  = node.getStringProp("HtmlExtractor.default", "");
        String matchNumStr = node.getStringProp("HtmlExtractor.match_number", "1");

        if (refName.isBlank()) {
            LOG.log(Level.WARNING, "CssExtractor [{0}]: refname is empty — skipping",
                    node.getTestName());
            return;
        }

        if (expr.isBlank()) {
            variables.put(refName, defaultVal);
            return;
        }

        int matchNumber;
        try {
            matchNumber = Integer.parseInt(matchNumStr.trim());
        } catch (NumberFormatException e) {
            matchNumber = 1;
        }

        String body = result.getResponseBody();
        if (body == null || body.isBlank()) {
            variables.put(refName, defaultVal);
            return;
        }

        try {
            // Build a regex pattern from the CSS selector
            Pattern tagPattern = buildTagPattern(expr);
            List<String> matches = new ArrayList<>();

            Matcher matcher = tagPattern.matcher(body);
            while (matcher.find()) {
                String element = matcher.group(0);
                if (matchesCssSelector(element, expr)) {
                    String value;
                    if (attribute.isBlank() || "text".equalsIgnoreCase(attribute)) {
                        value = extractTextContent(element);
                    } else {
                        value = extractAttribute(element, attribute);
                    }
                    if (value != null) {
                        matches.add(value);
                    }
                }
            }

            if (matches.isEmpty()) {
                variables.put(refName, defaultVal);
                variables.put(refName + "_matchNr", "0");
                return;
            }

            variables.put(refName + "_matchNr", String.valueOf(matches.size()));

            String extracted;
            if (matchNumber == 0) {
                // Random
                extracted = matches.get((int) (Math.random() * matches.size()));
            } else if (matchNumber == -1) {
                // All
                for (int i = 0; i < matches.size(); i++) {
                    variables.put(refName + "_" + (i + 1), matches.get(i));
                }
                extracted = matches.get(0);
            } else {
                int idx = Math.min(matchNumber - 1, matches.size() - 1);
                extracted = matches.get(idx);
            }

            variables.put(refName, extracted);
            LOG.log(Level.FINE, "CssExtractor [{0}]: {1} = {2}",
                    new Object[]{node.getTestName(), refName, extracted});

        } catch (Exception e) {
            variables.put(refName, defaultVal);
            LOG.log(Level.WARNING, "CssExtractor [" + node.getTestName() + "]: error — " + e.getMessage(), e);
        }
    }

    /**
     * Builds a regex pattern to find HTML elements matching a basic CSS selector.
     * Supports: tag, tag.class, tag#id, .class, #id, tag[attr]
     */
    static Pattern buildTagPattern(String selector) {
        // Extract tag name from selector
        String tag = extractTagName(selector);

        if (tag.isEmpty()) {
            // Match any tag
            return Pattern.compile("<(\\w+)(\\s[^>]*)?>[\\s\\S]*?</\\1>|<(\\w+)(\\s[^>]*)?>",
                    Pattern.CASE_INSENSITIVE);
        }

        // Match opening tag with content (self-closing or with closing tag)
        return Pattern.compile("<" + Pattern.quote(tag) + "(\\s[^>]*)?>([\\s\\S]*?)</" + Pattern.quote(tag) + ">"
                        + "|<" + Pattern.quote(tag) + "(\\s[^>]*)?/?>",
                Pattern.CASE_INSENSITIVE);
    }

    /**
     * Extracts the tag name from a CSS selector. Returns empty string if no tag specified.
     */
    static String extractTagName(String selector) {
        selector = selector.trim();
        if (selector.startsWith(".") || selector.startsWith("#") || selector.startsWith("[")) {
            return "";
        }
        // Extract tag up to first . # [ or space
        int end = selector.length();
        for (int i = 0; i < selector.length(); i++) {
            char c = selector.charAt(i);
            if (c == '.' || c == '#' || c == '[' || c == ' ' || c == '>') {
                end = i;
                break;
            }
        }
        return selector.substring(0, end).toLowerCase();
    }

    /**
     * Checks if an HTML element string matches the CSS selector.
     */
    static boolean matchesCssSelector(String element, String selector) {
        selector = selector.trim();

        // Check class selector (.className)
        int dotIdx = selector.indexOf('.');
        if (dotIdx >= 0) {
            int end = selector.indexOf(' ', dotIdx);
            if (end < 0) end = selector.indexOf('#', dotIdx + 1);
            if (end < 0) end = selector.indexOf('[', dotIdx + 1);
            if (end < 0) end = selector.length();
            String className = selector.substring(dotIdx + 1, end);
            if (!matchesClass(element, className)) return false;
        }

        // Check ID selector (#idName)
        int hashIdx = selector.indexOf('#');
        if (hashIdx >= 0) {
            int end = selector.indexOf(' ', hashIdx);
            if (end < 0) end = selector.indexOf('.', hashIdx + 1);
            if (end < 0) end = selector.indexOf('[', hashIdx + 1);
            if (end < 0) end = selector.length();
            String idName = selector.substring(hashIdx + 1, end);
            if (!matchesId(element, idName)) return false;
        }

        return true;
    }

    private static boolean matchesClass(String element, String className) {
        // Look for class="...className..." in the element
        Pattern p = Pattern.compile("class\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(element);
        if (m.find()) {
            String classes = m.group(1);
            for (String cls : classes.split("\\s+")) {
                if (cls.equals(className)) return true;
            }
        }
        return false;
    }

    private static boolean matchesId(String element, String idName) {
        Pattern p = Pattern.compile("id\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(element);
        return m.find() && m.group(1).equals(idName);
    }

    /**
     * Extracts the text content from an HTML element (strips inner tags).
     */
    static String extractTextContent(String element) {
        // Find content between opening and closing tags
        int closeStart = element.indexOf('>');
        if (closeStart < 0) return "";
        int closeEnd = element.lastIndexOf('<');
        if (closeEnd <= closeStart) return "";
        String content = element.substring(closeStart + 1, closeEnd);
        // Strip inner tags
        return content.replaceAll("<[^>]+>", "").trim();
    }

    /**
     * Extracts an attribute value from an HTML element.
     */
    static String extractAttribute(String element, String attributeName) {
        Pattern p = Pattern.compile(
                Pattern.quote(attributeName) + "\\s*=\\s*[\"']([^\"']*)[\"']",
                Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(element);
        return m.find() ? m.group(1) : null;
    }
}
