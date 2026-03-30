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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JsonMetricsOutput}.
 */
class JsonMetricsOutputTest {

    @TempDir
    Path tempDir;

    private static SampleBucket makeBucket(String label, long count) {
        return new SampleBucket(
                Instant.ofEpochMilli(1700000000000L), label, count, 1,
                50.0, 10.0, 200.0,
                90.0, 95.0, 99.0, count);
    }

    // =========================================================================
    // Valid NDJSON
    // =========================================================================

    @Test
    void writesValidNdjson() throws IOException {
        Path jsonFile = tempDir.resolve("test.jsonl");
        JsonMetricsOutput json = new JsonMetricsOutput();

        json.start("run-1", Map.of("json.file", jsonFile.toString()));
        json.writeSamples(List.of(
                makeBucket("http-get", 10),
                makeBucket("http-post", 5)
        ));
        json.stop();

        List<String> lines = Files.readAllLines(jsonFile);
        assertEquals(2, lines.size(), "Should have 2 NDJSON lines");

        // Each line should be a valid JSON object (starts with { and ends with })
        for (String line : lines) {
            assertTrue(line.startsWith("{"), "Each line should start with {");
            assertTrue(line.endsWith("}"), "Each line should end with }");
            assertFalse(line.contains("\n"), "No newlines within a single JSON line");
        }
    }

    // =========================================================================
    // Parseable JSON with expected fields
    // =========================================================================

    @Test
    void eachLineContainsAllExpectedFields() throws IOException {
        Path jsonFile = tempDir.resolve("fields.jsonl");
        JsonMetricsOutput json = new JsonMetricsOutput();

        json.start("run-2", Map.of("json.file", jsonFile.toString()));
        json.writeSamples(List.of(makeBucket("api-call", 7)));
        json.stop();

        String line = Files.readAllLines(jsonFile).get(0);

        // Verify all expected keys are present
        List<String> expectedKeys = List.of(
                "\"timestamp\":", "\"timestampIso\":", "\"label\":",
                "\"sampleCount\":", "\"errorCount\":", "\"avgResponseTime\":",
                "\"minResponseTime\":", "\"maxResponseTime\":",
                "\"p90\":", "\"p95\":", "\"p99\":", "\"throughput\":");

        for (String key : expectedKeys) {
            assertTrue(line.contains(key), "JSON should contain key: " + key);
        }
    }

    @Test
    void fieldValuesAreCorrect() throws IOException {
        Path jsonFile = tempDir.resolve("values.jsonl");
        JsonMetricsOutput json = new JsonMetricsOutput();

        json.start("run-3", Map.of("json.file", jsonFile.toString()));
        json.writeSamples(List.of(makeBucket("test-label", 10)));
        json.stop();

        String line = Files.readAllLines(jsonFile).get(0);

        assertTrue(line.contains("\"timestamp\":1700000000000"), "Should contain epoch millis");
        assertTrue(line.contains("\"label\":\"test-label\""), "Should contain label");
        assertTrue(line.contains("\"sampleCount\":10"), "Should contain sample count");
        assertTrue(line.contains("\"errorCount\":1"), "Should contain error count");
    }

    // =========================================================================
    // File creation
    // =========================================================================

    @Test
    void fileIsCreatedAtConfiguredPath() {
        Path jsonFile = tempDir.resolve("subdir/results.jsonl");
        JsonMetricsOutput json = new JsonMetricsOutput();

        json.start("run-4", Map.of("json.file", jsonFile.toString()));

        assertTrue(Files.exists(jsonFile), "JSON file should exist at configured path");

        json.stop();
    }

    // =========================================================================
    // JSON escaping
    // =========================================================================

    @Test
    void labelWithSpecialCharsIsEscaped() throws IOException {
        Path jsonFile = tempDir.resolve("escape.jsonl");
        JsonMetricsOutput json = new JsonMetricsOutput();

        json.start("run-5", Map.of("json.file", jsonFile.toString()));
        json.writeSamples(List.of(makeBucket("label\"with\\special\nchars", 1)));
        json.stop();

        String line = Files.readAllLines(jsonFile).get(0);

        // The special characters should be escaped
        assertTrue(line.contains("\\\""), "Double quotes should be escaped");
        assertTrue(line.contains("\\\\"), "Backslashes should be escaped");
        assertTrue(line.contains("\\n"), "Newlines should be escaped");

        // The overall line should still be valid JSON structure
        assertTrue(line.startsWith("{"));
        assertTrue(line.endsWith("}"));
    }

    // =========================================================================
    // Edge cases
    // =========================================================================

    @Test
    void emptyListIsNoOp() throws IOException {
        Path jsonFile = tempDir.resolve("empty.jsonl");
        JsonMetricsOutput json = new JsonMetricsOutput();

        json.start("run-6", Map.of("json.file", jsonFile.toString()));
        json.writeSamples(List.of());
        json.stop();

        List<String> lines = Files.readAllLines(jsonFile);
        assertTrue(lines.isEmpty() || (lines.size() == 1 && lines.get(0).isEmpty()),
                "No data lines should be written for empty sample list");
    }

    @Test
    void nameReturnsJson() {
        JsonMetricsOutput json = new JsonMetricsOutput();
        assertEquals("json", json.name());
    }

    @Test
    void stopClosesWriter() throws IOException {
        Path jsonFile = tempDir.resolve("stop.jsonl");
        JsonMetricsOutput json = new JsonMetricsOutput();

        json.start("run-7", Map.of("json.file", jsonFile.toString()));
        json.writeSamples(List.of(makeBucket("before-stop", 1)));
        json.stop();

        // Writing after stop should be a no-op (writer is null), not throw
        assertDoesNotThrow(() -> json.writeSamples(List.of(makeBucket("after-stop", 1))));

        // Only the first sample should have been written
        List<String> lines = Files.readAllLines(jsonFile);
        assertEquals(1, lines.size());
    }
}
