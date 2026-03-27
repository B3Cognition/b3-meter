package com.jmeternext.engine.service.interpreter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SimpleDataWriterExecutor}.
 */
class SimpleDataWriterExecutorTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        SimpleDataWriterExecutor.closeAll();
    }

    @Test
    void open_createsFileWithHeader() throws IOException {
        String filePath = tempDir.resolve("test.jtl").toString();
        SimpleDataWriterExecutor.open(filePath);

        assertTrue(SimpleDataWriterExecutor.isOpen(filePath));
        SimpleDataWriterExecutor.close(filePath);

        List<String> lines = Files.readAllLines(Path.of(filePath));
        assertFalse(lines.isEmpty(), "JTL file should have at least the header");
        assertEquals(SimpleDataWriterExecutor.CSV_HEADER, lines.get(0));
    }

    @Test
    void writeSample_appendsCsvLine() throws IOException {
        String filePath = tempDir.resolve("results.jtl").toString();
        SimpleDataWriterExecutor.open(filePath);

        SampleResult result = new SampleResult("GET /api/test");
        result.setStatusCode(200);
        result.setTotalTimeMs(150);
        result.setLatencyMs(50);
        result.setConnectTimeMs(10);
        result.setSuccess(true);

        SimpleDataWriterExecutor.writeSample(filePath, result);
        SimpleDataWriterExecutor.close(filePath);

        List<String> lines = Files.readAllLines(Path.of(filePath));
        assertEquals(2, lines.size(), "header + 1 data line");

        String dataLine = lines.get(1);
        assertTrue(dataLine.contains("GET /api/test"), "line should contain label");
        assertTrue(dataLine.contains("200"), "line should contain status code");
        assertTrue(dataLine.contains("150"), "line should contain elapsed time");
        assertTrue(dataLine.contains("true"), "line should contain success=true");
    }

    @Test
    void writeSample_failedSample_containsFailureMessage() throws IOException {
        String filePath = tempDir.resolve("errors.jtl").toString();
        SimpleDataWriterExecutor.open(filePath);

        SampleResult result = new SampleResult("POST /api/fail");
        result.setStatusCode(500);
        result.setTotalTimeMs(300);
        result.setFailureMessage("Internal Server Error");

        SimpleDataWriterExecutor.writeSample(filePath, result);
        SimpleDataWriterExecutor.close(filePath);

        List<String> lines = Files.readAllLines(Path.of(filePath));
        String dataLine = lines.get(1);
        assertTrue(dataLine.contains("false"), "failed sample should have success=false");
        assertTrue(dataLine.contains("Internal Server Error"),
                "line should contain failure message");
    }

    @Test
    void writeSample_multipleResults_appendsMultipleLines() throws IOException {
        String filePath = tempDir.resolve("multi.jtl").toString();
        SimpleDataWriterExecutor.open(filePath);

        for (int i = 0; i < 5; i++) {
            SampleResult r = new SampleResult("Sampler " + i);
            r.setTotalTimeMs(100 + i * 10);
            SimpleDataWriterExecutor.writeSample(filePath, r);
        }
        SimpleDataWriterExecutor.close(filePath);

        List<String> lines = Files.readAllLines(Path.of(filePath));
        assertEquals(6, lines.size(), "header + 5 data lines");
    }

    @Test
    void formatCsvLine_escapesCommasInLabel() {
        SampleResult result = new SampleResult("Label, with comma");
        result.setStatusCode(200);
        result.setTotalTimeMs(100);

        String line = SimpleDataWriterExecutor.formatCsvLine(result);
        assertTrue(line.contains("\"Label, with comma\""),
                "commas in label should be quoted: " + line);
    }

    @Test
    void close_nonExistentPath_doesNotThrow() {
        assertDoesNotThrow(() -> SimpleDataWriterExecutor.close("/nonexistent/path.jtl"));
    }

    @Test
    void close_nullPath_doesNotThrow() {
        assertDoesNotThrow(() -> SimpleDataWriterExecutor.close(null));
    }

    @Test
    void writeSample_withoutOpen_doesNotThrow() {
        SampleResult result = new SampleResult("test");
        // Should log a warning but not throw
        assertDoesNotThrow(() ->
                SimpleDataWriterExecutor.writeSample("/not/opened.jtl", result));
    }

    @Test
    void open_nullPath_throws() {
        assertThrows(NullPointerException.class,
                () -> SimpleDataWriterExecutor.open(null));
    }
}
