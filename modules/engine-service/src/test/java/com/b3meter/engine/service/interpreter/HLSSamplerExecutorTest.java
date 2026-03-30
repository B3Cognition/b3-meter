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
 * Tests for {@link HLSSamplerExecutor}.
 *
 * <p>Uses an embedded {@link HttpServer} to simulate HLS endpoints. No external
 * libraries are required (Constitution Principle I).
 */
class HLSSamplerExecutorTest {

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
        SampleResult result = new SampleResult("hls-test");
        assertThrows(NullPointerException.class,
                () -> HLSSamplerExecutor.execute(null, result, Map.of()));
    }

    @Test
    void execute_throwsOnNullResult() {
        PlanNode node = hlsNode("http://localhost/master.m3u8", "best", 3);
        assertThrows(NullPointerException.class,
                () -> HLSSamplerExecutor.execute(node, null, Map.of()));
    }

    @Test
    void execute_throwsOnNullVariables() {
        PlanNode node = hlsNode("http://localhost/master.m3u8", "best", 3);
        SampleResult result = new SampleResult("hls-test");
        assertThrows(NullPointerException.class,
                () -> HLSSamplerExecutor.execute(node, result, null));
    }

    // =========================================================================
    // Empty URL validation
    // =========================================================================

    @Test
    void execute_failsOnEmptyUrl() {
        PlanNode node = hlsNode("", "best", 3);
        SampleResult result = new SampleResult("hls-test");

        HLSSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("url is empty"));
    }

    // =========================================================================
    // M3U8 parsing
    // =========================================================================

    @Test
    void parseMasterPlaylist_extractsVariants() {
        String master = "#EXTM3U\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360\n"
                + "360p.m3u8\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=1400000,RESOLUTION=1280x720\n"
                + "720p.m3u8\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=2800000,RESOLUTION=1920x1080\n"
                + "1080p.m3u8\n";

        List<HLSSamplerExecutor.Variant> variants = HLSSamplerExecutor.parseMasterPlaylist(master);

        assertEquals(3, variants.size());
        assertEquals("360p.m3u8", variants.get(0).uri);
        assertEquals(800000, variants.get(0).bandwidth);
        assertEquals("640x360", variants.get(0).resolution);
        assertEquals("720p.m3u8", variants.get(1).uri);
        assertEquals(1400000, variants.get(1).bandwidth);
        assertEquals("1080p.m3u8", variants.get(2).uri);
        assertEquals(2800000, variants.get(2).bandwidth);
    }

    @Test
    void parseMasterPlaylist_returnsEmptyForMediaPlaylist() {
        String media = "#EXTM3U\n"
                + "#EXT-X-TARGETDURATION:10\n"
                + "#EXTINF:9.009,\n"
                + "segment0.ts\n"
                + "#EXTINF:9.009,\n"
                + "segment1.ts\n";

        List<HLSSamplerExecutor.Variant> variants = HLSSamplerExecutor.parseMasterPlaylist(media);

        assertTrue(variants.isEmpty());
    }

    @Test
    void parseMediaPlaylist_extractsSegments() {
        String media = "#EXTM3U\n"
                + "#EXT-X-TARGETDURATION:10\n"
                + "#EXTINF:9.009,\n"
                + "segment0.ts\n"
                + "#EXTINF:9.009,\n"
                + "segment1.ts\n"
                + "#EXTINF:9.009,\n"
                + "segment2.ts\n"
                + "#EXT-X-ENDLIST\n";

        List<String> segments = HLSSamplerExecutor.parseMediaPlaylist(media,
                "http://cdn.example.com/stream/720p.m3u8");

        assertEquals(3, segments.size());
        assertEquals("http://cdn.example.com/stream/segment0.ts", segments.get(0));
        assertEquals("http://cdn.example.com/stream/segment1.ts", segments.get(1));
        assertEquals("http://cdn.example.com/stream/segment2.ts", segments.get(2));
    }

    @Test
    void parseMediaPlaylist_handlesAbsoluteSegmentUrls() {
        String media = "#EXTM3U\n"
                + "#EXTINF:10.0,\n"
                + "http://other.cdn.com/seg0.ts\n";

        List<String> segments = HLSSamplerExecutor.parseMediaPlaylist(media,
                "http://cdn.example.com/stream/media.m3u8");

        assertEquals(1, segments.size());
        assertEquals("http://other.cdn.com/seg0.ts", segments.get(0));
    }

    // =========================================================================
    // Variant selection
    // =========================================================================

    @Test
    void selectVariant_best() {
        List<HLSSamplerExecutor.Variant> variants = List.of(
                new HLSSamplerExecutor.Variant("360p.m3u8", 800000, "640x360"),
                new HLSSamplerExecutor.Variant("720p.m3u8", 1400000, "1280x720"),
                new HLSSamplerExecutor.Variant("1080p.m3u8", 2800000, "1920x1080"));

        HLSSamplerExecutor.Variant selected = HLSSamplerExecutor.selectVariant(variants, "best");
        assertEquals("1080p.m3u8", selected.uri);
    }

    @Test
    void selectVariant_worst() {
        List<HLSSamplerExecutor.Variant> variants = List.of(
                new HLSSamplerExecutor.Variant("360p.m3u8", 800000, "640x360"),
                new HLSSamplerExecutor.Variant("720p.m3u8", 1400000, "1280x720"));

        HLSSamplerExecutor.Variant selected = HLSSamplerExecutor.selectVariant(variants, "worst");
        assertEquals("360p.m3u8", selected.uri);
    }

    @Test
    void selectVariant_byResolution() {
        List<HLSSamplerExecutor.Variant> variants = List.of(
                new HLSSamplerExecutor.Variant("360p.m3u8", 800000, "640x360"),
                new HLSSamplerExecutor.Variant("720p.m3u8", 1400000, "1280x720"),
                new HLSSamplerExecutor.Variant("1080p.m3u8", 2800000, "1920x1080"));

        HLSSamplerExecutor.Variant selected = HLSSamplerExecutor.selectVariant(variants, "720p");
        assertEquals("720p.m3u8", selected.uri);
    }

    @Test
    void selectVariant_fallsToBestOnUnknownResolution() {
        List<HLSSamplerExecutor.Variant> variants = List.of(
                new HLSSamplerExecutor.Variant("360p.m3u8", 800000, "640x360"),
                new HLSSamplerExecutor.Variant("720p.m3u8", 1400000, "1280x720"));

        HLSSamplerExecutor.Variant selected = HLSSamplerExecutor.selectVariant(variants, "480p");
        assertEquals("720p.m3u8", selected.uri); // fallback to best
    }

    // =========================================================================
    // URL resolution
    // =========================================================================

    @Test
    void resolveUrl_relative() {
        assertEquals("http://cdn.com/stream/720p.m3u8",
                HLSSamplerExecutor.resolveUrl("http://cdn.com/stream/master.m3u8", "720p.m3u8"));
    }

    @Test
    void resolveUrl_absolute() {
        assertEquals("http://other.com/720p.m3u8",
                HLSSamplerExecutor.resolveUrl("http://cdn.com/master.m3u8", "http://other.com/720p.m3u8"));
    }

    // =========================================================================
    // Full integration: master -> variant -> segments
    // =========================================================================

    @Test
    void execute_fullHlsFlow() {
        String masterPlaylist = "#EXTM3U\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360\n"
                + "360p.m3u8\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=1400000,RESOLUTION=1280x720\n"
                + "720p.m3u8\n";

        String mediaPlaylist = "#EXTM3U\n"
                + "#EXT-X-TARGETDURATION:10\n"
                + "#EXTINF:9.009,\n"
                + "seg0.ts\n"
                + "#EXTINF:9.009,\n"
                + "seg1.ts\n"
                + "#EXTINF:9.009,\n"
                + "seg2.ts\n"
                + "#EXT-X-ENDLIST\n";

        byte[] segmentData = "FAKE_TS_SEGMENT_DATA_1234567890".getBytes(StandardCharsets.UTF_8);

        server.createContext("/stream/master.m3u8", exchange -> {
            byte[] body = masterPlaylist.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        server.createContext("/stream/720p.m3u8", exchange -> {
            byte[] body = mediaPlaylist.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        // Serve all segment requests
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            server.createContext("/stream/seg" + i + ".ts", exchange -> {
                exchange.sendResponseHeaders(200, segmentData.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(segmentData);
                }
            });
        }

        server.start();

        PlanNode node = hlsNode("http://127.0.0.1:" + serverPort + "/stream/master.m3u8", "720p", 3);
        SampleResult result = new SampleResult("hls-full");

        HLSSamplerExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess(), "Expected success but got: " + result.getFailureMessage());
        assertEquals(200, result.getStatusCode());
        String body = result.getResponseBody();
        assertTrue(body.contains("master: OK"), "Should mention master OK");
        assertTrue(body.contains("1280x720"), "Should mention selected resolution");
        assertTrue(body.contains("segments: 3/3"), "Should download all 3 segments");
        assertTrue(body.contains("total bytes:"), "Should report total bytes");
        assertTrue(result.getConnectTimeMs() >= 0);
        assertTrue(result.getTotalTimeMs() >= 0);
    }

    @Test
    void execute_selectsBestByDefault() {
        String masterPlaylist = "#EXTM3U\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360\n"
                + "360p.m3u8\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=2800000,RESOLUTION=1920x1080\n"
                + "1080p.m3u8\n";

        String mediaPlaylist = "#EXTM3U\n"
                + "#EXTINF:10.0,\n"
                + "seg0.ts\n"
                + "#EXT-X-ENDLIST\n";

        byte[] segData = "SEG".getBytes(StandardCharsets.UTF_8);

        server.createContext("/master.m3u8", exchange -> {
            byte[] body = masterPlaylist.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) { out.write(body); }
        });
        server.createContext("/1080p.m3u8", exchange -> {
            byte[] body = mediaPlaylist.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) { out.write(body); }
        });
        server.createContext("/seg0.ts", exchange -> {
            exchange.sendResponseHeaders(200, segData.length);
            try (OutputStream out = exchange.getResponseBody()) { out.write(segData); }
        });
        server.start();

        // No quality specified — should default to "best"
        PlanNode node = PlanNode.builder("HLSSampler", "hls-best")
                .property("HLSSampler.url", "http://127.0.0.1:" + serverPort + "/master.m3u8")
                .property("HLSSampler.segmentCount", 1)
                .build();
        SampleResult result = new SampleResult("hls-best");

        HLSSamplerExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess(), result.getFailureMessage());
        assertTrue(result.getResponseBody().contains("1920x1080"), "Should select best (1080p)");
    }

    // =========================================================================
    // Error handling
    // =========================================================================

    @Test
    void execute_handlesConnectionRefused() {
        // Don't start the server
        PlanNode node = hlsNode("http://127.0.0.1:" + serverPort + "/master.m3u8", "best", 3);
        SampleResult result = new SampleResult("hls-refused");

        HLSSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertFalse(result.getFailureMessage().isEmpty());
        assertTrue(result.getTotalTimeMs() >= 0);
    }

    @Test
    void execute_handlesMasterPlaylistError() {
        server.createContext("/master.m3u8", exchange -> {
            exchange.sendResponseHeaders(500, -1);
        });
        server.start();

        PlanNode node = hlsNode("http://127.0.0.1:" + serverPort + "/master.m3u8", "best", 3);
        SampleResult result = new SampleResult("hls-500");

        HLSSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("500"));
    }

    // =========================================================================
    // Variable substitution
    // =========================================================================

    @Test
    void execute_resolvesVariablesInUrl() {
        String masterPlaylist = "#EXTM3U\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=1000000,RESOLUTION=1280x720\n"
                + "720p.m3u8\n";
        String mediaPlaylist = "#EXTM3U\n#EXTINF:10.0,\nseg0.ts\n#EXT-X-ENDLIST\n";
        byte[] segData = "X".getBytes(StandardCharsets.UTF_8);

        server.createContext("/master.m3u8", exchange -> {
            byte[] body = masterPlaylist.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) { out.write(body); }
        });
        server.createContext("/720p.m3u8", exchange -> {
            byte[] body = mediaPlaylist.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) { out.write(body); }
        });
        server.createContext("/seg0.ts", exchange -> {
            exchange.sendResponseHeaders(200, segData.length);
            try (OutputStream out = exchange.getResponseBody()) { out.write(segData); }
        });
        server.start();

        PlanNode node = PlanNode.builder("HLSSampler", "hls-var")
                .property("HLSSampler.url", "http://127.0.0.1:${hlsPort}/master.m3u8")
                .property("HLSSampler.segmentCount", 1)
                .build();
        SampleResult result = new SampleResult("hls-var");
        Map<String, String> vars = new HashMap<>();
        vars.put("hlsPort", String.valueOf(serverPort));

        HLSSamplerExecutor.execute(node, result, vars);

        assertTrue(result.isSuccess(), result.getFailureMessage());
        assertEquals(200, result.getStatusCode());
    }

    // =========================================================================
    // Partial segment download (206)
    // =========================================================================

    @Test
    void execute_returnsPartialWhenSomeSegmentsFail() {
        String masterPlaylist = "#EXTM3U\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=1000000,RESOLUTION=1280x720\n"
                + "720p.m3u8\n";
        String mediaPlaylist = "#EXTM3U\n"
                + "#EXTINF:10.0,\nseg0.ts\n"
                + "#EXTINF:10.0,\nseg1.ts\n"
                + "#EXTINF:10.0,\nseg2.ts\n"
                + "#EXT-X-ENDLIST\n";
        byte[] segData = "OK".getBytes(StandardCharsets.UTF_8);

        server.createContext("/master.m3u8", exchange -> {
            byte[] body = masterPlaylist.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) { out.write(body); }
        });
        server.createContext("/720p.m3u8", exchange -> {
            byte[] body = mediaPlaylist.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) { out.write(body); }
        });
        // Only seg0 succeeds; seg1 and seg2 return 404
        server.createContext("/seg0.ts", exchange -> {
            exchange.sendResponseHeaders(200, segData.length);
            try (OutputStream out = exchange.getResponseBody()) { out.write(segData); }
        });
        server.createContext("/seg1.ts", exchange -> {
            exchange.sendResponseHeaders(404, -1);
        });
        server.createContext("/seg2.ts", exchange -> {
            exchange.sendResponseHeaders(404, -1);
        });
        server.start();

        PlanNode node = hlsNode("http://127.0.0.1:" + serverPort + "/master.m3u8", "best", 3);
        SampleResult result = new SampleResult("hls-partial");

        HLSSamplerExecutor.execute(node, result, Map.of());

        // Should return 206 (partial) since only 1/3 segments downloaded
        assertEquals(206, result.getStatusCode());
        assertTrue(result.getResponseBody().contains("segments: 1/3"));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static PlanNode hlsNode(String url, String quality, int segmentCount) {
        return PlanNode.builder("HLSSampler", "hls-test")
                .property("HLSSampler.url", url)
                .property("HLSSampler.quality", quality)
                .property("HLSSampler.segmentCount", segmentCount)
                .property("HLSSampler.connectTimeout", 5000)
                .property("HLSSampler.responseTimeout", 10000)
                .build();
    }
}
