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
package com.b3meter.engine.adapter.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HtmlReportGenerator}.
 */
class HtmlReportGeneratorTest {

    @TempDir
    Path tempDir;

    private static final String JTL_HEADER =
            "timeStamp,elapsed,label,responseCode,responseMessage,threadName,"
            + "dataType,success,failureMessage,bytes,sentBytes,grpThreads,allThreads,"
            + "URL,Latency,IdleTime,Connect";

    private Path writeJtl(String... lines) throws IOException {
        Path jtlFile = tempDir.resolve("results.jtl");
        StringBuilder sb = new StringBuilder();
        sb.append(JTL_HEADER).append("\n");
        for (String line : lines) {
            sb.append(line).append("\n");
        }
        Files.writeString(jtlFile, sb.toString());
        return jtlFile;
    }

    @Test
    void generate_createsIndexHtmlAndStatisticsJson() throws IOException {
        Path jtl = writeJtl(
                "1700000000000,150,Login,200,OK,Thread-1,text,true,,500,0,1,1,,50,0,10",
                "1700000000200,250,Login,200,OK,Thread-1,text,true,,600,0,1,1,,60,0,15",
                "1700000000500,100,Logout,200,OK,Thread-1,text,true,,200,0,1,1,,30,0,5"
        );

        Path outputDir = tempDir.resolve("report");
        HtmlReportGenerator.generate(jtl, outputDir);

        assertTrue(Files.exists(outputDir.resolve("index.html")),
                "index.html should be generated");
        assertTrue(Files.exists(outputDir.resolve("statistics.json")),
                "statistics.json should be generated");
    }

    @Test
    void generate_indexHtmlContainsSummaryStats() throws IOException {
        Path jtl = writeJtl(
                "1700000000000,100,API Call,200,OK,Thread-1,text,true,,500,0,1,1,,50,0,10",
                "1700000000200,200,API Call,200,OK,Thread-1,text,true,,600,0,1,1,,60,0,15",
                "1700000000500,300,API Call,500,FAIL,Thread-1,text,false,Server error,200,0,1,1,,30,0,5"
        );

        Path outputDir = tempDir.resolve("report");
        HtmlReportGenerator.generate(jtl, outputDir);

        String html = Files.readString(outputDir.resolve("index.html"));
        assertTrue(html.contains("b3meter Test Report"), "Should contain report title");
        assertTrue(html.contains("Total Samples"), "Should contain total samples card");
        assertTrue(html.contains("3"), "Should show 3 total samples");
        assertTrue(html.contains("API Call"), "Should contain label name");
        assertTrue(html.contains("33.33%"), "Should show ~33% error rate");
    }

    @Test
    void generate_statisticsJsonContainsPerLabelData() throws IOException {
        Path jtl = writeJtl(
                "1700000000000,100,Login,200,OK,Thread-1,text,true,,500,0,1,1,,50,0,10",
                "1700000000200,200,Dashboard,200,OK,Thread-1,text,true,,600,0,1,1,,60,0,15"
        );

        Path outputDir = tempDir.resolve("report");
        HtmlReportGenerator.generate(jtl, outputDir);

        String json = Files.readString(outputDir.resolve("statistics.json"));
        assertTrue(json.contains("\"Login\""), "Should contain Login label");
        assertTrue(json.contains("\"Dashboard\""), "Should contain Dashboard label");
        assertTrue(json.contains("\"Total\""), "Should contain Total entry");
        assertTrue(json.contains("\"sampleCount\": 1"), "Each label should have 1 sample");
    }

    @Test
    void generate_emptyJtl_throwsIllegalArgument() throws IOException {
        Path jtl = writeJtl(); // header only, no data
        Path outputDir = tempDir.resolve("report");

        assertThrows(IllegalArgumentException.class,
                () -> HtmlReportGenerator.generate(jtl, outputDir));
    }

    @Test
    void generate_nonExistentJtl_throwsIllegalArgument() {
        Path jtl = tempDir.resolve("nonexistent.jtl");
        Path outputDir = tempDir.resolve("report");

        assertThrows(IllegalArgumentException.class,
                () -> HtmlReportGenerator.generate(jtl, outputDir));
    }

    @Test
    void generate_createsOutputDirectoryIfAbsent() throws IOException {
        Path jtl = writeJtl(
                "1700000000000,100,Test,200,OK,Thread-1,text,true,,500,0,1,1,,50,0,10"
        );

        Path deepDir = tempDir.resolve("deep/nested/report");
        assertFalse(Files.exists(deepDir));

        HtmlReportGenerator.generate(jtl, deepDir);

        assertTrue(Files.exists(deepDir), "Output directory should be created");
        assertTrue(Files.exists(deepDir.resolve("index.html")));
    }

