/*
 * Copyright 2024-2026 b3meter Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.b3meter.engine.service.output;

import com.b3meter.engine.service.SampleBucket;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Writes a JUnit XML report from aggregated test-run results.
 *
 * <p>The output format is compatible with Jenkins, GitHub Actions, Azure DevOps,
 * and other CI systems that consume JUnit XML. Each sampler label becomes a
 * {@code <testcase>} element. A testcase is marked as a {@code <failure>} when
 * the error rate exceeds zero or any SLA threshold is breached.
 *
 * <p>Uses {@link XMLStreamWriter} (JDK built-in StAX API) — no external
 * dependencies required.
 *
 * <h3>Example output</h3>
 * <pre>{@code
 * <?xml version="1.0" encoding="UTF-8"?>
 * <testsuites tests="5" failures="1" errors="0" time="60.0">
 *   <testsuite name="b3meter" tests="5" failures="1" time="60.0">
 *     <testcase name="HTTP GET /api/users" time="0.042" classname="jmeter.http">
 *     </testcase>
 *     <testcase name="HTTP POST /api/login" time="0.150" classname="jmeter.http">
 *       <failure message="Error rate 5.2% exceeds threshold 1.0%">
 *         SLA violation: p95=250ms (threshold: 200ms), error_rate=5.2% (threshold: 1.0%)
 *       </failure>
 *     </testcase>
 *   </testsuite>
 * </testsuites>
 * }</pre>
 *
 * @see SampleBucket
 * @see SlaThreshold
 */
public final class JUnitXmlReportWriter {

    private static final Logger LOG = Logger.getLogger(JUnitXmlReportWriter.class.getName());

    /**
     * SLA threshold definition for a sampler label.
     *
     * @param maxErrorPercent maximum allowed error rate (0.0–100.0)
     * @param maxP95Ms        maximum allowed 95th-percentile response time in ms
     * @param maxP99Ms        maximum allowed 99th-percentile response time in ms
     * @param maxAvgMs        maximum allowed average response time in ms
     */
    public record SlaThreshold(double maxErrorPercent, long maxP95Ms, long maxP99Ms, long maxAvgMs) {

        public SlaThreshold {
            if (maxErrorPercent < 0) {
                throw new IllegalArgumentException("maxErrorPercent must be >= 0, got: " + maxErrorPercent);
            }
            if (maxP95Ms < 0) {
                throw new IllegalArgumentException("maxP95Ms must be >= 0, got: " + maxP95Ms);
            }
            if (maxP99Ms < 0) {
                throw new IllegalArgumentException("maxP99Ms must be >= 0, got: " + maxP99Ms);
            }
            if (maxAvgMs < 0) {
                throw new IllegalArgumentException("maxAvgMs must be >= 0, got: " + maxAvgMs);
            }
        }
    }

    private JUnitXmlReportWriter() {
        // utility class
    }

    /**
     * Writes a JUnit XML report to the given output path.
     *
     * @param outputPath        file path for the XML report; parent directories are created if needed
     * @param suiteName         the {@code name} attribute of the {@code <testsuite>} element
     * @param aggregatedResults list of aggregated {@link SampleBucket} entries (one per sampler label)
     * @param thresholds        SLA thresholds keyed by sampler label; may be empty but not {@code null}
     * @throws IOException if writing to the file fails
     */
    public static void write(Path outputPath,
                             String suiteName,
                             List<SampleBucket> aggregatedResults,
                             Map<String, SlaThreshold> thresholds) throws IOException {

        Objects.requireNonNull(outputPath, "outputPath must not be null");
        Objects.requireNonNull(suiteName, "suiteName must not be null");
        Objects.requireNonNull(aggregatedResults, "aggregatedResults must not be null");
        Objects.requireNonNull(thresholds, "thresholds must not be null");

        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }

        final int totalTests = aggregatedResults.size();
        int failureCount = 0;
        double timeSec = 0.0;

        // Pre-compute failures and total time
        for (SampleBucket bucket : aggregatedResults) {
            if (isFailure(bucket, thresholds.get(bucket.samplerLabel()))) {
                failureCount++;
            }
            timeSec += bucket.avgResponseTime() / 1000.0;
        }
        final int totalFailures = failureCount;
        final double totalTimeSec = timeSec;

