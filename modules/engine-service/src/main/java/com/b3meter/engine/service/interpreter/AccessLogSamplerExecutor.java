package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.http.HttpClientFactory;
import com.jmeternext.engine.service.plan.PlanNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes an {@code AccessLogSampler} {@link PlanNode} by reading an Apache
 * access log and replaying each parsed request via {@link HttpSamplerExecutor}.
 *
 * <p>Reads the following sampler properties:
 * <ul>
 *   <li>{@code AccessLogSampler.log_file} — path to Apache access log file</li>
 *   <li>{@code AccessLogSampler.filter_class} — optional filter class name (ignored)</li>
 *   <li>{@code AccessLogSampler.domain} — target domain for replaying requests</li>
 *   <li>{@code AccessLogSampler.port} — target port (default 80)</li>
 *   <li>{@code AccessLogSampler.protocol} — http or https (default http)</li>
 * </ul>
 *
 * <p>This implementation parses the access log file line-by-line, extracts the
 * HTTP method and path from each entry in Combined/Common Log Format, and
 * delegates to {@link HttpSamplerExecutor} for the actual HTTP request.
 *
 * <p>The result aggregates timing from all replayed requests. If no
 * {@link HttpClientFactory} is provided (legacy 3-arg call), falls back to
 * parse-only mode that reports extracted URLs without sending HTTP requests.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class AccessLogSamplerExecutor {

    private static final Logger LOG = Logger.getLogger(AccessLogSamplerExecutor.class.getName());

    /** Maximum number of log lines to process per invocation. */
    private static final int MAX_LINES = 1000;

    /** Pattern for Combined/Common Log Format: extracts method, path, and protocol. */
    static final Pattern ACCESS_LOG_PATTERN = Pattern.compile(
            "\"(GET|POST|PUT|DELETE|HEAD|OPTIONS|PATCH)\\s+(\\S+)\\s+HTTP/[\\d.]+\"");

    private AccessLogSamplerExecutor() {
        // utility class -- not instantiable
    }

    /**
     * Executes the access log sampler with HTTP replay support.
     *
     * <p>Parses the log file and replays each request through
     * {@link HttpSamplerExecutor}. The result contains the aggregate of all
     * replayed requests.
     *
     * @param node              the AccessLogSampler node; must not be {@code null}
     * @param result            the sample result to populate; must not be {@code null}
     * @param variables         current VU variable scope
     * @param httpClientFactory HTTP client factory for replaying requests
     */
    public static void execute(PlanNode node, SampleResult result,
                               Map<String, String> variables,
                               HttpClientFactory httpClientFactory) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String logFile = resolve(node.getStringProp("AccessLogSampler.log_file", ""), variables);
        String domain = resolve(node.getStringProp("AccessLogSampler.domain", ""), variables);
        int port = node.getIntProp("AccessLogSampler.port", 80);
        String protocol = resolve(node.getStringProp("AccessLogSampler.protocol", "http"), variables);

        if (logFile.isBlank()) {
            result.setFailureMessage("AccessLogSampler: log_file is empty");
            return;
        }

        if (domain.isBlank()) {
            result.setFailureMessage("AccessLogSampler: domain is empty");
            return;
        }

        Path path = Path.of(logFile);
        if (!Files.exists(path)) {
            result.setStatusCode(404);
            result.setFailureMessage("AccessLogSampler: log file not found: " + logFile);
            return;
        }

        LOG.log(Level.INFO, "AccessLogSamplerExecutor: replaying access log {0} -> {1}://{2}:{3}",
                new Object[]{logFile, protocol, domain, port});

        // Parse log entries
        List<String[]> entries = parseLogFile(path);

        if (entries.isEmpty()) {
            result.setStatusCode(200);
            result.setResponseBody("AccessLogSampler: no valid log entries found in " + logFile);
            return;
        }

        // Replay each entry via HttpSamplerExecutor
        long startMs = System.currentTimeMillis();
        HttpSamplerExecutor httpExecutor = new HttpSamplerExecutor(httpClientFactory);
        int successCount = 0;
        int errorCount = 0;
        long totalLatency = 0;
        StringBuilder responseLog = new StringBuilder();

        for (String[] entry : entries) {
            String method = entry[0];
            String entryPath = entry[1];

            PlanNode httpNode = PlanNode.builder("HTTPSamplerProxy",
                            node.getTestName() + " [" + method + " " + entryPath + "]")
                    .property("HTTPSampler.domain", domain)
                    .property("HTTPSampler.port", String.valueOf(port))
                    .property("HTTPSampler.path", entryPath)
                    .property("HTTPSampler.method", method)
                    .property("HTTPSampler.protocol", protocol)
                    .build();

            SampleResult httpResult = httpExecutor.execute(httpNode, variables);

            if (httpResult.isSuccess()) {
                successCount++;
            } else {
                errorCount++;
            }
            totalLatency += httpResult.getTotalTimeMs();

            responseLog.append(method).append(' ').append(entryPath)
                    .append(" -> ").append(httpResult.getStatusCode())
                    .append(" (").append(httpResult.getTotalTimeMs()).append("ms)\n");
        }

        long elapsedMs = System.currentTimeMillis() - startMs;
        result.setTotalTimeMs(elapsedMs);
        result.setLatencyMs(entries.isEmpty() ? 0 : totalLatency / entries.size());
        result.setStatusCode(errorCount == 0 ? 200 : 207); // 207 Multi-Status if partial errors
        result.setResponseBody(
                "AccessLogSampler: replayed " + entries.size() + " requests\n"
                + "Success: " + successCount + ", Errors: " + errorCount + "\n"
                + "Total time: " + elapsedMs + "ms, Avg latency: "
                + (entries.isEmpty() ? 0 : totalLatency / entries.size()) + "ms\n"
                + "Details:\n" + responseLog);

        if (errorCount > 0) {
            result.setSuccess(false);
        }
    }

    /**
     * Legacy 3-arg execute method for backward compatibility.
     *
     * <p>Falls back to parse-only mode without HTTP replay when no
     * {@link HttpClientFactory} is available.
     *
     * @param node      the AccessLogSampler node; must not be {@code null}
     * @param result    the sample result to populate; must not be {@code null}
     * @param variables current VU variable scope
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String logFile = resolve(node.getStringProp("AccessLogSampler.log_file", ""), variables);
        String domain = resolve(node.getStringProp("AccessLogSampler.domain", ""), variables);
        int port = node.getIntProp("AccessLogSampler.port", 80);
        String protocol = resolve(node.getStringProp("AccessLogSampler.protocol", "http"), variables);
        String filterClass = resolve(node.getStringProp("AccessLogSampler.filter_class", ""), variables);

        if (logFile.isBlank()) {
            result.setFailureMessage("AccessLogSampler: log_file is empty");
            return;
        }

        Path path = Path.of(logFile);
        if (!Files.exists(path)) {
            result.setStatusCode(404);
            result.setResponseBody(
                    "AccessLogSampler: log file not found: " + logFile + "\n"
                    + "Domain: " + domain + "\n"
                    + "Port: " + port + "\n"
                    + "Protocol: " + protocol + "\n"
                    + "Filter: " + filterClass);
            result.setFailureMessage("AccessLogSampler: log file not found: " + logFile);
            return;
        }

        LOG.log(Level.INFO,
                "AccessLogSamplerExecutor: parsing access log {0} (parse-only, no httpClientFactory)",
                logFile);

        long start = System.currentTimeMillis();
        List<String[]> entries = parseLogFile(path);
        long elapsed = System.currentTimeMillis() - start;

        result.setTotalTimeMs(elapsed);

        if (domain.isBlank()) {
            // No domain = can't replay, report parsed entries
            StringBuilder sb = new StringBuilder();
            int displayed = 0;
            for (String[] e : entries) {
                if (displayed < 20) {
                    sb.append(e[0]).append(' ').append(e[1]).append('\n');
                    displayed++;
                }
            }

            result.setStatusCode(200);
            result.setResponseBody(
                    "AccessLogSampler: parsed " + entries.size() + " requests from log file\n"
                    + "Target: " + protocol + "://" + domain + ":" + port + "\n"
                    + "Filter: " + (filterClass.isBlank() ? "(none)" : filterClass) + "\n"
                    + "Parsed URLs:\n" + sb
                    + (entries.size() > 20 ? "... and " + (entries.size() - 20) + " more\n" : "")
                    + "\nNote: No httpClientFactory provided. Requests were parsed but not replayed.");
        } else {
            // Domain present but no httpClientFactory — report what would be replayed
            StringBuilder sb = new StringBuilder();
            int displayed = 0;
            for (String[] e : entries) {
                if (displayed < 20) {
                    sb.append(e[0]).append(' ').append(protocol).append("://")
                      .append(domain).append(':').append(port).append(e[1]).append('\n');
                    displayed++;
                }
            }

            result.setStatusCode(200);
            result.setResponseBody(
                    "AccessLogSampler: parsed " + entries.size() + " requests\n"
                    + "Target: " + protocol + "://" + domain + ":" + port + "\n"
                    + "Parsed URLs (would replay with httpClientFactory):\n" + sb
                    + (entries.size() > 20 ? "... and " + (entries.size() - 20) + " more\n" : ""));
        }
    }

    /**
     * Parses an Apache access log file and returns method+path pairs.
     *
     * @param logPath path to the log file
     * @return list of [method, path] arrays
     */
    static List<String[]> parseLogFile(Path logPath) {
        List<String[]> entries = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(logPath)) {
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null && lineCount < MAX_LINES) {
                lineCount++;
                String[] parsed = parseLogLine(line);
                if (parsed != null) {
                    entries.add(parsed);
                }
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "AccessLogSamplerExecutor: error reading " + logPath, e);
        }
        return entries;
    }

    /**
     * Extracts method and path from an Apache Combined Log Format line.
     * Visible for testing.
     *
     * @param line a log line
     * @return array of [method, path] or null if no match
     */
    static String[] parseLogLine(String line) {
        Matcher m = ACCESS_LOG_PATTERN.matcher(line);
        if (m.find()) {
            return new String[]{m.group(1), m.group(2)};
        }
        return null;
    }

    private static String resolve(String value, Map<String, String> variables) {
        if (value == null) return "";
        return VariableResolver.resolve(value, variables);
    }
}
