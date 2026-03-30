package com.jmeternext.web.api.service;

import com.jmeternext.web.api.repository.SampleBucketRow;
import com.jmeternext.web.api.repository.SampleResultRepository;
import com.jmeternext.web.api.repository.TestRunEntity;
import com.jmeternext.web.api.repository.TestRunRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generates HTML dashboard reports from persisted sample result data.
 *
 * <p>Reports are written to {@code data/reports/{runId}/index.html} relative to
 * the process working directory. Each report contains:
 * <ul>
 *   <li>Summary statistics: total samples, error count, error rate, avg/min/max
 *       response time, throughput</li>
 *   <li>Response time chart data serialised as inline JSON for client-side rendering</li>
 *   <li>Throughput chart data serialised as inline JSON</li>
 *   <li>An error breakdown table</li>
 * </ul>
 *
 * <p>Generation is synchronous inside this service; callers that need async
 * behaviour should submit to an executor (see {@link ReportStatus}).
 */
@Service
public class ReportService {

    private static final Logger LOG = Logger.getLogger(ReportService.class.getName());

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    /** Base directory for all generated reports (relative to working dir). */
    static final String REPORTS_BASE = "data/reports";

    /**
     * In-memory report status map.
     * Keys are runIds; values are the current {@link ReportStatus}.
     */
    private final ConcurrentHashMap<String, ReportStatus> statusMap = new ConcurrentHashMap<>();

    private final TestRunRepository runRepository;
    private final SampleResultRepository sampleRepository;

