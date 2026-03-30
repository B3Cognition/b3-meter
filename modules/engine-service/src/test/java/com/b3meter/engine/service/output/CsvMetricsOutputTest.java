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
 * Tests for {@link CsvMetricsOutput}.
 */
class CsvMetricsOutputTest {

    @TempDir
    Path tempDir;

    private static SampleBucket makeBucket(String label, long count) {
        return new SampleBucket(
                Instant.ofEpochMilli(1700000000000L), label, count, 1,
                50.0, 10.0, 200.0,
                90.0, 95.0, 99.0, count);
    }

    // =========================================================================
    // Header
    // =========================================================================

    @Test
    void writesHeaderRowOnStart() throws IOException {
        Path csvFile = tempDir.resolve("test.csv");
        CsvMetricsOutput csv = new CsvMetricsOutput();

        csv.start("run-1", Map.of("csv.file", csvFile.toString()));

        List<String> lines = Files.readAllLines(csvFile);
        assertEquals(1, lines.size(), "Should have exactly the header row");
        assertEquals("timestamp,label,sampleCount,errorCount,avgResponseTime,"
                + "minResponseTime,maxResponseTime,p90,p95,p99,throughput", lines.get(0));

        csv.stop();
    }

    // =========================================================================
    // Data rows
    // =========================================================================

    @Test
    void writesSampleDataInCorrectFormat() throws IOException {
        Path csvFile = tempDir.resolve("data.csv");
        CsvMetricsOutput csv = new CsvMetricsOutput();

        csv.start("run-2", Map.of("csv.file", csvFile.toString()));
        csv.writeSamples(List.of(makeBucket("http-get", 10)));
        csv.stop();

        List<String> lines = Files.readAllLines(csvFile);
        assertEquals(2, lines.size(), "Header + 1 data row");

        String dataLine = lines.get(1);
        String[] fields = dataLine.split(",");
        assertEquals(11, fields.length, "Should have 11 fields");
        assertEquals("1700000000000", fields[0]);
        assertEquals("http-get", fields[1]);
        assertEquals("10", fields[2]);
        assertEquals("1", fields[3]);
    }

    @Test
    void writesMultipleSamples() throws IOException {
        Path csvFile = tempDir.resolve("multi.csv");
        CsvMetricsOutput csv = new CsvMetricsOutput();

        csv.start("run-3", Map.of("csv.file", csvFile.toString()));
        csv.writeSamples(List.of(
                makeBucket("label-a", 5),
                makeBucket("label-b", 15)
        ));
        csv.stop();

        List<String> lines = Files.readAllLines(csvFile);
        assertEquals(3, lines.size(), "Header + 2 data rows");
    }

    // =========================================================================
    // Custom delimiter
    // =========================================================================

    @Test
    void customDelimiterWorks() throws IOException {
        Path csvFile = tempDir.resolve("tab.csv");
        CsvMetricsOutput csv = new CsvMetricsOutput();

        csv.start("run-4", Map.of(
                "csv.file", csvFile.toString(),
                "csv.delimiter", "\t"
        ));
        csv.writeSamples(List.of(makeBucket("test", 3)));
        csv.stop();

        List<String> lines = Files.readAllLines(csvFile);
        // Header should be tab-separated
        assertTrue(lines.get(0).contains("\t"), "Header should use tab delimiter");
        assertFalse(lines.get(0).contains(","), "Header should not use comma with tab delimiter");

        // Data row should be tab-separated
        String[] fields = lines.get(1).split("\t");
        assertEquals(11, fields.length, "Should have 11 tab-separated fields");
    }

    // =========================================================================
    // File creation
    // =========================================================================

    @Test
    void fileIsCreatedAtConfiguredPath() {
        Path csvFile = tempDir.resolve("subdir/output.csv");
        CsvMetricsOutput csv = new CsvMetricsOutput();

        csv.start("run-5", Map.of("csv.file", csvFile.toString()));

        assertTrue(Files.exists(csvFile), "CSV file should exist at configured path");
        assertTrue(Files.exists(csvFile.getParent()), "Parent directory should be created");

        csv.stop();
    }

    @Test
    void defaultFileNameUsedWhenNotConfigured() {
        // We just verify no exception is thrown with empty config (defaults to "results.csv")
        CsvMetricsOutput csv = new CsvMetricsOutput();
        assertDoesNotThrow(() -> csv.start("run-6", Map.of()));
        csv.stop();
    }

    // =========================================================================
    // Edge cases
    // =========================================================================

    @Test
    void emptyListIsNoOp() throws IOException {
        Path csvFile = tempDir.resolve("empty.csv");
        CsvMetricsOutput csv = new CsvMetricsOutput();

        csv.start("run-7", Map.of("csv.file", csvFile.toString()));
        csv.writeSamples(List.of());
        csv.stop();

        List<String> lines = Files.readAllLines(csvFile);
        assertEquals(1, lines.size(), "Only header, no data rows");
    }

    @Test
    void labelWithCommaIsEscaped() throws IOException {
        Path csvFile = tempDir.resolve("escape.csv");
        CsvMetricsOutput csv = new CsvMetricsOutput();

        csv.start("run-8", Map.of("csv.file", csvFile.toString()));
        csv.writeSamples(List.of(makeBucket("label,with,commas", 1)));
        csv.stop();

        List<String> lines = Files.readAllLines(csvFile);
        String dataLine = lines.get(1);
        // The label should be quoted
        assertTrue(dataLine.contains("\"label,with,commas\""),
                "Label with commas should be quoted");
    }

    @Test
    void nameReturnsCsv() {
        CsvMetricsOutput csv = new CsvMetricsOutput();
        assertEquals("csv", csv.name());
    }
}
