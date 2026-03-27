package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Processes an {@code AuthManager} {@link PlanNode} to manage HTTP authentication.
 *
 * <p>Stores credentials for HTTP Basic/Digest auth per URL pattern. Before HTTP
 * requests, the sampler checks if the URL matches a pattern and adds the
 * appropriate {@code Authorization} header.
 *
 * <p>Reads the following JMX properties:
 * <ul>
 *   <li>{@code AuthManager.auth_list} — collection of Authorization entries</li>
 *   <li>{@code AuthManager.clearEachIteration} — clear credentials each iteration</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class HTTPAuthManagerExecutor {

    private static final Logger LOG = Logger.getLogger(HTTPAuthManagerExecutor.class.getName());

    /** Variable key prefix for storing auth config in VU variables. */
    private static final String AUTH_VAR_PREFIX = "__jmn_auth_";

    private HTTPAuthManagerExecutor() {}

    /**
     * Configures the auth manager from the plan node properties and stores
     * authorization entries in the VU variable map.
     *
     * @param node      the AuthManager node; must not be {@code null}
     * @param variables mutable VU variable map
     */
    public static void configure(PlanNode node, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        List<Object> authList = node.getCollectionProp("AuthManager.auth_list");
        boolean clearEachIteration = node.getBoolProp("AuthManager.clearEachIteration", false);

        // Clear existing auth entries if configured
        if (clearEachIteration) {
            clearAuthEntries(variables);
        }

        List<AuthEntry> entries = new ArrayList<>();

        for (Object item : authList) {
            if (item instanceof PlanNode authNode) {
                String url = authNode.getStringProp("Authorization.url", "");
                String username = authNode.getStringProp("Authorization.username", "");
                String password = authNode.getStringProp("Authorization.password", "");
                String domain = authNode.getStringProp("Authorization.domain", "");
                String realm = authNode.getStringProp("Authorization.realm", "");
                String mechanism = authNode.getStringProp("Authorization.mechanism", "BASIC");

                entries.add(new AuthEntry(url, username, password, domain, realm, mechanism));
            }
        }

        // Store entries count and each entry in variables
        variables.put(AUTH_VAR_PREFIX + "enabled", "true");
        variables.put(AUTH_VAR_PREFIX + "count", String.valueOf(entries.size()));

        for (int i = 0; i < entries.size(); i++) {
            AuthEntry entry = entries.get(i);
            String prefix = AUTH_VAR_PREFIX + i + "_";
            variables.put(prefix + "url", entry.url());
            variables.put(prefix + "username", entry.username());
            variables.put(prefix + "password", entry.password());
            variables.put(prefix + "domain", entry.domain());
            variables.put(prefix + "realm", entry.realm());
            variables.put(prefix + "mechanism", entry.mechanism());
        }

        LOG.log(Level.FINE,
                "HTTPAuthManagerExecutor [{0}]: configured {1} auth entries",
                new Object[]{node.getTestName(), entries.size()});
    }

    /**
     * Finds the matching Authorization header value for a given URL.
     *
     * @param requestUrl the request URL to match against auth entries
     * @param variables  VU variable map containing auth configuration
     * @return the Authorization header value, or {@code null} if no match
     */
    public static String getAuthorizationHeader(String requestUrl, Map<String, String> variables) {
        if (!"true".equals(variables.get(AUTH_VAR_PREFIX + "enabled"))) {
            return null;
        }

        String countStr = variables.get(AUTH_VAR_PREFIX + "count");
        if (countStr == null) return null;

        int count;
        try {
            count = Integer.parseInt(countStr);
        } catch (NumberFormatException e) {
            return null;
        }

        for (int i = 0; i < count; i++) {
            String prefix = AUTH_VAR_PREFIX + i + "_";
            String urlPattern = variables.getOrDefault(prefix + "url", "");

            if (urlMatches(requestUrl, urlPattern)) {
                String username = variables.getOrDefault(prefix + "username", "");
                String password = variables.getOrDefault(prefix + "password", "");
                String mechanism = variables.getOrDefault(prefix + "mechanism", "BASIC");

                return buildAuthHeader(username, password, mechanism);
            }
        }

        return null;
    }

    /**
     * Builds the Authorization header value for the given mechanism.
     */
    static String buildAuthHeader(String username, String password, String mechanism) {
        if ("BASIC".equalsIgnoreCase(mechanism)) {
            String credentials = username + ":" + password;
            String encoded = Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            return "Basic " + encoded;
        }
        // DIGEST and KERBEROS require server challenge — store credentials for later
        // For now, return Basic as fallback
        LOG.log(Level.FINE,
                "HTTPAuthManagerExecutor: mechanism [{0}] not fully supported, using Basic",
                mechanism);
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    /**
     * Checks if a request URL matches an auth URL pattern.
     * Simple prefix matching: the request URL must start with the pattern URL.
     */
    static boolean urlMatches(String requestUrl, String pattern) {
        if (pattern == null || pattern.isEmpty()) return false;
        if (requestUrl == null || requestUrl.isEmpty()) return false;
        return requestUrl.startsWith(pattern);
    }

    /**
     * Clears all auth entries from the variable map.
     */
    private static void clearAuthEntries(Map<String, String> variables) {
        variables.entrySet().removeIf(e -> e.getKey().startsWith(AUTH_VAR_PREFIX));
    }

    /**
     * Immutable record representing a single authorization entry.
     */
    public record AuthEntry(
            String url,
            String username,
            String password,
            String domain,
            String realm,
            String mechanism
    ) {}
}
