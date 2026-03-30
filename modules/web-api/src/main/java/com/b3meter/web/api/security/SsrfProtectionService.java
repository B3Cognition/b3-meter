package com.jmeternext.web.api.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Guards outbound HTTP calls against Server-Side Request Forgery (SSRF) by
 * validating target URLs before the application opens a connection.
 *
 * <h2>Default blocked ranges</h2>
 * <ul>
 *   <li>{@code 10.0.0.0/8}       — RFC-1918 private range A</li>
 *   <li>{@code 172.16.0.0/12}    — RFC-1918 private range B</li>
 *   <li>{@code 192.168.0.0/16}   — RFC-1918 private range C</li>
 *   <li>{@code 169.254.0.0/16}   — link-local / cloud metadata endpoints
 *       (e.g. AWS {@code 169.254.169.254})</li>
 *   <li>{@code 127.0.0.0/8}      — loopback</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>Additional CIDRs can be supplied via the
 * {@code jmeter.security.ssrf.blocked-cidrs} property as a comma-separated
 * list:
 * <pre>
 * jmeter.security.ssrf.blocked-cidrs=10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,169.254.0.0/16,127.0.0.0/8
 * </pre>
 *
 * <p>When the property is set it <em>replaces</em> the default list, so
 * callers must include all desired ranges explicitly.
 */
@Service
public class SsrfProtectionService {

    private static final Logger log = LoggerFactory.getLogger(SsrfProtectionService.class);

    /** CIDRs that are blocked by default when no property is configured. */
    private static final List<String> DEFAULT_BLOCKED_CIDRS = List.of(
            "10.0.0.0/8",
            "172.16.0.0/12",
            "192.168.0.0/16",
            "169.254.0.0/16",
            "127.0.0.0/8"
    );

    private final List<CidrBlock> blockedRanges;

    /**
     * Constructs the service, parsing the configured (or default) blocked CIDRs.
     *
     * @param configuredCidrs comma-separated CIDR list from application properties;
     *                        if blank the {@link #DEFAULT_BLOCKED_CIDRS} are used
     */
    public SsrfProtectionService(
            @Value("${jmeter.security.ssrf.blocked-cidrs:}") String configuredCidrs) {

        List<String> rawCidrs = configuredCidrs == null || configuredCidrs.isBlank()
                ? DEFAULT_BLOCKED_CIDRS
                : List.of(configuredCidrs.split(","));

        List<CidrBlock> parsed = new ArrayList<>(rawCidrs.size());
        for (String cidr : rawCidrs) {
            String trimmed = cidr.strip();
            if (!trimmed.isEmpty()) {
                try {
                    parsed.add(CidrBlock.parse(trimmed));
                } catch (IllegalArgumentException e) {
                    log.warn("Ignoring unparseable CIDR '{}': {}", trimmed, e.getMessage());
                }
            }
        }
        this.blockedRanges = List.copyOf(parsed);
        log.info("SSRF protection active — {} blocked CIDR(s)", this.blockedRanges.size());
    }

    /**
     * Returns {@code true} when the supplied host resolves to an IP address
     * that falls inside one of the configured blocked CIDR ranges.
     *
     * <p>DNS resolution is performed eagerly; if the host cannot be resolved
     * the method returns {@code true} (fail-closed) to err on the side of
     * safety.
     *
     * @param host hostname or dotted-decimal IPv4 address to validate
     * @return {@code true} if the host is blocked, {@code false} if it is allowed
     */
    public boolean isBlocked(String host) {
        if (host == null || host.isBlank()) {
            return true;
        }

        InetAddress address;
        try {
            address = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            log.warn("SSRF check: could not resolve host '{}' — blocking as a precaution", host);
            return true;
        }

        byte[] addr = address.getAddress();
        if (addr.length != 4) {
            // IPv6 — not yet supported; block to be safe
            log.warn("SSRF check: IPv6 address '{}' not supported — blocking", address.getHostAddress());
            return true;
        }

        long ip = toUnsignedLong(addr);
        for (CidrBlock block : blockedRanges) {
            if (block.contains(ip)) {
                log.debug("SSRF check: host '{}' ({}) is in blocked range {}", host,
                        address.getHostAddress(), block);
                return true;
            }
        }
        return false;
    }

    /**
     * Convenience inverse of {@link #isBlocked(String)}.
     *
     * @param host hostname or IP to check
     * @return {@code true} if the host is allowed (not in any blocked range)
     */
    public boolean isAllowed(String host) {
        return !isBlocked(host);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /** Converts a 4-byte IPv4 address array to an unsigned 32-bit long. */
    private static long toUnsignedLong(byte[] addr) {
        return ((addr[0] & 0xFFL) << 24)
             | ((addr[1] & 0xFFL) << 16)
             | ((addr[2] & 0xFFL) <<  8)
             |  (addr[3] & 0xFFL);
    }

    // -------------------------------------------------------------------------
    // Value type: CIDR block
    // -------------------------------------------------------------------------

    /**
     * Immutable representation of an IPv4 CIDR block, e.g. {@code 10.0.0.0/8}.
     */
    static final class CidrBlock {

        private final long networkAddress;
        private final long subnetMask;
        private final String notation;

        private CidrBlock(long networkAddress, long subnetMask, String notation) {
            this.networkAddress = networkAddress;
            this.subnetMask     = subnetMask;
            this.notation       = notation;
        }

        /**
         * Parses a CIDR notation string such as {@code "192.168.0.0/16"}.
         *
         * @param cidr CIDR notation; prefix length must be 0–32
         * @return parsed {@link CidrBlock}
         * @throws IllegalArgumentException if the input cannot be parsed
         */
        static CidrBlock parse(String cidr) {
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Expected CIDR notation (e.g. 10.0.0.0/8): " + cidr);
            }
            int prefixLength;
            try {
                prefixLength = Integer.parseInt(parts[1].strip());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid prefix length in: " + cidr, e);
            }
            if (prefixLength < 0 || prefixLength > 32) {
                throw new IllegalArgumentException("Prefix length must be 0–32: " + cidr);
            }

            InetAddress inetAddress;
            try {
                inetAddress = InetAddress.getByName(parts[0].strip());
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Invalid network address in: " + cidr, e);
            }

            byte[] addr = inetAddress.getAddress();
            if (addr.length != 4) {
                throw new IllegalArgumentException("Only IPv4 CIDRs are supported: " + cidr);
            }

            long network = toUnsignedLong(addr);
            long mask    = prefixLength == 0 ? 0L : (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;

            return new CidrBlock(network & mask, mask, cidr);
        }

        /** Returns {@code true} when {@code ip} (unsigned 32-bit long) is within this block. */
        boolean contains(long ip) {
            return (ip & subnetMask) == networkAddress;
        }

        @Override
        public String toString() {
            return notation;
        }
    }
}
