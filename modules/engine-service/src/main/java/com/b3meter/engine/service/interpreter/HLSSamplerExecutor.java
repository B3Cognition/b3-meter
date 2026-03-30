package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes an {@code HLSSampler} {@link PlanNode}.
 *
 * <p>Reads the following sampler properties:
 * <ul>
 *   <li>{@code HLSSampler.url} — URL to master M3U8 playlist</li>
 *   <li>{@code HLSSampler.quality} — preferred quality (e.g., "720p", "480p", "best", "worst"), default "best"</li>
 *   <li>{@code HLSSampler.segmentCount} — number of segments to download, default 3</li>
 *   <li>{@code HLSSampler.connectTimeout} — ms, default 5000</li>
 *   <li>{@code HLSSampler.responseTimeout} — ms, default 10000</li>
 * </ul>
 *
 * <p>Implementation flow:
 * <ol>
 *   <li>GET master.m3u8 — parse for {@code #EXT-X-STREAM-INF} variant streams</li>
 *   <li>Select variant by quality preference (bandwidth or resolution)</li>
 *   <li>GET media.m3u8 for selected variant — parse for {@code #EXTINF} segments</li>
 *   <li>GET each segment URL (up to segmentCount)</li>
 * </ol>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class HLSSamplerExecutor {

    private static final Logger LOG = Logger.getLogger(HLSSamplerExecutor.class.getName());

    private static final int DEFAULT_SEGMENT_COUNT = 3;
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_RESPONSE_TIMEOUT_MS = 10000;

    private HLSSamplerExecutor() {
        // utility class — not instantiable
    }

    /**
     * Executes the HLS operation described by {@code node}.
     *
     * @param node      the HLSSampler node; must not be {@code null}
     * @param result    the sample result to populate; must not be {@code null}
     * @param variables current VU variable scope for {@code ${varName}} substitution
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String url = resolve(node.getStringProp("HLSSampler.url", ""), variables);
        String quality = resolve(node.getStringProp("HLSSampler.quality", "best"), variables);
        int segmentCount = node.getIntProp("HLSSampler.segmentCount", DEFAULT_SEGMENT_COUNT);
        int connectTimeout = node.getIntProp("HLSSampler.connectTimeout", DEFAULT_CONNECT_TIMEOUT_MS);
        int responseTimeout = node.getIntProp("HLSSampler.responseTimeout", DEFAULT_RESPONSE_TIMEOUT_MS);

        if (url.isBlank()) {
            result.setFailureMessage("HLSSampler.url is empty");
            return;
        }

        LOG.log(Level.FINE, "HLSSamplerExecutor: fetching master playlist from {0}", url);

        long start = System.currentTimeMillis();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeout))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        try {
            // Step 1: GET master playlist
            String masterBody = httpGet(client, url, responseTimeout);
            long connectTime = System.currentTimeMillis() - start;
            result.setConnectTimeMs(connectTime);

            // Step 2: Parse master playlist for variants
            List<Variant> variants = parseMasterPlaylist(masterBody);

            if (variants.isEmpty()) {
                // Might be a media playlist directly (no variants)
                List<String> segments = parseMediaPlaylist(masterBody, url);
                int downloaded = downloadSegments(client, segments, segmentCount, responseTimeout);
                int target = Math.min(segmentCount, segments.size());

                long total = System.currentTimeMillis() - start;
                result.setTotalTimeMs(total);
                result.setLatencyMs(total - connectTime);
                result.setStatusCode(downloaded == target ? 200 : 206);
                result.setResponseBody("master: OK (media playlist), segments: "
                        + downloaded + "/" + target + " downloaded");
                return;
            }

            // Step 3: Select variant by quality
            Variant selected = selectVariant(variants, quality);
            String variantUrl = resolveUrl(url, selected.uri);

            // Step 4: GET media playlist
            String mediaBody = httpGet(client, variantUrl, responseTimeout);
            result.setLatencyMs(System.currentTimeMillis() - start - connectTime);

            // Step 5: Parse media playlist for segments
            List<String> segmentUrls = parseMediaPlaylist(mediaBody, variantUrl);

            // Step 6: Download segments
            int targetCount = Math.min(segmentCount, segmentUrls.size());
            long totalBytes = 0;
            int downloadedCount = 0;

            for (int i = 0; i < targetCount; i++) {
                try {
                    byte[] segData = httpGetBytes(client, segmentUrls.get(i), responseTimeout);
                    totalBytes += segData.length;
                    downloadedCount++;
                } catch (IOException | InterruptedException e) {
                    LOG.log(Level.FINE, "HLSSamplerExecutor: segment {0} failed: {1}",
                            new Object[]{i, e.getMessage()});
                }
            }

            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);

            String qualityLabel = selected.resolution != null ? selected.resolution
                    : (selected.bandwidth + "bps");
            result.setResponseBody("master: OK, variant: " + qualityLabel
                    + ", segments: " + downloadedCount + "/" + targetCount
                    + " downloaded, total bytes: " + totalBytes);

            if (downloadedCount == targetCount) {
                result.setStatusCode(200);
            } else if (downloadedCount > 0) {
                result.setStatusCode(206);
            } else {
                result.setFailureMessage("HLS: all segment downloads failed");
            }

        } catch (IOException | InterruptedException e) {
            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);
            result.setFailureMessage("HLS error: " + e.getMessage());
            LOG.log(Level.WARNING, "HLSSamplerExecutor: error for " + url, e);
        }
    }

    // =========================================================================
    // M3U8 parsing (package-visible for testing)
    // =========================================================================

    /**
     * Parses a master M3U8 playlist and extracts variant streams.
     *
     * @param content the playlist content
     * @return list of variants; empty if no {@code #EXT-X-STREAM-INF} lines found
     */
    static List<Variant> parseMasterPlaylist(String content) {
        List<Variant> variants = new ArrayList<>();
        String[] lines = content.split("\\r?\\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                String attrs = line.substring("#EXT-X-STREAM-INF:".length());
                long bandwidth = parseAttribute(attrs, "BANDWIDTH", 0L);
                String resolution = parseStringAttribute(attrs, "RESOLUTION");

                // Next non-empty, non-comment line is the URI
                for (int j = i + 1; j < lines.length; j++) {
                    String uri = lines[j].trim();
                    if (!uri.isEmpty() && !uri.startsWith("#")) {
                        variants.add(new Variant(uri, bandwidth, resolution));
                        break;
                    }
                }
            }
        }
        return variants;
    }

    /**
     * Parses a media M3U8 playlist and extracts segment URLs.
     *
     * @param content the media playlist content
     * @param baseUrl the base URL for resolving relative segment paths
     * @return list of absolute segment URLs
     */
    static List<String> parseMediaPlaylist(String content, String baseUrl) {
        List<String> segments = new ArrayList<>();
        String[] lines = content.split("\\r?\\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("#EXTINF:")) {
                // Next non-empty, non-comment line is the segment URI
                for (int j = i + 1; j < lines.length; j++) {
                    String uri = lines[j].trim();
                    if (!uri.isEmpty() && !uri.startsWith("#")) {
                        segments.add(resolveUrl(baseUrl, uri));
                        break;
                    }
                }
            }
        }
        return segments;
    }

    /**
     * Selects the best matching variant for the given quality preference.
     *
     * @param variants non-empty list of variants
     * @param quality  quality preference: "best", "worst", or a resolution like "720p"
     * @return selected variant
     */
    static Variant selectVariant(List<Variant> variants, String quality) {
        if (variants.size() == 1) {
            return variants.get(0);
        }

        if ("worst".equalsIgnoreCase(quality)) {
            return variants.stream()
                    .min((a, b) -> Long.compare(a.bandwidth, b.bandwidth))
                    .orElse(variants.get(0));
        }

        if ("best".equalsIgnoreCase(quality)) {
            return variants.stream()
                    .max((a, b) -> Long.compare(a.bandwidth, b.bandwidth))
                    .orElse(variants.get(0));
        }

        // Try to match resolution (e.g., "720p" matches "1280x720")
        String heightTarget = quality.replaceAll("[^0-9]", "");
        for (Variant v : variants) {
            if (v.resolution != null && v.resolution.contains("x")) {
                String height = v.resolution.split("x")[1];
                if (height.equals(heightTarget)) {
                    return v;
                }
            }
        }

        // Fallback to best
        return variants.stream()
                .max((a, b) -> Long.compare(a.bandwidth, b.bandwidth))
                .orElse(variants.get(0));
    }

    // =========================================================================
    // Variant record
    // =========================================================================

    /** A parsed HLS variant stream. Package-visible for testing. */
    static final class Variant {
        final String uri;
        final long bandwidth;
        final String resolution; // nullable

        Variant(String uri, long bandwidth, String resolution) {
            this.uri = uri;
            this.bandwidth = bandwidth;
            this.resolution = resolution;
        }
    }

    // =========================================================================
    // HTTP helpers
    // =========================================================================

    private static String httpGet(HttpClient client, String url, int timeoutMs)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }
        return response.body();
    }

    private static byte[] httpGetBytes(HttpClient client, String url, int timeoutMs)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .GET()
                .build();
        HttpResponse<byte[]> response = client.send(request,
                HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }
        return response.body();
    }

    private static int downloadSegments(HttpClient client, List<String> segmentUrls,
                                         int maxCount, int timeoutMs) {
        int target = Math.min(maxCount, segmentUrls.size());
        int downloaded = 0;
        for (int i = 0; i < target; i++) {
            try {
                httpGetBytes(client, segmentUrls.get(i), timeoutMs);
                downloaded++;
            } catch (IOException | InterruptedException e) {
                LOG.log(Level.FINE, "Segment download failed: {0}", e.getMessage());
            }
        }
        return downloaded;
    }

    // =========================================================================
    // Attribute parsing helpers
    // =========================================================================

    private static long parseAttribute(String attrs, String name, long defaultValue) {
        int idx = attrs.indexOf(name + "=");
        if (idx < 0) return defaultValue;
        int start = idx + name.length() + 1;
        int end = start;
        while (end < attrs.length() && Character.isDigit(attrs.charAt(end))) {
            end++;
        }
        if (end == start) return defaultValue;
        try {
            return Long.parseLong(attrs.substring(start, end));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String parseStringAttribute(String attrs, String name) {
        int idx = attrs.indexOf(name + "=");
        if (idx < 0) return null;
        int start = idx + name.length() + 1;
        if (start < attrs.length() && attrs.charAt(start) == '"') {
            int end = attrs.indexOf('"', start + 1);
            return end > start ? attrs.substring(start + 1, end) : null;
        }
        int end = start;
        while (end < attrs.length() && attrs.charAt(end) != ',' && attrs.charAt(end) != ' ') {
            end++;
        }
        return end > start ? attrs.substring(start, end) : null;
    }

    /**
     * Resolves a potentially relative URL against a base URL.
     */
    static String resolveUrl(String baseUrl, String relative) {
        if (relative.startsWith("http://") || relative.startsWith("https://")) {
            return relative;
        }
        int lastSlash = baseUrl.lastIndexOf('/');
        if (lastSlash >= 0) {
            return baseUrl.substring(0, lastSlash + 1) + relative;
        }
        return relative;
    }

    private static String resolve(String value, Map<String, String> variables) {
        if (value == null) return "";
        return VariableResolver.resolve(value, variables);
    }
}
