package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code TCPSampler} {@link PlanNode} using raw TCP sockets.
 *
 * <p>Reads the following sampler properties:
 * <ul>
 *   <li>{@code TCPSampler.host} — target host</li>
 *   <li>{@code TCPSampler.port} — target port</li>
 *   <li>{@code TCPSampler.payload} — data to send</li>
 *   <li>{@code TCPSampler.timeout} — connection timeout in ms (default 5000)</li>
 *   <li>{@code TCPSampler.responseSize} — bytes to read back (default 1024)</li>
 * </ul>
 *
 * <p>Implementation:
 * <ol>
 *   <li>Open socket with timeout</li>
 *   <li>Write payload bytes</li>
 *   <li>Read response bytes</li>
 *   <li>Set SampleResult (success, responseBody, totalTimeMs)</li>
 *   <li>Close socket in finally</li>
 * </ol>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class TCPSamplerExecutor {

    private static final Logger LOG = Logger.getLogger(TCPSamplerExecutor.class.getName());

    private static final int DEFAULT_PORT = 0;
    private static final int DEFAULT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_RESPONSE_SIZE = 1024;

    private TCPSamplerExecutor() {
        // utility class — not instantiable
    }

    /**
     * Executes the TCP operation described by {@code node}.
     *
     * @param node      the TCPSampler node; must not be {@code null}
     * @param result    the sample result to populate; must not be {@code null}
     * @param variables current VU variable scope for {@code ${varName}} substitution
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String host = resolve(node.getStringProp("TCPSampler.host", ""), variables);
        int port = node.getIntProp("TCPSampler.port", DEFAULT_PORT);
        String payload = resolve(node.getStringProp("TCPSampler.payload", ""), variables);
        int timeout = node.getIntProp("TCPSampler.timeout", DEFAULT_TIMEOUT_MS);
        int responseSize = node.getIntProp("TCPSampler.responseSize", DEFAULT_RESPONSE_SIZE);

        if (host.isBlank()) {
            result.setFailureMessage("TCPSampler.host is empty");
            return;
        }

        if (port <= 0) {
            result.setFailureMessage("TCPSampler.port is invalid: " + port);
            return;
        }

        LOG.log(Level.FINE, "TCPSamplerExecutor: connecting to {0}:{1}",
                new Object[]{host, port});

        long start = System.currentTimeMillis();
        Socket socket = null;

        try {
            socket = new Socket();
            socket.setSoTimeout(timeout);
            socket.connect(new InetSocketAddress(host, port), timeout);

            long connectTime = System.currentTimeMillis() - start;
            result.setConnectTimeMs(connectTime);

            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // Write payload
            if (!payload.isEmpty()) {
                out.write(payload.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }

            result.setLatencyMs(System.currentTimeMillis() - start - connectTime);

            // Read response
            byte[] buffer = new byte[responseSize];
            int totalRead = 0;
            int bytesRead;

            while (totalRead < responseSize) {
                bytesRead = in.read(buffer, totalRead, responseSize - totalRead);
                if (bytesRead == -1) {
                    break; // end of stream
                }
                totalRead += bytesRead;
            }

            String responseBody = new String(buffer, 0, totalRead, StandardCharsets.UTF_8);
            result.setResponseBody(responseBody);
            result.setStatusCode(0); // TCP has no status codes; 0 = success

            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);

        } catch (IOException e) {
            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);
            result.setFailureMessage("TCP error: " + e.getMessage());
            LOG.log(Level.WARNING, "TCPSamplerExecutor: error for " + host + ":" + port, e);
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // best-effort close
                }
            }
        }
    }

    private static String resolve(String value, Map<String, String> variables) {
        if (value == null) return "";
        return VariableResolver.resolve(value, variables);
    }
}
