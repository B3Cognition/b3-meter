package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code BackendListener} node.
 *
 * <p>The Backend Listener collects sample metrics and periodically pushes them
 * to an external time-series backend. Two built-in implementations are supported:
 * <ul>
 *   <li><b>Graphite</b> — sends metrics via UDP in Graphite plaintext protocol</li>
 *   <li><b>InfluxDB</b> — sends metrics via HTTP POST using InfluxDB line protocol</li>
 * </ul>
 *
 * <p>JMeter properties:
 * <ul>
 *   <li>{@code className} — implementation: "graphite" or "influxdb"</li>
 *   <li>{@code graphiteHost}, {@code graphitePort} — Graphite endpoint</li>
 *   <li>{@code influxdbUrl} — InfluxDB write endpoint URL</li>
 *   <li>{@code influxdbToken} — InfluxDB auth token</li>
 *   <li>{@code influxdbBucket} — InfluxDB bucket</li>
 *   <li>{@code influxdbOrg} — InfluxDB organization</li>
 *   <li>{@code metricPrefix} — prefix for metric names (default: "jmeter")</li>
 *   <li>{@code flushIntervalMs} — flush interval in ms (default: 5000)</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class BackendListenerExecutor {

    private static final Logger LOG = Logger.getLogger(BackendListenerExecutor.class.getName());

    /** Accumulated metrics per label, flushed periodically. */
    private static final ConcurrentHashMap<String, MetricsAccumulator> ACCUMULATORS =
            new ConcurrentHashMap<>();

    /** Active flush schedulers keyed by listener test name. */
    private static final ConcurrentHashMap<String, ScheduledExecutorService> SCHEDULERS =
            new ConcurrentHashMap<>();

    private BackendListenerExecutor() {}

    /**
     * Initializes the backend listener — sets up the periodic flush scheduler.
     * Should be called once at plan start for each BackendListener node.
     *
     * @param node the listener node; must not be {@code null}
     */
    public static void start(PlanNode node) {
        Objects.requireNonNull(node, "node must not be null");
        String key = node.getTestName();

        long flushIntervalMs = parseLong(
                node.getStringProp("flushIntervalMs", "5000"), 5000L);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BackendListener-" + key);
            t.setDaemon(true);
            return t;
        });

        SCHEDULERS.put(key, scheduler);
        scheduler.scheduleAtFixedRate(
                () -> flush(node),
                flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);

        LOG.log(Level.INFO,
                "BackendListener [{0}]: started (flush every {1}ms)",
                new Object[]{key, flushIntervalMs});
    }

    /**
     * Records a sample result for later flushing to the backend.
     *
     * @param node   the listener node
     * @param result the sample result to record
     */
    public static void recordSample(PlanNode node, SampleResult result) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(result, "result must not be null");

        String key = node.getTestName();
        MetricsAccumulator acc = ACCUMULATORS.computeIfAbsent(key,
                k -> new MetricsAccumulator());
        acc.add(result);
    }

    /**
     * Stops the backend listener — flushes remaining metrics and shuts down the scheduler.
     *
     * @param node the listener node
     */
    public static void stop(PlanNode node) {
        Objects.requireNonNull(node, "node must not be null");
        String key = node.getTestName();

        // Final flush
        flush(node);

        ScheduledExecutorService scheduler = SCHEDULERS.remove(key);
        if (scheduler != null) {
            scheduler.shutdown();
        }
        ACCUMULATORS.remove(key);

        LOG.log(Level.INFO, "BackendListener [{0}]: stopped", key);
    }

    /**
     * Flushes accumulated metrics to the configured backend.
     */
    static void flush(PlanNode node) {
        String key = node.getTestName();
        MetricsAccumulator acc = ACCUMULATORS.get(key);
        if (acc == null) return;

        MetricsSnapshot snapshot = acc.snapshot();
        if (snapshot.count == 0) return;

        String className = node.getStringProp("className", "graphite");
        String prefix = node.getStringProp("metricPrefix", "jmeter");

        try {
            switch (className.toLowerCase()) {
                case "graphite" -> sendGraphite(node, prefix, snapshot);
                case "influxdb" -> sendInfluxDB(node, prefix, snapshot);
                default -> LOG.log(Level.WARNING,
                        "BackendListener [{0}]: unknown className [{1}]",
                        new Object[]{key, className});
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "BackendListener [{0}]: flush failed: {1}",
                    new Object[]{key, e.getMessage()});
        }
    }

    // -------------------------------------------------------------------------
    // Graphite (UDP plaintext protocol)
    // -------------------------------------------------------------------------

    private static void sendGraphite(PlanNode node, String prefix, MetricsSnapshot snapshot)
            throws IOException {
        String host = node.getStringProp("graphiteHost", "localhost");
        int port = node.getIntProp("graphitePort", 2003);
        if (port == 0) port = 2003;

        long timestamp = Instant.now().getEpochSecond();
        StringBuilder sb = new StringBuilder();
        sb.append(prefix).append(".count ").append(snapshot.count).append(' ').append(timestamp).append('\n');
        sb.append(prefix).append(".ok.count ").append(snapshot.okCount).append(' ').append(timestamp).append('\n');
        sb.append(prefix).append(".ko.count ").append(snapshot.koCount).append(' ').append(timestamp).append('\n');
        sb.append(prefix).append(".avg ").append(snapshot.avgMs).append(' ').append(timestamp).append('\n');
        sb.append(prefix).append(".min ").append(snapshot.minMs).append(' ').append(timestamp).append('\n');
        sb.append(prefix).append(".max ").append(snapshot.maxMs).append(' ').append(timestamp).append('\n');

        byte[] data = sb.toString().getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress addr = InetAddress.getByName(host);
            DatagramPacket packet = new DatagramPacket(data, data.length, addr, port);
            socket.send(packet);
        }

        LOG.log(Level.FINE,
                "BackendListener: sent {0} metrics to Graphite {1}:{2}",
                new Object[]{snapshot.count, host, port});
    }

    // -------------------------------------------------------------------------
    // InfluxDB (HTTP line protocol)
    // -------------------------------------------------------------------------

    private static void sendInfluxDB(PlanNode node, String prefix, MetricsSnapshot snapshot)
            throws IOException, InterruptedException {
        String url = node.getStringProp("influxdbUrl", "http://localhost:8086");
        String token = node.getStringProp("influxdbToken", "");
        String bucket = node.getStringProp("influxdbBucket", "jmeter");
        String org = node.getStringProp("influxdbOrg", "");

        // InfluxDB line protocol
        long timestampNs = Instant.now().toEpochMilli() * 1_000_000L;
        String lineProtocol = String.format(
                "%s,type=aggregate count=%di,ok=%di,ko=%di,avg=%.2f,min=%di,max=%di %d",
                prefix, snapshot.count, snapshot.okCount, snapshot.koCount,
                snapshot.avgMs, snapshot.minMs, snapshot.maxMs, timestampNs);

        String writeUrl = url + "/api/v2/write?bucket=" + bucket
                + (org.isEmpty() ? "" : "&org=" + org);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(writeUrl))
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(lineProtocol));

        if (!token.isEmpty()) {
            reqBuilder.header("Authorization", "Token " + token);
        }

        HttpResponse<String> response = client.send(
                reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 300) {
            LOG.log(Level.WARNING,
                    "BackendListener: InfluxDB write returned status {0}: {1}",
                    new Object[]{response.statusCode(), response.body()});
        } else {
            LOG.log(Level.FINE,
                    "BackendListener: sent {0} samples to InfluxDB", snapshot.count);
        }
    }

    // -------------------------------------------------------------------------
    // Metrics accumulator
    // -------------------------------------------------------------------------

    /**
     * Thread-safe accumulator that collects sample metrics between flush intervals.
     */
    static final class MetricsAccumulator {
        private final AtomicLong count = new AtomicLong();
        private final AtomicLong okCount = new AtomicLong();
        private final AtomicLong koCount = new AtomicLong();
        private final AtomicLong totalMs = new AtomicLong();
        private final AtomicLong minMs = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxMs = new AtomicLong(0);

        void add(SampleResult result) {
            count.incrementAndGet();
            if (result.isSuccess()) {
                okCount.incrementAndGet();
            } else {
                koCount.incrementAndGet();
            }
            long elapsed = result.getTotalTimeMs();
            totalMs.addAndGet(elapsed);
            minMs.updateAndGet(prev -> Math.min(prev, elapsed));
            maxMs.updateAndGet(prev -> Math.max(prev, elapsed));
        }

        /**
         * Takes a snapshot of accumulated metrics and resets counters.
         */
        MetricsSnapshot snapshot() {
            long c = count.getAndSet(0);
            long ok = okCount.getAndSet(0);
            long ko = koCount.getAndSet(0);
            long total = totalMs.getAndSet(0);
            long min = minMs.getAndSet(Long.MAX_VALUE);
            long max = maxMs.getAndSet(0);

            double avg = c > 0 ? (double) total / c : 0.0;
            if (c == 0) { min = 0; max = 0; }

            return new MetricsSnapshot(c, ok, ko, avg, min, max);
        }
    }

    /**
     * Immutable snapshot of accumulated metrics.
     */
    record MetricsSnapshot(long count, long okCount, long koCount,
                           double avgMs, long minMs, long maxMs) {}

    /**
     * Builds an InfluxDB line protocol string from a snapshot. Visible for testing.
     */
    static String buildInfluxLine(String prefix, MetricsSnapshot snapshot) {
        long timestampNs = Instant.now().toEpochMilli() * 1_000_000L;
        return String.format(
                "%s,type=aggregate count=%di,ok=%di,ko=%di,avg=%.2f,min=%di,max=%di %d",
                prefix, snapshot.count, snapshot.okCount, snapshot.koCount,
                snapshot.avgMs, snapshot.minMs, snapshot.maxMs, timestampNs);
    }

    /**
     * Builds a Graphite plaintext protocol payload from a snapshot. Visible for testing.
     */
    static String buildGraphitePayload(String prefix, MetricsSnapshot snapshot) {
        long timestamp = Instant.now().getEpochSecond();
        return prefix + ".count " + snapshot.count + " " + timestamp + "\n"
                + prefix + ".ok.count " + snapshot.okCount + " " + timestamp + "\n"
                + prefix + ".ko.count " + snapshot.koCount + " " + timestamp + "\n"
                + prefix + ".avg " + snapshot.avgMs + " " + timestamp + "\n"
                + prefix + ".min " + snapshot.minMs + " " + timestamp + "\n"
                + prefix + ".max " + snapshot.maxMs + " " + timestamp + "\n";
    }

    /** Clears all accumulators and schedulers. For test isolation. */
    static void reset() {
        SCHEDULERS.values().forEach(ScheduledExecutorService::shutdownNow);
        SCHEDULERS.clear();
        ACCUMULATORS.clear();
    }

    private static long parseLong(String value, long defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
