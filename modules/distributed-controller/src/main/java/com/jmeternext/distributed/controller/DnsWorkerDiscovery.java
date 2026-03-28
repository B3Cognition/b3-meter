package com.jmeternext.distributed.controller;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Auto-discovers jMeter Next worker nodes via DNS SRV record lookup.
 *
 * <p>Designed for Kubernetes headless Services where each worker pod is
 * registered as an SRV record under the service DNS name. This enables
 * zero-configuration worker discovery in containerized deployments.
 *
 * <h2>Discovery Strategy</h2>
 * <ol>
 *   <li>Query DNS SRV records at {@code _grpc._tcp.<serviceName>}</li>
 *   <li>Parse each SRV record to extract target host and port</li>
 *   <li>If SRV lookup fails, fall back to A record lookup using the
 *       configured default port</li>
 * </ol>
 *
 * <h2>Periodic Refresh</h2>
 * Workers can be added or removed from the Kubernetes cluster at any time.
 * Call {@link #startPeriodicRefresh(Duration, Consumer)} to re-query DNS
 * at a fixed interval and receive callbacks when the worker set changes.
 *
 * <p>Only JDK types are used (JNDI DNS provider — {@code javax.naming}).
 */
public class DnsWorkerDiscovery {

    private static final Logger LOG = Logger.getLogger(DnsWorkerDiscovery.class.getName());

    private final String serviceName;
    private final int defaultPort;

    private volatile ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> refreshTask;
    private volatile List<WorkerEndpoint> lastKnownEndpoints = List.of();

    /**
     * Creates a DNS worker discovery instance.
     *
     * @param serviceName the Kubernetes headless service DNS name,
     *                    e.g., {@code "jmeter-next-worker.default.svc.cluster.local"}
     * @param defaultPort the default gRPC port to use when SRV records are
     *                    unavailable and A record fallback is used
     * @throws NullPointerException     if serviceName is null
     * @throws IllegalArgumentException if defaultPort is not in [1, 65535]
     */
    public DnsWorkerDiscovery(String serviceName, int defaultPort) {
        this.serviceName = Objects.requireNonNull(serviceName, "serviceName must not be null");
        if (defaultPort < 1 || defaultPort > 65535) {
            throw new IllegalArgumentException(
                    "defaultPort must be in [1, 65535], got: " + defaultPort);
        }
        this.defaultPort = defaultPort;
    }

    /**
     * Performs a one-shot DNS lookup and returns the discovered worker endpoints.
     *
     * <p>First attempts SRV record lookup. If that fails (e.g., non-Kubernetes
     * environment), falls back to A record lookup using the configured default port.
     *
     * @return an unmodifiable list of discovered endpoints; may be empty if DNS
     *         resolution fails entirely
     */
    public List<WorkerEndpoint> discover() {
        List<WorkerEndpoint> endpoints = discoverViaSrv();
        if (endpoints.isEmpty()) {
            LOG.fine("SRV lookup returned no results, falling back to A record lookup");
            endpoints = discoverViaARecord();
        }
        lastKnownEndpoints = List.copyOf(endpoints);
        return lastKnownEndpoints;
    }

    /**
     * Starts periodic DNS refresh on a background thread.
     *
     * <p>Re-queries DNS every {@code interval}. When the set of discovered
     * endpoints changes (new workers added or existing workers removed), the
     * {@code onChange} callback is invoked with the updated endpoint list.
     *
     * <p>Only one refresh loop can be active at a time. Calling this method
     * again stops the previous loop before starting a new one.
     *
     * @param interval the refresh interval; must be positive
     * @param onChange callback invoked when the worker set changes; never
     *                 called with {@code null}
     * @throws NullPointerException     if interval or onChange is null
     * @throws IllegalArgumentException if interval is zero or negative
     */
    public void startPeriodicRefresh(Duration interval, Consumer<List<WorkerEndpoint>> onChange) {
        Objects.requireNonNull(interval, "interval must not be null");
        Objects.requireNonNull(onChange, "onChange must not be null");
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("interval must be positive, got: " + interval);
        }

        stop(); // stop any existing refresh

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dns-worker-discovery");
            t.setDaemon(true);
            return t;
        });

        long intervalMs = interval.toMillis();
        refreshTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                List<WorkerEndpoint> previous = lastKnownEndpoints;
                List<WorkerEndpoint> current = discover();
                if (!current.equals(previous)) {
                    onChange.accept(current);
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "DNS refresh failed", e);
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);

        LOG.info("Started periodic DNS refresh every " + interval + " for " + serviceName);
    }

    /**
     * Stops the periodic refresh loop if running.
     *
     * <p>Safe to call multiple times or when no refresh is active.
     */
    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
            refreshTask = null;
        }
        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
        }
    }

    /**
     * Returns the last known set of worker endpoints from the most recent
     * discovery or refresh.
     *
     * @return unmodifiable list of endpoints; empty if discovery has not
     *         been performed yet
     */
    public List<WorkerEndpoint> getLastKnownEndpoints() {
        return lastKnownEndpoints;
    }

    // -------------------------------------------------------------------------
    // Internal DNS resolution
    // -------------------------------------------------------------------------

    private List<WorkerEndpoint> discoverViaSrv() {
        String srvName = "_grpc._tcp." + serviceName;
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            DirContext ctx = new InitialDirContext(env);
            try {
                Attributes attrs = ctx.getAttributes(srvName, new String[]{"SRV"});
                Attribute srvAttr = attrs.get("SRV");
                if (srvAttr == null) {
                    return List.of();
                }

                List<WorkerEndpoint> endpoints = new ArrayList<>();
                NamingEnumeration<?> records = srvAttr.getAll();
                while (records.hasMore()) {
                    String record = records.next().toString();
                    WorkerEndpoint endpoint = parseSrvRecord(record);
                    if (endpoint != null) {
                        endpoints.add(endpoint);
                    }
                }
                records.close();

                LOG.info("SRV lookup for " + srvName + " found " + endpoints.size() + " workers");
                return endpoints;
            } finally {
                ctx.close();
            }
        } catch (NamingException e) {
            LOG.log(Level.FINE, "SRV lookup failed for " + srvName, e);
            return List.of();
        }
    }

    private List<WorkerEndpoint> discoverViaARecord() {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            DirContext ctx = new InitialDirContext(env);
            try {
                Attributes attrs = ctx.getAttributes(serviceName, new String[]{"A"});
                Attribute aAttr = attrs.get("A");
                if (aAttr == null) {
                    return List.of();
                }

                List<WorkerEndpoint> endpoints = new ArrayList<>();
                NamingEnumeration<?> records = aAttr.getAll();
                while (records.hasMore()) {
                    String ip = records.next().toString().trim();
                    if (!ip.isEmpty()) {
                        endpoints.add(new WorkerEndpoint(ip, defaultPort));
                    }
                }
                records.close();

                LOG.info("A record lookup for " + serviceName + " found " +
                        endpoints.size() + " workers (using default port " + defaultPort + ")");
                return endpoints;
            } finally {
                ctx.close();
            }
        } catch (NamingException e) {
            LOG.log(Level.FINE, "A record lookup failed for " + serviceName, e);
            return List.of();
        }
    }

    /**
     * Parses an SRV record string in the format {@code "priority weight port target"}.
     *
     * @param record the raw SRV record string
     * @return a WorkerEndpoint, or {@code null} if parsing fails
     */
    static WorkerEndpoint parseSrvRecord(String record) {
        // SRV format: priority weight port target
        String[] parts = record.trim().split("\\s+");
        if (parts.length < 4) {
            LOG.warning("Malformed SRV record: " + record);
            return null;
        }
        try {
            int port = Integer.parseInt(parts[2]);
            String target = parts[3];
            // Remove trailing dot from FQDN if present
            if (target.endsWith(".")) {
                target = target.substring(0, target.length() - 1);
            }
            return new WorkerEndpoint(target, port);
        } catch (NumberFormatException e) {
            LOG.warning("Failed to parse port from SRV record: " + record);
            return null;
        }
    }

    /**
     * Represents a discovered worker node endpoint.
     *
     * @param host the worker hostname or IP address
     * @param port the worker gRPC port
     */
    public record WorkerEndpoint(String host, int port) {

        public WorkerEndpoint {
            Objects.requireNonNull(host, "host must not be null");
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("port must be in [1, 65535], got: " + port);
            }
        }

        @Override
        public String toString() {
            return host + ":" + port;
        }
    }
}
