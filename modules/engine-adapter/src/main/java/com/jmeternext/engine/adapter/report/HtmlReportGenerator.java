package com.jmeternext.engine.adapter.report;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Generates an HTML report from a JTL CSV file.
 *
 * <p>Parses the JTL CSV produced by {@code SimpleDataWriterExecutor}, aggregates
 * metrics per label, and writes:
 * <ul>
 *   <li>{@code index.html} — main dashboard with summary stats, per-label table,
 *       and an SVG response time chart</li>
 *   <li>{@code statistics.json} — aggregated metrics in JSON format</li>
 * </ul>
 *
 * <p>Compatible with the JMeter 5.x JTL CSV format:
 * <pre>
 * timeStamp,elapsed,label,responseCode,responseMessage,threadName,
 * dataType,success,failureMessage,bytes,sentBytes,grpThreads,allThreads,
 * URL,Latency,IdleTime,Connect
 * </pre>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class HtmlReportGenerator {

    private static final Logger LOG = Logger.getLogger(HtmlReportGenerator.class.getName());

    private HtmlReportGenerator() {}

    /**
     * Generates an HTML report from the given JTL file into the output directory.
     *
     * @param jtlFile   path to the JTL CSV file; must exist
     * @param outputDir path to the output directory; will be created if absent
     * @throws IOException if reading or writing fails
     * @throws IllegalArgumentException if the JTL file does not exist or is empty
     */
    public static void generate(Path jtlFile, Path outputDir) throws IOException {
        Objects.requireNonNull(jtlFile, "jtlFile must not be null");
        Objects.requireNonNull(outputDir, "outputDir must not be null");

        if (!Files.exists(jtlFile)) {
            throw new IllegalArgumentException("JTL file does not exist: " + jtlFile);
        }

        Files.createDirectories(outputDir);

        List<JtlRow> rows = parseJtl(jtlFile);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("JTL file contains no sample data: " + jtlFile);
        }

        Map<String, LabelStats> statsMap = aggregate(rows);
        LabelStats totalStats = computeTotal(rows);

        writeStatisticsJson(outputDir, statsMap, totalStats);
        writeIndexHtml(outputDir, statsMap, totalStats);

        LOG.log(Level.INFO,
                "HtmlReportGenerator: report generated in [{0}] ({1} samples, {2} labels)",
                new Object[]{outputDir, rows.size(), statsMap.size()});
    }

    // =========================================================================
    // JTL Parsing
    // =========================================================================

    /**
     * Parses a JTL CSV file into a list of rows.
     */
    static List<JtlRow> parseJtl(Path jtlFile) throws IOException {
        List<JtlRow> rows = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(jtlFile, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) return rows;

            // Determine column indices from header
            String[] headers = headerLine.split(",", -1);
            int tsIdx = indexOf(headers, "timeStamp");
            int elIdx = indexOf(headers, "elapsed");
            int lblIdx = indexOf(headers, "label");
            int rcIdx = indexOf(headers, "responseCode");
            int successIdx = indexOf(headers, "success");
            int latIdx = indexOf(headers, "Latency");
            int connIdx = indexOf(headers, "Connect");
            int bytesIdx = indexOf(headers, "bytes");

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = parseCsvLine(line);
                if (parts.length < 3) continue;

                try {
                    JtlRow row = new JtlRow();
                    row.timeStamp = safeParseLong(safeGet(parts, tsIdx), 0L);
                    row.elapsed = safeParseLong(safeGet(parts, elIdx), 0L);
                    row.label = safeGet(parts, lblIdx);
                    row.responseCode = safeGet(parts, rcIdx);
                    row.success = "true".equalsIgnoreCase(safeGet(parts, successIdx));
                    row.latency = safeParseLong(safeGet(parts, latIdx), 0L);
                    row.connect = safeParseLong(safeGet(parts, connIdx), 0L);
                    row.bytes = safeParseLong(safeGet(parts, bytesIdx), 0L);
                    rows.add(row);
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Skipping malformed JTL line: {0}", line);
                }
            }
        }

        return rows;
    }

    private static int indexOf(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    private static String safeGet(String[] parts, int idx) {
        if (idx < 0 || idx >= parts.length) return "";
        return parts[idx].trim();
    }

    private static long safeParseLong(String val, long def) {
        if (val == null || val.isBlank()) return def;
        try { return Long.parseLong(val); } catch (NumberFormatException e) { return def; }
    }

    /**
     * Simple CSV line parser that handles quoted fields with commas.
     */
    static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    // =========================================================================
    // Aggregation
    // =========================================================================

    static Map<String, LabelStats> aggregate(List<JtlRow> rows) {
        Map<String, List<JtlRow>> grouped = new LinkedHashMap<>();
        for (JtlRow row : rows) {
            grouped.computeIfAbsent(row.label, k -> new ArrayList<>()).add(row);
        }

        Map<String, LabelStats> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<JtlRow>> entry : grouped.entrySet()) {
            result.put(entry.getKey(), computeStats(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    static LabelStats computeTotal(List<JtlRow> rows) {
        return computeStats("TOTAL", rows);
    }

    private static LabelStats computeStats(String label, List<JtlRow> rows) {
        LabelStats stats = new LabelStats();
        stats.label = label;
        stats.samples = rows.size();

        long[] elapsed = rows.stream().mapToLong(r -> r.elapsed).toArray();
        Arrays.sort(elapsed);

        stats.errorCount = rows.stream().filter(r -> !r.success).count();
        stats.errorPct = stats.samples > 0 ? (stats.errorCount * 100.0 / stats.samples) : 0.0;
        stats.avg = Arrays.stream(elapsed).average().orElse(0.0);
        stats.min = elapsed.length > 0 ? elapsed[0] : 0;
        stats.max = elapsed.length > 0 ? elapsed[elapsed.length - 1] : 0;
        stats.median = percentile(elapsed, 50);
        stats.p90 = percentile(elapsed, 90);
        stats.p95 = percentile(elapsed, 95);
        stats.p99 = percentile(elapsed, 99);

        // Throughput: samples / second
        if (rows.size() >= 2) {
            long minTs = rows.stream().mapToLong(r -> r.timeStamp).min().orElse(0);
            long maxTs = rows.stream().mapToLong(r -> r.timeStamp + r.elapsed).max().orElse(0);
            long durationMs = maxTs - minTs;
            stats.throughput = durationMs > 0 ? (rows.size() * 1000.0 / durationMs) : 0.0;
        } else {
            stats.throughput = 0.0;
        }

        return stats;
    }

    private static long percentile(long[] sorted, int pct) {
        if (sorted.length == 0) return 0;
        int idx = (int) Math.ceil(pct / 100.0 * sorted.length) - 1;
        idx = Math.max(0, Math.min(idx, sorted.length - 1));
        return sorted[idx];
    }

    // =========================================================================
    // statistics.json
    // =========================================================================

    private static void writeStatisticsJson(Path outputDir, Map<String, LabelStats> statsMap,
                                             LabelStats totalStats) throws IOException {
        Path jsonFile = outputDir.resolve("statistics.json");
        try (BufferedWriter writer = Files.newBufferedWriter(jsonFile, StandardCharsets.UTF_8)) {
            writer.write("{\n");

            List<Map.Entry<String, LabelStats>> entries = new ArrayList<>(statsMap.entrySet());
            for (int i = 0; i < entries.size(); i++) {
                Map.Entry<String, LabelStats> entry = entries.get(i);
                writeLabelStatsJson(writer, entry.getKey(), entry.getValue());
                writer.write(",\n");
            }

            writeLabelStatsJson(writer, "Total", totalStats);
            writer.write("\n}\n");
        }
    }

    private static void writeLabelStatsJson(BufferedWriter writer, String key, LabelStats stats)
            throws IOException {
        writer.write("  \"" + escapeJson(key) + "\": {\n");
        writer.write("    \"transaction\": \"" + escapeJson(stats.label) + "\",\n");
        writer.write("    \"sampleCount\": " + stats.samples + ",\n");
        writer.write("    \"errorCount\": " + stats.errorCount + ",\n");
        writer.write("    \"errorPct\": " + String.format("%.2f", stats.errorPct) + ",\n");
        writer.write("    \"meanResTime\": " + String.format("%.2f", stats.avg) + ",\n");
        writer.write("    \"medianResTime\": " + stats.median + ",\n");
        writer.write("    \"minResTime\": " + stats.min + ",\n");
        writer.write("    \"maxResTime\": " + stats.max + ",\n");
        writer.write("    \"pct1ResTime\": " + stats.p90 + ",\n");
        writer.write("    \"pct2ResTime\": " + stats.p95 + ",\n");
        writer.write("    \"pct3ResTime\": " + stats.p99 + ",\n");
        writer.write("    \"throughput\": " + String.format("%.2f", stats.throughput) + "\n");
        writer.write("  }");
    }

    // =========================================================================
    // index.html
    // =========================================================================

    private static void writeIndexHtml(Path outputDir, Map<String, LabelStats> statsMap,
                                        LabelStats total) throws IOException {
        Path htmlFile = outputDir.resolve("index.html");
        try (BufferedWriter w = Files.newBufferedWriter(htmlFile, StandardCharsets.UTF_8)) {
            w.write("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
            w.write("<meta charset=\"UTF-8\">\n");
            w.write("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
            w.write("<title>jmeter-next Test Report</title>\n");
            writeEmbeddedCss(w);
            w.write("</head>\n<body>\n");

            // Header
            w.write("<header><h1>jmeter-next Test Report</h1></header>\n");

            // Summary cards
            w.write("<section class=\"summary\">\n");
            writeCard(w, "Total Samples", String.valueOf(total.samples));
            writeCard(w, "Avg Response Time", String.format("%.0f ms", total.avg));
            writeCard(w, "Error Rate", String.format("%.2f%%", total.errorPct));
            writeCard(w, "Throughput", String.format("%.2f req/s", total.throughput));
            writeCard(w, "Min / Max", total.min + " / " + total.max + " ms");
            writeCard(w, "P95 / P99", total.p95 + " / " + total.p99 + " ms");
            w.write("</section>\n");

            // Statistics table
            w.write("<section class=\"stats-table\">\n");
            w.write("<h2>Per-Label Statistics</h2>\n");
            w.write("<table>\n<thead><tr>");
            for (String hdr : new String[]{
                    "Label", "Samples", "Avg (ms)", "Min (ms)", "Max (ms)",
                    "Median (ms)", "P90 (ms)", "P95 (ms)", "P99 (ms)",
                    "Error %", "Throughput (req/s)"}) {
                w.write("<th>" + hdr + "</th>");
            }
            w.write("</tr></thead>\n<tbody>\n");

            for (LabelStats s : statsMap.values()) {
                w.write("<tr>");
                w.write("<td>" + escapeHtml(s.label) + "</td>");
                w.write("<td>" + s.samples + "</td>");
                w.write("<td>" + String.format("%.0f", s.avg) + "</td>");
                w.write("<td>" + s.min + "</td>");
                w.write("<td>" + s.max + "</td>");
                w.write("<td>" + s.median + "</td>");
                w.write("<td>" + s.p90 + "</td>");
                w.write("<td>" + s.p95 + "</td>");
                w.write("<td>" + s.p99 + "</td>");
                w.write("<td class=\"" + (s.errorPct > 0 ? "error" : "") + "\">"
                        + String.format("%.2f%%", s.errorPct) + "</td>");
                w.write("<td>" + String.format("%.2f", s.throughput) + "</td>");
                w.write("</tr>\n");
            }

            // Total row
            w.write("<tr class=\"total\">");
            w.write("<td><strong>TOTAL</strong></td>");
            w.write("<td>" + total.samples + "</td>");
            w.write("<td>" + String.format("%.0f", total.avg) + "</td>");
            w.write("<td>" + total.min + "</td>");
            w.write("<td>" + total.max + "</td>");
            w.write("<td>" + total.median + "</td>");
            w.write("<td>" + total.p90 + "</td>");
            w.write("<td>" + total.p95 + "</td>");
            w.write("<td>" + total.p99 + "</td>");
            w.write("<td class=\"" + (total.errorPct > 0 ? "error" : "") + "\">"
                    + String.format("%.2f%%", total.errorPct) + "</td>");
            w.write("<td>" + String.format("%.2f", total.throughput) + "</td>");
            w.write("</tr>\n");

            w.write("</tbody>\n</table>\n</section>\n");

            // SVG Chart — Response Time by Label
            writeSvgChart(w, statsMap);

            w.write("</body>\n</html>\n");
        }
    }

    private static void writeCard(BufferedWriter w, String title, String value) throws IOException {
        w.write("<div class=\"card\"><div class=\"card-title\">" + title
                + "</div><div class=\"card-value\">" + value + "</div></div>\n");
    }

    private static void writeSvgChart(BufferedWriter w, Map<String, LabelStats> statsMap)
            throws IOException {
        if (statsMap.isEmpty()) return;

        w.write("<section class=\"chart\">\n<h2>Response Time by Label</h2>\n");

        List<LabelStats> labels = new ArrayList<>(statsMap.values());
        int barWidth = 60;
        int gap = 20;
        int chartWidth = labels.size() * (barWidth + gap) + gap + 40;
        int chartHeight = 300;
        int topMargin = 20;
        int bottomMargin = 80;
        int leftMargin = 60;

        double maxVal = labels.stream()
                .mapToDouble(s -> Math.max(s.avg, Math.max(s.p95, s.p99)))
                .max().orElse(100);
        if (maxVal == 0) maxVal = 100;
        double scale = (chartHeight - topMargin - bottomMargin) / maxVal;

        w.write("<svg width=\"" + (chartWidth + leftMargin) + "\" height=\"" + chartHeight
                + "\" xmlns=\"http://www.w3.org/2000/svg\">\n");

        // Y-axis
        w.write("<line x1=\"" + leftMargin + "\" y1=\"" + topMargin + "\" x2=\"" + leftMargin
                + "\" y2=\"" + (chartHeight - bottomMargin)
                + "\" stroke=\"#666\" stroke-width=\"1\"/>\n");

        // Y-axis labels
        int ySteps = 5;
        for (int i = 0; i <= ySteps; i++) {
            double val = maxVal * i / ySteps;
            int y = chartHeight - bottomMargin - (int)(val * scale);
            w.write("<text x=\"" + (leftMargin - 5) + "\" y=\"" + (y + 4)
                    + "\" text-anchor=\"end\" font-size=\"10\" fill=\"#666\">"
                    + String.format("%.0f", val) + "</text>\n");
            w.write("<line x1=\"" + leftMargin + "\" y1=\"" + y + "\" x2=\""
                    + (chartWidth + leftMargin) + "\" y2=\"" + y
                    + "\" stroke=\"#eee\" stroke-width=\"1\"/>\n");
        }

        // Bars
        for (int i = 0; i < labels.size(); i++) {
            LabelStats s = labels.get(i);
            int x = leftMargin + gap + i * (barWidth + gap);
            int barBase = chartHeight - bottomMargin;

            // P99 bar (light)
            int p99H = (int)(s.p99 * scale);
            w.write("<rect x=\"" + x + "\" y=\"" + (barBase - p99H) + "\" width=\"" + barWidth
                    + "\" height=\"" + p99H + "\" fill=\"#e8d5e8\" rx=\"2\"/>\n");

            // P95 bar (medium)
            int p95H = (int)(s.p95 * scale);
            w.write("<rect x=\"" + (x + 5) + "\" y=\"" + (barBase - p95H) + "\" width=\""
                    + (barWidth - 10) + "\" height=\"" + p95H + "\" fill=\"#b8a5d4\" rx=\"2\"/>\n");

            // Avg bar (dark)
            int avgH = (int)(s.avg * scale);
            w.write("<rect x=\"" + (x + 10) + "\" y=\"" + (barBase - avgH) + "\" width=\""
                    + (barWidth - 20) + "\" height=\"" + avgH + "\" fill=\"#5b4a96\" rx=\"2\"/>\n");

            // Label text (rotated)
            String shortLabel = s.label.length() > 15 ? s.label.substring(0, 12) + "..." : s.label;
            w.write("<text x=\"" + (x + barWidth / 2) + "\" y=\"" + (barBase + 15)
                    + "\" text-anchor=\"end\" font-size=\"10\" fill=\"#333\" "
                    + "transform=\"rotate(-45 " + (x + barWidth / 2) + " " + (barBase + 15) + ")\">"
                    + escapeHtml(shortLabel) + "</text>\n");
        }

        // Legend
        int ly = chartHeight - 15;
        w.write("<rect x=\"" + (leftMargin + 10) + "\" y=\"" + ly + "\" width=\"12\" height=\"12\" fill=\"#5b4a96\"/>\n");
        w.write("<text x=\"" + (leftMargin + 25) + "\" y=\"" + (ly + 10) + "\" font-size=\"10\">Avg</text>\n");
        w.write("<rect x=\"" + (leftMargin + 60) + "\" y=\"" + ly + "\" width=\"12\" height=\"12\" fill=\"#b8a5d4\"/>\n");
        w.write("<text x=\"" + (leftMargin + 75) + "\" y=\"" + (ly + 10) + "\" font-size=\"10\">P95</text>\n");
        w.write("<rect x=\"" + (leftMargin + 110) + "\" y=\"" + ly + "\" width=\"12\" height=\"12\" fill=\"#e8d5e8\"/>\n");
        w.write("<text x=\"" + (leftMargin + 125) + "\" y=\"" + (ly + 10) + "\" font-size=\"10\">P99</text>\n");

        w.write("</svg>\n</section>\n");
    }

    // =========================================================================
    // Embedded CSS
    // =========================================================================

    private static void writeEmbeddedCss(BufferedWriter w) throws IOException {
        w.write("<style>\n");
        w.write("* { margin: 0; padding: 0; box-sizing: border-box; }\n");
        w.write("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; ");
        w.write("background: #f5f5f5; color: #333; padding: 20px; }\n");
        w.write("header { background: #2c2c54; color: white; padding: 20px 30px; ");
        w.write("border-radius: 8px; margin-bottom: 20px; }\n");
        w.write("header h1 { font-size: 24px; font-weight: 600; }\n");
        w.write(".summary { display: flex; gap: 15px; flex-wrap: wrap; margin-bottom: 20px; }\n");
        w.write(".card { background: white; border-radius: 8px; padding: 15px 20px; ");
        w.write("flex: 1; min-width: 150px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }\n");
        w.write(".card-title { font-size: 12px; color: #888; text-transform: uppercase; ");
        w.write("letter-spacing: 0.5px; margin-bottom: 4px; }\n");
        w.write(".card-value { font-size: 22px; font-weight: 600; color: #2c2c54; }\n");
        w.write(".stats-table { background: white; border-radius: 8px; padding: 20px; ");
        w.write("box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 20px; overflow-x: auto; }\n");
        w.write(".stats-table h2 { margin-bottom: 15px; font-size: 18px; color: #2c2c54; }\n");
        w.write("table { width: 100%; border-collapse: collapse; font-size: 13px; }\n");
        w.write("th { background: #2c2c54; color: white; padding: 10px 12px; text-align: left; ");
        w.write("font-weight: 500; }\n");
        w.write("td { padding: 8px 12px; border-bottom: 1px solid #eee; }\n");
        w.write("tr:hover { background: #f9f9ff; }\n");
        w.write("tr.total { background: #f0eef5; font-weight: 600; }\n");
        w.write("td.error { color: #d63031; font-weight: 600; }\n");
        w.write(".chart { background: white; border-radius: 8px; padding: 20px; ");
        w.write("box-shadow: 0 1px 3px rgba(0,0,0,0.1); }\n");
        w.write(".chart h2 { margin-bottom: 15px; font-size: 18px; color: #2c2c54; }\n");
        w.write("</style>\n");
    }

    // =========================================================================
    // Escape helpers
    // =========================================================================

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    // =========================================================================
    // Data classes (package-visible for testing)
    // =========================================================================

    static final class JtlRow {
        long timeStamp;
        long elapsed;
        String label = "";
        String responseCode = "";
        boolean success;
        long latency;
        long connect;
        long bytes;
    }

    static final class LabelStats {
        String label = "";
        int samples;
        long errorCount;
        double errorPct;
        double avg;
        long min;
        long max;
        long median;
        long p90;
        long p95;
        long p99;
        double throughput;
    }
}