    public ReportService(TestRunRepository runRepository,
                         SampleResultRepository sampleRepository) {
        this.runRepository = runRepository;
        this.sampleRepository = sampleRepository;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Generates an HTML report for the specified run and writes it to
     * {@code data/reports/{runId}/index.html}.
     *
     * <p>All sample results stored for the run are aggregated to compute
     * summary statistics and chart data.
     *
     * @param runId the run identifier; must not be null
     * @return the absolute path of the report directory
     * @throws IllegalArgumentException if the run does not exist
     * @throws ReportGenerationException if writing the report file fails
     */
    public Path generateReport(String runId) {
        Optional<TestRunEntity> runOpt = runRepository.findById(runId);
        if (runOpt.isEmpty()) {
            throw new IllegalArgumentException("Run not found: " + runId);
        }

        statusMap.put(runId, ReportStatus.GENERATING);

        try {
            TestRunEntity run = runOpt.get();
            List<SampleBucketRow> rows = sampleRepository.findByRunId(
                    runId, Instant.EPOCH, Instant.now().plusSeconds(86400));

            String html = buildHtml(run, rows);

            Path reportDir = Path.of(REPORTS_BASE, runId);
            Files.createDirectories(reportDir);
            Path reportFile = reportDir.resolve("index.html");
            Files.writeString(reportFile, html, StandardCharsets.UTF_8);

            statusMap.put(runId, ReportStatus.READY);
            LOG.info("Report generated for run " + runId + " at " + reportFile.toAbsolutePath());
            return reportDir.toAbsolutePath();

        } catch (IOException e) {
            statusMap.put(runId, ReportStatus.FAILED);
            LOG.log(Level.SEVERE, "Failed to write report for run " + runId, e);
            throw new ReportGenerationException("Failed to generate report for run " + runId, e);
        }
    }

    /**
     * Returns the current generation status for a run's report.
     *
     * @param runId the run identifier; must not be null
     * @return the report status, or {@link ReportStatus#NOT_FOUND} if no report
     *         has been requested for this run
     */
    public ReportStatus getStatus(String runId) {
        Path reportFile = Path.of(REPORTS_BASE, runId, "index.html");
        if (Files.exists(reportFile)) {
            return ReportStatus.READY;
        }
        return statusMap.getOrDefault(runId, ReportStatus.NOT_FOUND);
    }

    /**
     * Returns the path of the generated report directory, if available.
     *
     * @param runId the run identifier; must not be null
     * @return the directory path, or empty if the report does not exist
     */
    public Optional<Path> getReportPath(String runId) {
        Path reportDir = Path.of(REPORTS_BASE, runId);
        Path reportFile = reportDir.resolve("index.html");
        return Files.exists(reportFile) ? Optional.of(reportDir.toAbsolutePath()) : Optional.empty();
    }

    // -------------------------------------------------------------------------
    // Report status enum
    // -------------------------------------------------------------------------

    /**
     * Lifecycle states for a report generation request.
     */
    public enum ReportStatus {
        /** No report has been requested for this run. */
        NOT_FOUND,
        /** Report generation is in progress. */
        GENERATING,
        /** Report is available. */
        READY,
        /** Report generation failed. */
        FAILED
    }

    // -------------------------------------------------------------------------
    // HTML builder
    // -------------------------------------------------------------------------

    private String buildHtml(TestRunEntity run, List<SampleBucketRow> rows) {
        SummaryStats stats = computeSummary(rows);
        String chartJson = buildResponseTimeChartJson(rows);
        String throughputJson = buildThroughputChartJson(rows);
        String errorTableHtml = buildErrorTableHtml(rows);

        String startedAt = run.startedAt() != null ? ISO_FORMATTER.format(run.startedAt()) : "—";
        String endedAt   = run.endedAt()   != null ? ISO_FORMATTER.format(run.endedAt())   : "—";

        return "<!DOCTYPE html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\">\n"
                + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
                + "  <title>JMeter-Next Report — " + escapeHtml(run.id()) + "</title>\n"
                + "  <style>\n"
                + "    body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }\n"
                + "    h1 { color: #333; }\n"
                + "    h2 { color: #555; margin-top: 30px; }\n"
                + "    .summary-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }\n"
                + "    .card { background: #fff; border-radius: 6px; padding: 16px; box-shadow: 0 1px 3px rgba(0,0,0,.15); }\n"
                + "    .card .label { font-size: 12px; color: #888; text-transform: uppercase; }\n"
                + "    .card .value { font-size: 28px; font-weight: bold; color: #222; margin-top: 4px; }\n"
                + "    table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 6px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,.15); }\n"
                + "    th { background: #3d5a80; color: #fff; padding: 10px 12px; text-align: left; }\n"
                + "    td { padding: 8px 12px; border-bottom: 1px solid #eee; }\n"
                + "    tr:last-child td { border-bottom: none; }\n"
                + "    .meta { color: #666; font-size: 14px; margin-bottom: 20px; }\n"
                + "    pre { display: none; }\n"
                + "  </style>\n"
                + "</head>\n"
                + "<body>\n"
                + "  <h1>Performance Report</h1>\n"
                + "  <p class=\"meta\">"
                + "Run ID: <strong>" + escapeHtml(run.id()) + "</strong> &nbsp;|&nbsp; "
                + "Status: <strong>" + escapeHtml(run.status()) + "</strong> &nbsp;|&nbsp; "
                + "Started: <strong>" + startedAt + "</strong> &nbsp;|&nbsp; "
                + "Ended: <strong>" + endedAt + "</strong>"
                + "  </p>\n"
                + "\n"
                + "  <h2>Summary Statistics</h2>\n"
                + "  <div class=\"summary-grid\">\n"
                + "    <div class=\"card\"><div class=\"label\">Total Samples</div><div class=\"value\">" + stats.totalSamples + "</div></div>\n"
                + "    <div class=\"card\"><div class=\"label\">Error Count</div><div class=\"value\">" + stats.totalErrors + "</div></div>\n"
                + "    <div class=\"card\"><div class=\"label\">Error Rate</div><div class=\"value\">" + String.format("%.2f%%", stats.errorRate) + "</div></div>\n"
                + "    <div class=\"card\"><div class=\"label\">Avg Response (ms)</div><div class=\"value\">" + String.format("%.1f", stats.avgResponseTime) + "</div></div>\n"
                + "    <div class=\"card\"><div class=\"label\">Min Response (ms)</div><div class=\"value\">" + String.format("%.1f", stats.minResponseTime) + "</div></div>\n"
                + "    <div class=\"card\"><div class=\"label\">Max Response (ms)</div><div class=\"value\">" + String.format("%.1f", stats.maxResponseTime) + "</div></div>\n"
                + "    <div class=\"card\"><div class=\"label\">90th Pct (ms)</div><div class=\"value\">" + String.format("%.1f", stats.percentile90) + "</div></div>\n"
                + "    <div class=\"card\"><div class=\"label\">Throughput (req/s)</div><div class=\"value\">" + String.format("%.2f", stats.avgThroughput) + "</div></div>\n"
                + "  </div>\n"
                + "\n"
                + "  <h2>Response Time Over Time</h2>\n"
                + "  <pre id=\"responseTimeData\">" + chartJson + "</pre>\n"
                + "\n"
                + "  <h2>Throughput Over Time</h2>\n"
                + "  <pre id=\"throughputData\">" + throughputJson + "</pre>\n"
                + "\n"
                + "  <h2>Error Breakdown</h2>\n"
                + errorTableHtml
                + "\n"
                + "</body>\n"
                + "</html>\n";
    }

    // -------------------------------------------------------------------------
    // Summary computation
    // -------------------------------------------------------------------------

    private SummaryStats computeSummary(List<SampleBucketRow> rows) {
        if (rows.isEmpty()) {
            return new SummaryStats(0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }

        long totalSamples = rows.stream().mapToLong(SampleBucketRow::sampleCount).sum();
        long totalErrors  = rows.stream().mapToLong(SampleBucketRow::errorCount).sum();
        double errorRate  = totalSamples > 0 ? (totalErrors * 100.0 / totalSamples) : 0.0;

        // Weighted average response time
        long weightedSum = 0;
        for (SampleBucketRow row : rows) {
            weightedSum += (long) (row.avgResponseTime() * row.sampleCount());
        }
        double avgResponseTime = totalSamples > 0 ? (double) weightedSum / totalSamples : 0.0;

        double minResponseTime = rows.stream()
                .mapToDouble(SampleBucketRow::minResponseTime)
                .min().orElse(0.0);
        double maxResponseTime = rows.stream()
                .mapToDouble(SampleBucketRow::maxResponseTime)
                .max().orElse(0.0);

        // Use the maximum 90th-percentile across buckets as the overall p90 approximation
        double percentile90 = rows.stream()
                .mapToDouble(SampleBucketRow::percentile90)
                .max().orElse(0.0);

        double avgThroughput = rows.stream()
                .mapToDouble(SampleBucketRow::samplesPerSecond)
                .average().orElse(0.0);

        return new SummaryStats(totalSamples, totalErrors, errorRate,
                avgResponseTime, minResponseTime, maxResponseTime,
                percentile90, avgThroughput);
    }

    private record SummaryStats(
            long totalSamples,
            long totalErrors,
            double errorRate,
            double avgResponseTime,
            double minResponseTime,
            double maxResponseTime,
            double percentile90,
            double avgThroughput
    ) {}

    // -------------------------------------------------------------------------
    // Chart data serialisation
    // -------------------------------------------------------------------------

    private String buildResponseTimeChartJson(List<SampleBucketRow> rows) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < rows.size(); i++) {
            SampleBucketRow row = rows.get(i);
            sb.append("  {\"timestamp\":\"")
              .append(ISO_FORMATTER.format(row.timestamp()))
              .append("\",\"label\":\"").append(escapeJson(row.samplerLabel())).append("\"")
              .append(",\"avg\":").append(String.format("%.2f", row.avgResponseTime()))
              .append(",\"min\":").append(String.format("%.2f", row.minResponseTime()))
              .append(",\"max\":").append(String.format("%.2f", row.maxResponseTime()))
              .append(",\"p90\":").append(String.format("%.2f", row.percentile90()))
              .append(",\"p95\":").append(String.format("%.2f", row.percentile95()))
              .append(",\"p99\":").append(String.format("%.2f", row.percentile99()))
              .append("}");
            if (i < rows.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    private String buildThroughputChartJson(List<SampleBucketRow> rows) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < rows.size(); i++) {
            SampleBucketRow row = rows.get(i);
            sb.append("  {\"timestamp\":\"")
              .append(ISO_FORMATTER.format(row.timestamp()))
              .append("\",\"label\":\"").append(escapeJson(row.samplerLabel())).append("\"")
              .append(",\"samplesPerSecond\":").append(String.format("%.2f", row.samplesPerSecond()))
              .append(",\"sampleCount\":").append(row.sampleCount())
              .append(",\"errorCount\":").append(row.errorCount())
              .append("}");
            if (i < rows.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Error table
    // -------------------------------------------------------------------------

    private String buildErrorTableHtml(List<SampleBucketRow> rows) {
        List<SampleBucketRow> errorRows = rows.stream()
                .filter(r -> r.errorCount() > 0)
                .toList();

        if (errorRows.isEmpty()) {
            return "<p>No errors recorded.</p>\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("  <table>\n")
          .append("    <thead><tr>")
          .append("<th>Timestamp</th>")
          .append("<th>Sampler</th>")
          .append("<th>Samples</th>")
          .append("<th>Errors</th>")
          .append("<th>Error %</th>")
          .append("</tr></thead>\n")
          .append("    <tbody>\n");

        for (SampleBucketRow row : errorRows) {
            double errPct = row.sampleCount() > 0
                    ? (row.errorCount() * 100.0 / row.sampleCount())
                    : 0.0;
            sb.append("      <tr>")
              .append("<td>").append(ISO_FORMATTER.format(row.timestamp())).append("</td>")
              .append("<td>").append(escapeHtml(row.samplerLabel())).append("</td>")
              .append("<td>").append(row.sampleCount()).append("</td>")
              .append("<td>").append(row.errorCount()).append("</td>")
              .append("<td>").append(String.format("%.2f%%", errPct)).append("</td>")
              .append("</tr>\n");
        }

        sb.append("    </tbody>\n  </table>\n");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Utility helpers
    // -------------------------------------------------------------------------

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // -------------------------------------------------------------------------
    // Exception types
    // -------------------------------------------------------------------------

    /**
     * Thrown when the HTML report cannot be written to disk.
     */
    public static final class ReportGenerationException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ReportGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
