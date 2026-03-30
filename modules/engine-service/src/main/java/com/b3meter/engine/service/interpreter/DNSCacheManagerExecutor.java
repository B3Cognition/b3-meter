package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Processes a {@code DNSCacheManager} {@link PlanNode} to store custom DNS
 * resolver configuration in the VU variable map.
 *
 * <p>Reads the following JMX properties:
 * <ul>
 *   <li>{@code DNSCacheManager.servers} — collection of DNS server addresses</li>
 *   <li>{@code DNSCacheManager.clearEachIteration} — clear DNS cache each iteration</li>
 *   <li>{@code DNSCacheManager.isCustomResolver} — use custom DNS resolver</li>
 * </ul>
 *
 * <p>DNS server addresses are stored as {@code __dns_server_0}, {@code __dns_server_1},
 * etc. in the VU variable map for the HTTP sampler to use when resolving hostnames.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class DNSCacheManagerExecutor {

    private static final Logger LOG = Logger.getLogger(DNSCacheManagerExecutor.class.getName());

    /** Variable key prefix for DNS configuration. */
    private static final String DNS_PREFIX = "__dns_";

    private DNSCacheManagerExecutor() {}

    /**
     * Reads DNS cache manager properties from the plan node and stores them
     * in the VU variable map.
     *
     * @param node      the DNSCacheManager node; must not be {@code null}
     * @param variables mutable VU variable map
     */
    public static void configure(PlanNode node, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        boolean clearEachIteration = node.getBoolProp("DNSCacheManager.clearEachIteration", false);
        boolean isCustomResolver = node.getBoolProp("DNSCacheManager.isCustomResolver", false);

        variables.put(DNS_PREFIX + "enabled", "true");
        variables.put(DNS_PREFIX + "clearEachIteration", String.valueOf(clearEachIteration));
        variables.put(DNS_PREFIX + "isCustomResolver", String.valueOf(isCustomResolver));

        // Parse DNS server addresses from collection property
        List<Object> servers = node.getCollectionProp("DNSCacheManager.servers");
        variables.put(DNS_PREFIX + "server_count", String.valueOf(servers.size()));
        for (int i = 0; i < servers.size(); i++) {
            String server = String.valueOf(servers.get(i));
            variables.put(DNS_PREFIX + "server_" + i, server);
        }

        LOG.log(Level.FINE,
                "DNSCacheManagerExecutor [{0}]: configured (custom={1}, servers={2}, clearEachIteration={3})",
                new Object[]{node.getTestName(), isCustomResolver, servers.size(), clearEachIteration});
    }
}
