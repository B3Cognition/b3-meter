package com.jmeternext.web.api.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SsrfProtectionService}.
 *
 * <p>Tests cover the five default blocked CIDR ranges, the AWS metadata
 * endpoint ({@code 169.254.169.254}), publicly routable addresses that must
 * be allowed, and edge cases such as null / blank input.
 *
 * <p>No Spring context is required — the service is constructed directly.
 */
class SsrfProtectionServiceTest {

    /** Service under test using the default blocked CIDRs (empty property). */
    private final SsrfProtectionService service = new SsrfProtectionService("");

    // -------------------------------------------------------------------------
    // AWS / cloud metadata endpoint — must always be blocked
    // -------------------------------------------------------------------------

    @Test
    void awsMetadataEndpoint_isBlocked() {
        assertTrue(service.isBlocked("169.254.169.254"),
                "AWS metadata IP 169.254.169.254 must be blocked");
    }

    @Test
    void awsMetadataEndpoint_isNotAllowed() {
        assertFalse(service.isAllowed("169.254.169.254"),
                "isAllowed must return false for AWS metadata IP");
    }

    // -------------------------------------------------------------------------
    // RFC-1918 private ranges — must be blocked
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "blocked: {0}")
    @ValueSource(strings = {
            "10.0.0.1",       // 10.0.0.0/8  start
            "10.255.255.255", // 10.0.0.0/8  end
            "172.16.0.1",     // 172.16.0.0/12 start
            "172.31.255.255", // 172.16.0.0/12 end
            "192.168.0.1",    // 192.168.0.0/16 start
            "192.168.255.255" // 192.168.0.0/16 end
    })
    void privateRangeAddresses_areBlocked(String ip) {
        assertTrue(service.isBlocked(ip),
                ip + " is in a private range and must be blocked");
    }

    // -------------------------------------------------------------------------
    // Loopback — must be blocked
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "blocked loopback: {0}")
    @ValueSource(strings = {
            "127.0.0.1",
            "127.255.255.255"
    })
    void loopbackAddresses_areBlocked(String ip) {
        assertTrue(service.isBlocked(ip),
                ip + " is loopback and must be blocked");
    }

    // -------------------------------------------------------------------------
    // Link-local range — must be blocked
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "blocked link-local: {0}")
    @ValueSource(strings = {
            "169.254.0.1",
            "169.254.169.254",
            "169.254.255.255"
    })
    void linkLocalAddresses_areBlocked(String ip) {
        assertTrue(service.isBlocked(ip),
                ip + " is link-local and must be blocked");
    }

    // -------------------------------------------------------------------------
    // Public / routable IPs — must be allowed
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "allowed: {0}")
    @ValueSource(strings = {
            "8.8.8.8",      // Google Public DNS
            "1.1.1.1",      // Cloudflare DNS
            "93.184.216.34" // example.com
    })
    void publicIpAddresses_areAllowed(String ip) {
        assertTrue(service.isAllowed(ip),
                ip + " is a public IP and must be allowed");
    }

    @ParameterizedTest(name = "not blocked: {0}")
    @ValueSource(strings = {
            "8.8.8.8",
            "1.1.1.1"
    })
    void publicIpAddresses_areNotBlocked(String ip) {
        assertFalse(service.isBlocked(ip),
                ip + " must not be blocked");
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    void nullHost_isBlocked() {
        assertTrue(service.isBlocked(null),
                "null host must be blocked (fail-closed)");
    }

    @Test
    void blankHost_isBlocked() {
        assertTrue(service.isBlocked("   "),
                "blank host must be blocked (fail-closed)");
    }

    @Test
    void unresolvableHost_isBlocked() {
        // A hostname that cannot be resolved should be blocked to fail closed
        assertTrue(service.isBlocked("this-host-does-not-exist.invalid"),
                "unresolvable host must be blocked");
    }

    // -------------------------------------------------------------------------
    // Custom CIDR configuration
    // -------------------------------------------------------------------------

    @Test
    void customBlockedCidr_blocksAddressInRange() {
        // Override defaults with a single custom CIDR
        SsrfProtectionService custom = new SsrfProtectionService("203.0.113.0/24");
        assertTrue(custom.isBlocked("203.0.113.42"),
                "Address in custom blocked CIDR must be blocked");
    }

    @Test
    void customBlockedCidr_allowsAddressOutsideRange() {
        SsrfProtectionService custom = new SsrfProtectionService("203.0.113.0/24");
        // 8.8.8.8 is outside 203.0.113.0/24
        assertTrue(custom.isAllowed("8.8.8.8"),
                "Address outside custom CIDR must be allowed");
    }

    @Test
    void customBlockedCidrs_multipleRangesCommaSeparated() {
        SsrfProtectionService custom =
                new SsrfProtectionService("10.0.0.0/8,192.168.0.0/16");
        assertTrue(custom.isBlocked("10.1.2.3"),
                "10.1.2.3 must be blocked by 10.0.0.0/8");
        assertTrue(custom.isBlocked("192.168.1.1"),
                "192.168.1.1 must be blocked by 192.168.0.0/16");
        assertTrue(custom.isAllowed("8.8.8.8"),
                "8.8.8.8 must be allowed (not in custom CIDRs)");
    }

    // -------------------------------------------------------------------------
    // Boundary conditions for CIDR parsing
    // -------------------------------------------------------------------------

    @Test
    void cidrWithPrefixLength32_matchesExactIp() {
        SsrfProtectionService custom = new SsrfProtectionService("8.8.8.8/32");
        assertTrue(custom.isBlocked("8.8.8.8"),
                "Exact /32 CIDR must match the single IP");
        assertTrue(custom.isAllowed("8.8.8.9"),
                "Address one off from /32 CIDR must be allowed");
    }

    @Test
    void cidrWithPrefixLength0_blocksAllAddresses() {
        SsrfProtectionService custom = new SsrfProtectionService("0.0.0.0/0");
        assertTrue(custom.isBlocked("8.8.8.8"),
                "0.0.0.0/0 must block all addresses");
    }
}
