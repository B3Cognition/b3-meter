package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Executes a {@code DASHSampler} {@link PlanNode}.
 *
 * <p>Reads the following sampler properties:
 * <ul>
 *   <li>{@code DASHSampler.url} — URL to MPD (Media Presentation Description) manifest</li>
 *   <li>{@code DASHSampler.quality} — preferred quality, default "best"</li>
 *   <li>{@code DASHSampler.segmentCount} — number of segments to download, default 3</li>
 *   <li>{@code DASHSampler.connectTimeout} — ms, default 5000</li>
 * </ul>
 *
 * <p>Implementation flow:
 * <ol>
 *   <li>GET MPD manifest — parse XML for {@code <AdaptationSet>} and {@code <Representation>}</li>
 *   <li>Select representation by quality (bandwidth attribute)</li>
 *   <li>Construct segment URLs from {@code <SegmentTemplate>}</li>
 *   <li>GET initialization segment + N media segments</li>
 * </ol>
 *
 * <p>Uses {@code javax.xml.parsers} for MPD parsing (already in JDK).
 * Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class DASHSamplerExecutor {

    private static final Logger LOG = Logger.getLogger(DASHSamplerExecutor.class.getName());

    private static final int DEFAULT_SEGMENT_COUNT = 3;
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;

    private DASHSamplerExecutor() {
        // utility class — not instantiable
    }

    /**
     * Executes the DASH operation described by {@code node}.
     *
     * @param node      the DASHSampler node; must not be {@code null}
     * @param result    the sample result to populate; must not be {@code null}
     * @param variables current VU variable scope for {@code ${varName}} substitution
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String url = resolve(node.getStringProp("DASHSampler.url", ""), variables);
        String quality = resolve(node.getStringProp("DASHSampler.quality", "best"), variables);
        int segmentCount = node.getIntProp("DASHSampler.segmentCount", DEFAULT_SEGMENT_COUNT);
        int connectTimeout = node.getIntProp("DASHSampler.connectTimeout", DEFAULT_CONNECT_TIMEOUT_MS);

        if (url.isBlank()) {
            result.setFailureMessage("DASHSampler.url is empty");
            return;
        }

        LOG.log(Level.FINE, "DASHSamplerExecutor: fetching MPD from {0}", url);

        long start = System.currentTimeMillis();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeout))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        try {
            // Step 1: GET MPD manifest
            String mpdBody = httpGet(client, url, connectTimeout);
            long connectTime = System.currentTimeMillis() - start;
            result.setConnectTimeMs(connectTime);

            // Step 2: Parse MPD XML
            List<Representation> representations = parseMpd(mpdBody);

            if (representations.isEmpty()) {
                result.setTotalTimeMs(System.currentTimeMillis() - start);
                result.setFailureMessage("DASH: no Representation elements found in MPD");
                return;
            }

            // Step 3: Select representation by quality
            Representation selected = selectRepresentation(representations, quality);
            result.setLatencyMs(System.currentTimeMillis() - start - connectTime);

            // Step 4: Build segment URLs
            List<String> segmentUrls = buildSegmentUrls(selected, url, segmentCount);

            // Step 5: Download init segment + media segments
            long totalBytes = 0;
            int downloadedCount = 0;
            boolean initDownloaded = false;

            // Download init segment if present
            if (selected.initUrl != null && !selected.initUrl.isEmpty()) {
                String initAbsUrl = resolveUrl(url, selected.initUrl);
                try {
                    byte[] initData = httpGetBytes(client, initAbsUrl, connectTimeout);
                    totalBytes += initData.length;
                    initDownloaded = true;
                } catch (IOException | InterruptedException e) {
                    LOG.log(Level.FINE, "DASHSamplerExecutor: init segment failed: {0}", e.getMessage());
                }
            }

            // Download media segments
            int targetCount = Math.min(segmentCount, segmentUrls.size());
            for (int i = 0; i < targetCount; i++) {
                try {
                    byte[] segData = httpGetBytes(client, segmentUrls.get(i), connectTimeout);
                    totalBytes += segData.length;
                    downloadedCount++;
                } catch (IOException | InterruptedException e) {
                    LOG.log(Level.FINE, "DASHSamplerExecutor: segment {0} failed: {1}",
                            new Object[]{i, e.getMessage()});
                }
            }

            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);

            String qualityLabel = selected.width > 0
                    ? (selected.width + "x" + selected.height)
                    : (selected.bandwidth + "bps");
            result.setResponseBody("mpd: OK, representation: " + qualityLabel
                    + " (id=" + selected.id + ")"
                    + ", init: " + (initDownloaded ? "OK" : (selected.initUrl != null ? "FAIL" : "N/A"))
                    + ", segments: " + downloadedCount + "/" + targetCount
                    + " downloaded, total bytes: " + totalBytes);

            if (downloadedCount == targetCount) {
                result.setStatusCode(200);
            } else if (downloadedCount > 0) {
                result.setStatusCode(206);
            } else {
                result.setFailureMessage("DASH: all segment downloads failed");
            }

        } catch (IOException | InterruptedException e) {
            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);
            result.setFailureMessage("DASH error: " + e.getMessage());
            LOG.log(Level.WARNING, "DASHSamplerExecutor: error for " + url, e);
        }
    }

    // =========================================================================
    // MPD parsing (package-visible for testing)
    // =========================================================================

    /**
     * Parses an MPD XML manifest and extracts Representation elements.
     *
     * @param mpdXml the MPD XML content
     * @return list of representations; empty if none found
     * @throws IOException if XML parsing fails
     */
    static List<Representation> parseMpd(String mpdXml) throws IOException {
        List<Representation> result = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            // Security: disable external entities
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(mpdXml.getBytes(StandardCharsets.UTF_8)));

            NodeList adaptationSets = doc.getElementsByTagName("AdaptationSet");
            for (int i = 0; i < adaptationSets.getLength(); i++) {
                Element as = (Element) adaptationSets.item(i);

                // Read SegmentTemplate from AdaptationSet level
                String asMedia = null;
                String asInit = null;
                int asStartNumber = 1;

                // Only look at direct-child SegmentTemplate elements of this AdaptationSet
                org.w3c.dom.Node child = as.getFirstChild();
                while (child != null) {
                    if (child instanceof Element && "SegmentTemplate".equals(child.getNodeName())) {
                        Element tmpl = (Element) child;
                        asMedia = attrOrNull(tmpl, "media");
                        asInit = attrOrNull(tmpl, "initialization");
                        String startNum = tmpl.getAttribute("startNumber");
                        if (!startNum.isEmpty()) {
                            asStartNumber = Integer.parseInt(startNum);
                        }
                        break;
                    }
                    child = child.getNextSibling();
                }

                // Find direct-child Representation elements
                org.w3c.dom.Node repChild = as.getFirstChild();
                while (repChild != null) {
                    if (repChild instanceof Element && "Representation".equals(repChild.getNodeName())) {
                        Element rep = (Element) repChild;
                        String id = rep.getAttribute("id");
                        long bandwidth = parseLongAttr(rep, "bandwidth", 0);
                        int width = parseIntAttr(rep, "width", 0);
                        int height = parseIntAttr(rep, "height", 0);

                        // Check for Representation-level SegmentTemplate
                        String media = asMedia;
                        String init = asInit;
                        int startNumber = asStartNumber;

                        org.w3c.dom.Node repTmplChild = rep.getFirstChild();
                        while (repTmplChild != null) {
                            if (repTmplChild instanceof Element
                                    && "SegmentTemplate".equals(repTmplChild.getNodeName())) {
                                Element tmpl = (Element) repTmplChild;
                                String m = attrOrNull(tmpl, "media");
                                if (m != null) media = m;
                                String ini = attrOrNull(tmpl, "initialization");
                                if (ini != null) init = ini;
                                String sn = tmpl.getAttribute("startNumber");
                                if (!sn.isEmpty()) startNumber = Integer.parseInt(sn);
                                break;
                            }
                            repTmplChild = repTmplChild.getNextSibling();
                        }

                        // Substitute $RepresentationID$ in template strings
                        if (media != null) {
                            media = media.replace("$RepresentationID$", id);
                        }
                        if (init != null) {
                            init = init.replace("$RepresentationID$", id);
                        }

                        result.add(new Representation(id, bandwidth, width, height,
                                media, init, startNumber));
                    }
                    repChild = repChild.getNextSibling();
                }
            }
        } catch (javax.xml.parsers.ParserConfigurationException | org.xml.sax.SAXException e) {
            throw new IOException("Failed to parse MPD XML: " + e.getMessage(), e);
        }
        return result;
    }

    /**
     * Selects the best matching representation for the given quality preference.
     *
     * @param representations non-empty list
     * @param quality         "best", "worst", or resolution like "720p"
     * @return selected representation
     */
    static Representation selectRepresentation(List<Representation> representations, String quality) {
        if (representations.size() == 1) {
            return representations.get(0);
        }

        if ("worst".equalsIgnoreCase(quality)) {
            return representations.stream()
                    .min((a, b) -> Long.compare(a.bandwidth, b.bandwidth))
                    .orElse(representations.get(0));
        }

        if ("best".equalsIgnoreCase(quality)) {
            return representations.stream()
                    .max((a, b) -> Long.compare(a.bandwidth, b.bandwidth))
                    .orElse(representations.get(0));
        }

        // Try to match height (e.g., "720p" matches height=720)
        String heightTarget = quality.replaceAll("[^0-9]", "");
        for (Representation r : representations) {
            if (r.height > 0 && String.valueOf(r.height).equals(heightTarget)) {
                return r;
            }
        }

        // Fallback to best
        return representations.stream()
                .max((a, b) -> Long.compare(a.bandwidth, b.bandwidth))
                .orElse(representations.get(0));
    }

    /**
     * Builds segment URLs from a representation's SegmentTemplate.
     *
     * @param rep          the representation
     * @param baseUrl      the MPD manifest URL for resolving relative paths
     * @param segmentCount number of segments to generate
     * @return list of absolute segment URLs
     */
    static List<String> buildSegmentUrls(Representation rep, String baseUrl, int segmentCount) {
        List<String> urls = new ArrayList<>();
        if (rep.mediaTemplate == null) {
            return urls;
        }

        for (int i = 0; i < segmentCount; i++) {
            int number = rep.startNumber + i;
            String segPath = rep.mediaTemplate.replace("$Number$", String.valueOf(number));
            urls.add(resolveUrl(baseUrl, segPath));
        }
        return urls;
    }

    // =========================================================================
    // Representation record
    // =========================================================================

    /** A parsed DASH representation. Package-visible for testing. */
    static final class Representation {
        final String id;
        final long bandwidth;
        final int width;
        final int height;
        final String mediaTemplate; // nullable
        final String initUrl;       // nullable
        final int startNumber;

        Representation(String id, long bandwidth, int width, int height,
                       String mediaTemplate, String initUrl, int startNumber) {
            this.id = id;
            this.bandwidth = bandwidth;
            this.width = width;
            this.height = height;
            this.mediaTemplate = mediaTemplate;
            this.initUrl = initUrl;
            this.startNumber = startNumber;
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

    // =========================================================================
    // Helpers
    // =========================================================================

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

    private static String attrOrNull(Element el, String attr) {
        String val = el.getAttribute(attr);
        return (val != null && !val.isEmpty()) ? val : null;
    }

    private static long parseLongAttr(Element el, String attr, long defaultValue) {
        String val = el.getAttribute(attr);
        if (val == null || val.isEmpty()) return defaultValue;
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static int parseIntAttr(Element el, String attr, int defaultValue) {
        String val = el.getAttribute(attr);
        if (val == null || val.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String resolve(String value, Map<String, String> variables) {
        if (value == null) return "";
        return VariableResolver.resolve(value, variables);
    }
}
