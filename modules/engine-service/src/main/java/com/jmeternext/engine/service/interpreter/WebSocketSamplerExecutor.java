package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code WebSocketSampler} {@link PlanNode}.
 *
 * <p>Reads the following sampler properties:
 * <ul>
 *   <li>{@code WebSocketSampler.url} — WebSocket endpoint (ws:// or wss://)</li>
 *   <li>{@code WebSocketSampler.message} — text message to send after connect</li>
 *   <li>{@code WebSocketSampler.connectTimeout} — connect timeout in ms (default 5000)</li>
 *   <li>{@code WebSocketSampler.responseTimeout} — response timeout in ms (default 10000)</li>
 * </ul>
 *
 * <p>Uses {@link java.net.http.HttpClient} WebSocket support (Java 11+).
 * Connects, sends message, waits for one response, then closes.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class WebSocketSamplerExecutor {

    private static final Logger LOG = Logger.getLogger(WebSocketSamplerExecutor.class.getName());

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_RESPONSE_TIMEOUT_MS = 10000;

    private WebSocketSamplerExecutor() {
        // utility class — not instantiable
    }

    /**
     * Executes the WebSocket operation described by {@code node}.
     *
     * @param node      the WebSocketSampler node; must not be {@code null}
     * @param result    the sample result to populate; must not be {@code null}
     * @param variables current VU variable scope for {@code ${varName}} substitution
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String url = resolve(node.getStringProp("WebSocketSampler.url", ""), variables);
        String message = resolve(node.getStringProp("WebSocketSampler.message", ""), variables);
        int connectTimeout = node.getIntProp("WebSocketSampler.connectTimeout", DEFAULT_CONNECT_TIMEOUT_MS);
        int responseTimeout = node.getIntProp("WebSocketSampler.responseTimeout", DEFAULT_RESPONSE_TIMEOUT_MS);

        if (url.isBlank()) {
            result.setFailureMessage("WebSocketSampler.url is empty");
            return;
        }

        LOG.log(Level.FINE, "WebSocketSamplerExecutor: connecting to {0}", url);

        long start = System.currentTimeMillis();

        try {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(connectTimeout))
                    .build();

            // Future that completes when first message is received
            CompletableFuture<String> responseFuture = new CompletableFuture<>();

            WebSocket.Listener listener = new WebSocket.Listener() {
                private final StringBuilder buffer = new StringBuilder();

                @Override
                public void onOpen(WebSocket webSocket) {
                    LOG.log(Level.FINE, "WebSocketSamplerExecutor: connected");
                    webSocket.request(1);
                }

                @Override
                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                    buffer.append(data);
                    if (last) {
                        responseFuture.complete(buffer.toString());
                    } else {
                        webSocket.request(1);
                    }
                    return null;
                }

                @Override
                public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                    byte[] bytes = new byte[data.remaining()];
                    data.get(bytes);
                    buffer.append(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                    if (last) {
                        responseFuture.complete(buffer.toString());
                    } else {
                        webSocket.request(1);
                    }
                    return null;
                }

                @Override
                public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                    if (!responseFuture.isDone()) {
                        responseFuture.complete(buffer.toString());
                    }
                    return null;
                }

                @Override
                public void onError(WebSocket webSocket, Throwable error) {
                    if (!responseFuture.isDone()) {
                        responseFuture.completeExceptionally(error);
                    }
                }
            };

            // Connect
            WebSocket ws = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofMillis(connectTimeout))
                    .buildAsync(URI.create(url), listener)
                    .get(connectTimeout, TimeUnit.MILLISECONDS);

            long connectTime = System.currentTimeMillis() - start;
            result.setConnectTimeMs(connectTime);

            // Send message if provided
            if (!message.isEmpty()) {
                ws.sendText(message, true).get(responseTimeout, TimeUnit.MILLISECONDS);
            }

            result.setLatencyMs(System.currentTimeMillis() - start - connectTime);

            // Wait for response
            String responseText;
            if (!message.isEmpty()) {
                responseText = responseFuture.get(responseTimeout, TimeUnit.MILLISECONDS);
            } else {
                // No message sent — just verify connection succeeded
                responseText = "";
            }

            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);
            result.setResponseBody(responseText);
            result.setStatusCode(101); // WebSocket upgrade status

            // Close gracefully
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done")
                    .orTimeout(2000, TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> null);

        } catch (TimeoutException e) {
            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);
            result.setFailureMessage("WebSocket timeout: " + e.getMessage());
            LOG.log(Level.WARNING, "WebSocketSamplerExecutor: timeout for " + url, e);
        } catch (Exception e) {
            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);
            Throwable cause = (e.getCause() != null) ? e.getCause() : e;
            result.setFailureMessage("WebSocket error: " + cause.getMessage());
            LOG.log(Level.WARNING, "WebSocketSamplerExecutor: error for " + url, cause);
        }
    }

    private static String resolve(String value, Map<String, String> variables) {
        if (value == null) return "";
        return VariableResolver.resolve(value, variables);
    }
}