    @Test
    void generate_htmlContainsSvgChart() throws IOException {
        Path jtl = writeJtl(
                "1700000000000,100,Fast,200,OK,Thread-1,text,true,,500,0,1,1,,50,0,10",
                "1700000000200,500,Slow,200,OK,Thread-1,text,true,,600,0,1,1,,60,0,15"
        );

        Path outputDir = tempDir.resolve("report");
        HtmlReportGenerator.generate(jtl, outputDir);

        String html = Files.readString(outputDir.resolve("index.html"));
        assertTrue(html.contains("<svg"), "Should contain SVG chart");
        assertTrue(html.contains("Response Time by Label"), "Should contain chart title");
    }

    @Test
    void generate_htmlContainsEmbeddedCss() throws IOException {
        Path jtl = writeJtl(
                "1700000000000,100,Test,200,OK,Thread-1,text,true,,500,0,1,1,,50,0,10"
        );

        Path outputDir = tempDir.resolve("report");
        HtmlReportGenerator.generate(jtl, outputDir);

        String html = Files.readString(outputDir.resolve("index.html"));
        assertTrue(html.contains("<style>"), "Should contain embedded CSS");
        assertTrue(html.contains("font-family"), "CSS should include font-family");
    }

    @Test
    void parseCsvLine_handlesQuotedFieldsWithCommas() {
        String[] result = HtmlReportGenerator.parseCsvLine("a,\"b,c\",d");
        assertArrayEquals(new String[]{"a", "b,c", "d"}, result);
    }

    @Test
    void parseCsvLine_handlesEscapedQuotes() {
        String[] result = HtmlReportGenerator.parseCsvLine("a,\"say \"\"hello\"\"\",c");
        assertArrayEquals(new String[]{"a", "say \"hello\"", "c"}, result);
    }

    @Test
    void aggregate_computesPercentilesCorrectly() throws IOException {
        Path jtl = writeJtl(
                "1700000000000,10,Test,200,OK,T1,text,true,,100,0,1,1,,5,0,2",
                "1700000000100,20,Test,200,OK,T1,text,true,,100,0,1,1,,5,0,2",
                "1700000000200,30,Test,200,OK,T1,text,true,,100,0,1,1,,5,0,2",
                "1700000000300,40,Test,200,OK,T1,text,true,,100,0,1,1,,5,0,2",
                "1700000000400,50,Test,200,OK,T1,text,true,,100,0,1,1,,5,0,2",
                "1700000000500,60,Test,200,OK,T1,text,true,,100,0,1,1,,5,0,2",
                "1700000000600,70,Test,200,OK,T1,text,true,,100,0,1,1,,5,0,2",
                "1700000000700,80,Test,200,OK,T1,text,true,,100,0,1,1,,5,0,2",
                "1700000000800,90,Test,200,OK,T1,text,true,,100,0,1,1,,5,0,2",
                "1700000000900,100,Test,200,OK,T1,text,true,,100,0,1,1,,5,0,2"
        );

        List<HtmlReportGenerator.JtlRow> rows = HtmlReportGenerator.parseJtl(jtl);
        assertEquals(10, rows.size());

        Map<String, HtmlReportGenerator.LabelStats> stats = HtmlReportGenerator.aggregate(rows);
        HtmlReportGenerator.LabelStats testStats = stats.get("Test");
        assertNotNull(testStats);
        assertEquals(10, testStats.samples);
        assertEquals(10, testStats.min);
        assertEquals(100, testStats.max);
        assertEquals(55.0, testStats.avg, 0.01);
        assertTrue(testStats.p90 >= 90, "P90 should be >= 90");
        assertTrue(testStats.p95 >= 90, "P95 should be >= 90");
        assertTrue(testStats.p99 >= 90, "P99 should be >= 90");
    }

    @Test
    void generate_handlesErrorSamples() throws IOException {
        Path jtl = writeJtl(
                "1700000000000,100,OK Request,200,OK,T1,text,true,,500,0,1,1,,50,0,10",
                "1700000000200,500,Failed Request,500,FAIL,T1,text,false,Server error,0,0,1,1,,50,0,10"
        );

        Path outputDir = tempDir.resolve("report");
        HtmlReportGenerator.generate(jtl, outputDir);

        String json = Files.readString(outputDir.resolve("statistics.json"));
        assertTrue(json.contains("\"errorCount\": 1"), "Should show 1 error in Failed Request");
        assertTrue(json.contains("\"errorPct\": 100.00"),
                "Failed Request label should have 100% error rate");
    }
}
