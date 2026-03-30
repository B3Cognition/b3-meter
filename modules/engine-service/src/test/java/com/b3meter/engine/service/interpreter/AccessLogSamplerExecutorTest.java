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
package com.b3meter.engine.service.interpreter;

import com.b3meter.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AccessLogSamplerExecutor}.
 */
class AccessLogSamplerExecutorTest {

    // =========================================================================
    // Null guard tests
    // =========================================================================

    @Test
    void throwsOnNullNode() {
        SampleResult r = new SampleResult("access");
        assertThrows(NullPointerException.class,
                () -> AccessLogSamplerExecutor.execute(null, r, Map.of()));
    }

    @Test
    void throwsOnNullResult() {
        PlanNode node = accessLogNode("/tmp/access.log");
        assertThrows(NullPointerException.class,
                () -> AccessLogSamplerExecutor.execute(node, null, Map.of()));
    }

    @Test
    void throwsOnNullVariables() {
        PlanNode node = accessLogNode("/tmp/access.log");
        SampleResult r = new SampleResult("access");
        assertThrows(NullPointerException.class,
                () -> AccessLogSamplerExecutor.execute(node, r, null));
    }

    // =========================================================================
    // Validation tests
    // =========================================================================

    @Test
    void failsOnEmptyLogFile() {
        PlanNode node = PlanNode.builder("AccessLogSampler", "access-empty")
                .build();

        SampleResult result = new SampleResult("access-empty");
        AccessLogSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("log_file is empty"));
    }

    @Test
    void returns404ForMissingFile() {
        PlanNode node = accessLogNode("/nonexistent/path/access.log");

        SampleResult result = new SampleResult("access-missing");
        AccessLogSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertEquals(404, result.getStatusCode());
        assertTrue(result.getFailureMessage().contains("not found"));
    }

    // =========================================================================
    // Parse-only mode (3-arg, no httpClientFactory)
    // =========================================================================

    @Test
    void parsesAccessLogFile(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("access.log");
        Files.writeString(logFile,
                "127.0.0.1 - - [01/Jan/2026:00:00:00 +0000] \"GET /index.html HTTP/1.1\" 200 1234\n"
                + "127.0.0.1 - - [01/Jan/2026:00:00:01 +0000] \"POST /api/data HTTP/1.1\" 201 56\n"
                + "127.0.0.1 - - [01/Jan/2026:00:00:02 +0000] \"DELETE /api/item/1 HTTP/1.1\" 204 0\n");

        PlanNode node = PlanNode.builder("AccessLogSampler", "access-parse")
                .property("AccessLogSampler.log_file", logFile.toString())
                .property("AccessLogSampler.domain", "example.com")
                .build();

        SampleResult result = new SampleResult("access-parse");
        AccessLogSamplerExecutor.execute(node, result, Map.of());

        assertEquals(200, result.getStatusCode());
        assertTrue(result.getResponseBody().contains("3 requests"));
        assertTrue(result.getResponseBody().contains("GET"));
        assertTrue(result.getResponseBody().contains("/index.html"));
        assertTrue(result.getResponseBody().contains("POST"));
        assertTrue(result.getResponseBody().contains("/api/data"));
        assertTrue(result.getResponseBody().contains("DELETE"));
        assertTrue(result.getResponseBody().contains("/api/item/1"));
    }

    @Test
    void parsesLogFileWithNoDomain(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("access.log");
        Files.writeString(logFile,
                "127.0.0.1 - - [01/Jan/2026:00:00:00 +0000] \"GET /test HTTP/1.1\" 200 100\n");

        PlanNode node = PlanNode.builder("AccessLogSampler", "no-domain")
                .property("AccessLogSampler.log_file", logFile.toString())
                .build();

        SampleResult result = new SampleResult("no-domain");
        AccessLogSamplerExecutor.execute(node, result, Map.of());

        assertEquals(200, result.getStatusCode());
        assertTrue(result.getResponseBody().contains("1 requests"));
    }

    @Test
    void parsesEmptyLogFileGracefully(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("empty.log");
        Files.writeString(logFile, "");

        PlanNode node = PlanNode.builder("AccessLogSampler", "empty-log")
                .property("AccessLogSampler.log_file", logFile.toString())
                .property("AccessLogSampler.domain", "example.com")
                .build();

        SampleResult result = new SampleResult("empty-log");
        AccessLogSamplerExecutor.execute(node, result, Map.of());

        assertEquals(200, result.getStatusCode());
        assertTrue(result.getResponseBody().contains("0 requests"));
    }

    // =========================================================================
    // 4-arg execute (with httpClientFactory)
    // =========================================================================

    @Test
    void fourArgFailsOnEmptyDomain(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("access.log");
        Files.writeString(logFile,
                "127.0.0.1 - - [01/Jan/2026:00:00:00 +0000] \"GET /test HTTP/1.1\" 200 100\n");

        PlanNode node = PlanNode.builder("AccessLogSampler", "no-domain-4arg")
                .property("AccessLogSampler.log_file", logFile.toString())
                .build();

        SampleResult result = new SampleResult("no-domain-4arg");
        AccessLogSamplerExecutor.execute(node, result, Map.of(), null);

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("domain is empty"));
    }

    @Test
    void fourArgReports404ForMissingFile() {
        PlanNode node = PlanNode.builder("AccessLogSampler", "missing-4arg")
                .property("AccessLogSampler.log_file", "/nonexistent/path.log")
                .property("AccessLogSampler.domain", "example.com")
                .build();

        SampleResult result = new SampleResult("missing-4arg");
        AccessLogSamplerExecutor.execute(node, result, Map.of(), null);

        assertFalse(result.isSuccess());
        assertEquals(404, result.getStatusCode());
    }

    @Test
    void fourArgReportsEmptyLogFile(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("empty.log");
        Files.writeString(logFile, "no valid log entries here\njust random text\n");

        PlanNode node = PlanNode.builder("AccessLogSampler", "empty-4arg")
                .property("AccessLogSampler.log_file", logFile.toString())
                .property("AccessLogSampler.domain", "example.com")
                .build();

        SampleResult result = new SampleResult("empty-4arg");
        AccessLogSamplerExecutor.execute(node, result, Map.of(), null);

        assertEquals(200, result.getStatusCode());
        assertTrue(result.getResponseBody().contains("no valid log entries"));
    }

    // =========================================================================
    // parseLogLine helper
    // =========================================================================

    @Test
    void parseLogLineExtractsMethodAndPath() {
        String[] parsed = AccessLogSamplerExecutor.parseLogLine(
                "127.0.0.1 - - [01/Jan/2026:00:00:00 +0000] \"GET /test HTTP/1.1\" 200 100");
        assertNotNull(parsed);
        assertEquals("GET", parsed[0]);
        assertEquals("/test", parsed[1]);
    }

    @Test
    void parseLogLineReturnsNullForInvalidLine() {
        assertNull(AccessLogSamplerExecutor.parseLogLine("not a log line"));
    }

    @Test
    void parseLogLineHandlesAllMethods() {
        for (String method : List.of("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "PATCH")) {
            String line = "127.0.0.1 - - [01/Jan/2026:00:00:00 +0000] \""
                    + method + " /path HTTP/1.1\" 200 0";
            String[] parsed = AccessLogSamplerExecutor.parseLogLine(line);
            assertNotNull(parsed, "Should parse " + method);
            assertEquals(method, parsed[0]);
            assertEquals("/path", parsed[1]);
        }
    }

    // =========================================================================
    // parseLogFile helper
    // =========================================================================

    @Test
    void parseLogFileExtractsMultipleEntries(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("multi.log");
        Files.writeString(logFile,
                "10.0.0.1 - - [01/Jan/2026:00:00:00 +0000] \"GET /a HTTP/1.1\" 200 100\n"
                + "10.0.0.1 - - [01/Jan/2026:00:00:00 +0000] \"POST /b HTTP/2.0\" 201 50\n"
                + "malformed line\n"
                + "10.0.0.1 - - [01/Jan/2026:00:00:00 +0000] \"DELETE /c HTTP/1.1\" 204 0\n");

        List<String[]> entries = AccessLogSamplerExecutor.parseLogFile(logFile);

        assertEquals(3, entries.size());
        assertEquals("GET", entries.get(0)[0]);
        assertEquals("/a", entries.get(0)[1]);
        assertEquals("POST", entries.get(1)[0]);
        assertEquals("/b", entries.get(1)[1]);
        assertEquals("DELETE", entries.get(2)[0]);
        assertEquals("/c", entries.get(2)[1]);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static PlanNode accessLogNode(String path) {
        return PlanNode.builder("AccessLogSampler", "access-test")
                .property("AccessLogSampler.log_file", path)
                .build();
    }
}
