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
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DASHSamplerExecutor}.
 *
 * <p>Uses an embedded {@link HttpServer} to simulate DASH endpoints. No external
 * libraries are required (Constitution Principle I).
 */
class DASHSamplerExecutorTest {

    private HttpServer server;
    private int serverPort;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverPort = server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    // =========================================================================
    // Null guards
    // =========================================================================

    @Test
    void execute_throwsOnNullNode() {
        SampleResult result = new SampleResult("dash-test");
        assertThrows(NullPointerException.class,
                () -> DASHSamplerExecutor.execute(null, result, Map.of()));
    }

    @Test
    void execute_throwsOnNullResult() {
        PlanNode node = dashNode("http://localhost/manifest.mpd", "best", 3);
        assertThrows(NullPointerException.class,
                () -> DASHSamplerExecutor.execute(node, null, Map.of()));
    }

    @Test
    void execute_throwsOnNullVariables() {
        PlanNode node = dashNode("http://localhost/manifest.mpd", "best", 3);
        SampleResult result = new SampleResult("dash-test");
        assertThrows(NullPointerException.class,
                () -> DASHSamplerExecutor.execute(node, result, null));
    }

    // =========================================================================
    // Empty URL validation
    // =========================================================================

    @Test
    void execute_failsOnEmptyUrl() {
        PlanNode node = dashNode("", "best", 3);
        SampleResult result = new SampleResult("dash-test");

        DASHSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("url is empty"));
    }

    // =========================================================================
    // MPD XML parsing
    // =========================================================================

    @Test
    void parseMpd_extractsRepresentations() throws IOException {
        String mpd = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<MPD>\n"
                + "  <Period>\n"
                + "    <AdaptationSet mimeType=\"video/mp4\">\n"
                + "      <SegmentTemplate media=\"segment-$RepresentationID$-$Number$.m4s\"\n"
                + "                       initialization=\"init-$RepresentationID$.mp4\"\n"
                + "                       startNumber=\"1\"/>\n"
                + "      <Representation id=\"360p\" bandwidth=\"800000\" width=\"640\" height=\"360\"/>\n"
                + "      <Representation id=\"720p\" bandwidth=\"1400000\" width=\"1280\" height=\"720\"/>\n"
                + "      <Representation id=\"1080p\" bandwidth=\"2800000\" width=\"1920\" height=\"1080\"/>\n"
                + "    </AdaptationSet>\n"
                + "  </Period>\n"
                + "</MPD>";

        List<DASHSamplerExecutor.Representation> reps = DASHSamplerExecutor.parseMpd(mpd);

        assertEquals(3, reps.size());

        assertEquals("360p", reps.get(0).id);
        assertEquals(800000, reps.get(0).bandwidth);
        assertEquals(640, reps.get(0).width);
        assertEquals(360, reps.get(0).height);
        assertEquals("segment-360p-$Number$.m4s", reps.get(0).mediaTemplate);
        assertEquals("init-360p.mp4", reps.get(0).initUrl);
        assertEquals(1, reps.get(0).startNumber);

        assertEquals("720p", reps.get(1).id);
        assertEquals(1400000, reps.get(1).bandwidth);
        assertEquals(1280, reps.get(1).width);
        assertEquals(720, reps.get(1).height);

        assertEquals("1080p", reps.get(2).id);
        assertEquals(2800000, reps.get(2).bandwidth);
    }

    @Test
    void parseMpd_handlesRepresentationLevelTemplate() throws IOException {
        String mpd = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<MPD>\n"
                + "  <Period>\n"
                + "    <AdaptationSet mimeType=\"video/mp4\">\n"
                + "      <Representation id=\"v1\" bandwidth=\"500000\" width=\"320\" height=\"240\">\n"
                + "        <SegmentTemplate media=\"v1-seg$Number$.m4s\"\n"
                + "                         initialization=\"v1-init.mp4\"\n"
                + "                         startNumber=\"0\"/>\n"
                + "      </Representation>\n"
                + "    </AdaptationSet>\n"
                + "  </Period>\n"
                + "</MPD>";

        List<DASHSamplerExecutor.Representation> reps = DASHSamplerExecutor.parseMpd(mpd);

        assertEquals(1, reps.size());
        assertEquals("v1-seg$Number$.m4s", reps.get(0).mediaTemplate);
        assertEquals("v1-init.mp4", reps.get(0).initUrl);
        assertEquals(0, reps.get(0).startNumber);
    }

    @Test
    void parseMpd_returnsEmptyForEmptyMpd() throws IOException {
        String mpd = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<MPD></MPD>";

        List<DASHSamplerExecutor.Representation> reps = DASHSamplerExecutor.parseMpd(mpd);

        assertTrue(reps.isEmpty());
    }

    // =========================================================================
    // Representation selection
    // =========================================================================

    @Test
    void selectRepresentation_best() {
        List<DASHSamplerExecutor.Representation> reps = List.of(
                new DASHSamplerExecutor.Representation("360p", 800000, 640, 360, null, null, 1),
                new DASHSamplerExecutor.Representation("720p", 1400000, 1280, 720, null, null, 1),
                new DASHSamplerExecutor.Representation("1080p", 2800000, 1920, 1080, null, null, 1));

        DASHSamplerExecutor.Representation selected =
                DASHSamplerExecutor.selectRepresentation(reps, "best");

        assertEquals("1080p", selected.id);
    }

    @Test
    void selectRepresentation_worst() {
        List<DASHSamplerExecutor.Representation> reps = List.of(
                new DASHSamplerExecutor.Representation("360p", 800000, 640, 360, null, null, 1),
                new DASHSamplerExecutor.Representation("720p", 1400000, 1280, 720, null, null, 1));

        DASHSamplerExecutor.Representation selected =
                DASHSamplerExecutor.selectRepresentation(reps, "worst");

        assertEquals("360p", selected.id);
    }

    @Test
    void selectRepresentation_byHeight() {
        List<DASHSamplerExecutor.Representation> reps = List.of(
                new DASHSamplerExecutor.Representation("360p", 800000, 640, 360, null, null, 1),
                new DASHSamplerExecutor.Representation("720p", 1400000, 1280, 720, null, null, 1),
                new DASHSamplerExecutor.Representation("1080p", 2800000, 1920, 1080, null, null, 1));

        DASHSamplerExecutor.Representation selected =
                DASHSamplerExecutor.selectRepresentation(reps, "720p");

        assertEquals("720p", selected.id);
    }

    // =========================================================================
    // Segment URL construction
    // =========================================================================

    @Test
    void buildSegmentUrls_constructsCorrectUrls() {
        DASHSamplerExecutor.Representation rep = new DASHSamplerExecutor.Representation(
                "720p", 1400000, 1280, 720,
                "segment-720p-$Number$.m4s", "init-720p.mp4", 1);

        List<String> urls = DASHSamplerExecutor.buildSegmentUrls(rep,
                "http://cdn.example.com/stream/manifest.mpd", 3);

        assertEquals(3, urls.size());
        assertEquals("http://cdn.example.com/stream/segment-720p-1.m4s", urls.get(0));
        assertEquals("http://cdn.example.com/stream/segment-720p-2.m4s", urls.get(1));
        assertEquals("http://cdn.example.com/stream/segment-720p-3.m4s", urls.get(2));
    }

    @Test
    void buildSegmentUrls_respectsStartNumber() {
        DASHSamplerExecutor.Representation rep = new DASHSamplerExecutor.Representation(
                "v1", 500000, 320, 240,
                "seg$Number$.m4s", null, 0);

        List<String> urls = DASHSamplerExecutor.buildSegmentUrls(rep,
                "http://cdn.com/manifest.mpd", 2);

        assertEquals(2, urls.size());
        assertEquals("http://cdn.com/seg0.m4s", urls.get(0));
        assertEquals("http://cdn.com/seg1.m4s", urls.get(1));
    }

    @Test
    void buildSegmentUrls_returnsEmptyWhenNoTemplate() {
        DASHSamplerExecutor.Representation rep = new DASHSamplerExecutor.Representation(
                "v1", 500000, 320, 240,
                null, null, 1);

        List<String> urls = DASHSamplerExecutor.buildSegmentUrls(rep,
                "http://cdn.com/manifest.mpd", 3);

        assertTrue(urls.isEmpty());
    }

    // =========================================================================
    // URL resolution
    // =========================================================================

    @Test
    void resolveUrl_relative() {
        assertEquals("http://cdn.com/stream/segment-1.m4s",
                DASHSamplerExecutor.resolveUrl("http://cdn.com/stream/manifest.mpd", "segment-1.m4s"));
    }

    @Test
    void resolveUrl_absolute() {
        assertEquals("http://other.com/seg.m4s",
                DASHSamplerExecutor.resolveUrl("http://cdn.com/manifest.mpd", "http://other.com/seg.m4s"));
    }

    // =========================================================================
    // Full integration: MPD -> representation -> segments
    // =========================================================================

    @Test
    void execute_fullDashFlow() {
        String mpdContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<MPD>\n"
                + "  <Period>\n"
                + "    <AdaptationSet mimeType=\"video/mp4\">\n"
                + "      <SegmentTemplate media=\"seg-$RepresentationID$-$Number$.m4s\"\n"
                + "                       initialization=\"init-$RepresentationID$.mp4\"\n"
                + "                       startNumber=\"1\"/>\n"
                + "      <Representation id=\"lo\" bandwidth=\"500000\" width=\"640\" height=\"360\"/>\n"
                + "      <Representation id=\"hi\" bandwidth=\"2000000\" width=\"1280\" height=\"720\"/>\n"
                + "    </AdaptationSet>\n"
                + "  </Period>\n"
                + "</MPD>";

        byte[] segData = "DASH_SEGMENT_DATA".getBytes(StandardCharsets.UTF_8);
        byte[] initData = "DASH_INIT".getBytes(StandardCharsets.UTF_8);

        server.createContext("/stream/manifest.mpd", exchange -> {
            byte[] body = mpdContent.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        server.createContext("/stream/init-hi.mp4", exchange -> {
            exchange.sendResponseHeaders(200, initData.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(initData);
            }
        });

        for (int i = 1; i <= 3; i++) {
            server.createContext("/stream/seg-hi-" + i + ".m4s", exchange -> {
                exchange.sendResponseHeaders(200, segData.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(segData);
                }
            });
        }

        server.start();

        PlanNode node = dashNode("http://127.0.0.1:" + serverPort + "/stream/manifest.mpd", "720p", 3);
        SampleResult result = new SampleResult("dash-full");

        DASHSamplerExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess(), "Expected success but got: " + result.getFailureMessage());
        assertEquals(200, result.getStatusCode());
        String body = result.getResponseBody();
        assertTrue(body.contains("mpd: OK"), "Should mention mpd OK");
        assertTrue(body.contains("1280x720"), "Should mention selected resolution");
        assertTrue(body.contains("init: OK"), "Init segment should be OK");
        assertTrue(body.contains("segments: 3/3"), "Should download all 3 segments");
        assertTrue(body.contains("total bytes:"), "Should report total bytes");
        assertTrue(result.getConnectTimeMs() >= 0);
        assertTrue(result.getTotalTimeMs() >= 0);
    }

    // =========================================================================
    // Error handling
    // =========================================================================

    @Test
    void execute_handlesConnectionRefused() {
        // Don't start the server
        PlanNode node = dashNode("http://127.0.0.1:" + serverPort + "/manifest.mpd", "best", 3);
        SampleResult result = new SampleResult("dash-refused");

        DASHSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertFalse(result.getFailureMessage().isEmpty());
        assertTrue(result.getTotalTimeMs() >= 0);
    }

    @Test
    void execute_failsOnEmptyMpd() {
        String emptyMpd = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<MPD></MPD>";

        server.createContext("/manifest.mpd", exchange -> {
            byte[] body = emptyMpd.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();

        PlanNode node = dashNode("http://127.0.0.1:" + serverPort + "/manifest.mpd", "best", 3);
        SampleResult result = new SampleResult("dash-empty");

        DASHSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("no Representation"));
    }

    @Test
    void execute_handlesMpdFetchError() {
        server.createContext("/manifest.mpd", exchange -> {
            exchange.sendResponseHeaders(500, -1);
        });
        server.start();

        PlanNode node = dashNode("http://127.0.0.1:" + serverPort + "/manifest.mpd", "best", 3);
        SampleResult result = new SampleResult("dash-500");

        DASHSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("500"));
    }

    // =========================================================================
    // Variable substitution
    // =========================================================================

    @Test
    void execute_resolvesVariablesInUrl() {
        String mpdContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<MPD><Period><AdaptationSet>\n"
                + "  <SegmentTemplate media=\"seg$Number$.m4s\" startNumber=\"1\"/>\n"
                + "  <Representation id=\"v1\" bandwidth=\"500000\" width=\"320\" height=\"240\"/>\n"
                + "</AdaptationSet></Period></MPD>";
        byte[] segData = "X".getBytes(StandardCharsets.UTF_8);

        server.createContext("/manifest.mpd", exchange -> {
            byte[] body = mpdContent.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) { out.write(body); }
        });
        server.createContext("/seg1.m4s", exchange -> {
            exchange.sendResponseHeaders(200, segData.length);
            try (OutputStream out = exchange.getResponseBody()) { out.write(segData); }
        });
        server.start();

        PlanNode node = PlanNode.builder("DASHSampler", "dash-var")
                .property("DASHSampler.url", "http://127.0.0.1:${dashPort}/manifest.mpd")
                .property("DASHSampler.segmentCount", 1)
                .build();
        SampleResult result = new SampleResult("dash-var");
        Map<String, String> vars = new HashMap<>();
        vars.put("dashPort", String.valueOf(serverPort));

        DASHSamplerExecutor.execute(node, result, vars);

        assertTrue(result.isSuccess(), result.getFailureMessage());
        assertEquals(200, result.getStatusCode());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static PlanNode dashNode(String url, String quality, int segmentCount) {
        return PlanNode.builder("DASHSampler", "dash-test")
                .property("DASHSampler.url", url)
                .property("DASHSampler.quality", quality)
                .property("DASHSampler.segmentCount", segmentCount)
                .property("DASHSampler.connectTimeout", 5000)
                .build();
    }
}