        try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(outputPath))) {
            XMLStreamWriter xml = XMLOutputFactory.newInstance().createXMLStreamWriter(os, "UTF-8");

            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeCharacters("\n");

            // <testsuites>
            xml.writeStartElement("testsuites");
            xml.writeAttribute("tests", String.valueOf(totalTests));
            xml.writeAttribute("failures", String.valueOf(totalFailures));
            xml.writeAttribute("errors", "0");
            xml.writeAttribute("time", formatTime(totalTimeSec));
            xml.writeCharacters("\n");

            // <testsuite>
            xml.writeStartElement("testsuite");
            xml.writeAttribute("name", suiteName);
            xml.writeAttribute("tests", String.valueOf(totalTests));
            xml.writeAttribute("failures", String.valueOf(totalFailures));
            xml.writeAttribute("time", formatTime(totalTimeSec));
            xml.writeCharacters("\n");

            for (SampleBucket bucket : aggregatedResults) {
                writeTestCase(xml, bucket, thresholds.get(bucket.samplerLabel()));
            }

            xml.writeEndElement(); // </testsuite>
            xml.writeCharacters("\n");
            xml.writeEndElement(); // </testsuites>
            xml.writeCharacters("\n");

            xml.writeEndDocument();
            xml.flush();
            xml.close();

            LOG.info(() -> "JUnit XML report written to: " + outputPath
                    + " (" + totalTests + " tests, " + totalFailures + " failures)");

        } catch (XMLStreamException ex) {
            throw new IOException("Failed to write JUnit XML report", ex);
        }
    }

    private static void writeTestCase(XMLStreamWriter xml,
                                      SampleBucket bucket,
                                      SlaThreshold threshold) throws XMLStreamException {

        xml.writeCharacters("  ");
        xml.writeStartElement("testcase");
        xml.writeAttribute("name", bucket.samplerLabel());
        xml.writeAttribute("time", formatTime(bucket.avgResponseTime() / 1000.0));
        xml.writeAttribute("classname", "jmeter.http");

        String failureMessage = buildFailureMessage(bucket, threshold);
        if (failureMessage != null) {
            xml.writeCharacters("\n    ");
            xml.writeStartElement("failure");
            xml.writeAttribute("message", failureMessage);
            xml.writeCharacters("\n      " + buildFailureDetail(bucket, threshold) + "\n    ");
            xml.writeEndElement(); // </failure>
            xml.writeCharacters("\n  ");
        } else {
            xml.writeCharacters("\n  ");
        }

        xml.writeEndElement(); // </testcase>
        xml.writeCharacters("\n");
    }

    /**
     * Determines whether a bucket should be marked as a failure.
     */
    private static boolean isFailure(SampleBucket bucket, SlaThreshold threshold) {
        if (bucket.errorCount() > 0) {
            return true;
        }
        if (threshold == null) {
            return false;
        }
        return bucket.errorPercent() > threshold.maxErrorPercent()
                || bucket.percentile95() > threshold.maxP95Ms()
                || bucket.percentile99() > threshold.maxP99Ms()
                || bucket.avgResponseTime() > threshold.maxAvgMs();
    }

    /**
     * Builds a short failure message for the {@code message} attribute.
     *
     * @return the failure message, or {@code null} if the test case passed
     */
    private static String buildFailureMessage(SampleBucket bucket, SlaThreshold threshold) {
        if (bucket.errorCount() > 0 && threshold == null) {
            return String.format(Locale.US, "Error rate %.1f%% (errors: %d/%d)",
                    bucket.errorPercent(), bucket.errorCount(), bucket.sampleCount());
        }
        if (threshold == null) {
            return null;
        }
        if (!isFailure(bucket, threshold)) {
            return null;
        }

        // Pick the most significant violation for the message attribute
        if (bucket.errorPercent() > threshold.maxErrorPercent()) {
            return String.format(Locale.US, "Error rate %.1f%% exceeds threshold %.1f%%",
                    bucket.errorPercent(), threshold.maxErrorPercent());
        }
        if (bucket.percentile95() > threshold.maxP95Ms()) {
            return String.format(Locale.US, "p95 %.0fms exceeds threshold %dms",
                    bucket.percentile95(), threshold.maxP95Ms());
        }
        if (bucket.percentile99() > threshold.maxP99Ms()) {
            return String.format(Locale.US, "p99 %.0fms exceeds threshold %dms",
                    bucket.percentile99(), threshold.maxP99Ms());
        }
        if (bucket.avgResponseTime() > threshold.maxAvgMs()) {
            return String.format(Locale.US, "avg %.0fms exceeds threshold %dms",
                    bucket.avgResponseTime(), threshold.maxAvgMs());
        }
        // Error count > 0 but within threshold — still a failure due to errors
        return String.format(Locale.US, "Error rate %.1f%% (errors: %d/%d)",
                bucket.errorPercent(), bucket.errorCount(), bucket.sampleCount());
    }

    /**
     * Builds the detailed failure body text listing all violations.
     */
    private static String buildFailureDetail(SampleBucket bucket, SlaThreshold threshold) {
        var sb = new StringBuilder("SLA violation:");
        if (threshold != null) {
            if (bucket.percentile95() > threshold.maxP95Ms()) {
                sb.append(String.format(Locale.US, " p95=%.0fms (threshold: %dms),",
                        bucket.percentile95(), threshold.maxP95Ms()));
            }
            if (bucket.percentile99() > threshold.maxP99Ms()) {
                sb.append(String.format(Locale.US, " p99=%.0fms (threshold: %dms),",
                        bucket.percentile99(), threshold.maxP99Ms()));
            }
            if (bucket.avgResponseTime() > threshold.maxAvgMs()) {
                sb.append(String.format(Locale.US, " avg=%.0fms (threshold: %dms),",
                        bucket.avgResponseTime(), threshold.maxAvgMs()));
            }
            if (bucket.errorPercent() > threshold.maxErrorPercent()) {
                sb.append(String.format(Locale.US, " error_rate=%.1f%% (threshold: %.1f%%),",
                        bucket.errorPercent(), threshold.maxErrorPercent()));
            }
        }
        if (bucket.errorCount() > 0 && (threshold == null
                || bucket.errorPercent() <= threshold.maxErrorPercent())) {
            sb.append(String.format(Locale.US, " error_rate=%.1f%% (%d errors),",
                    bucket.errorPercent(), bucket.errorCount()));
        }
        // Remove trailing comma
        if (sb.charAt(sb.length() - 1) == ',') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    private static String formatTime(double seconds) {
        return String.format(Locale.US, "%.3f", seconds);
    }
}
