package com.jmeternext.web.api.service;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable snapshot of a single HTTP request captured during a proxy recording session.
 *
 * <p>Instances are created by {@link ProxyRecorderService} when a request is submitted
 * via {@code POST /api/v1/proxy-recorder/capture} and are stored in-memory until the
 * recording session is stopped or the captured list is applied to the test plan.
 *
 * @param id           unique identifier assigned at capture time
 * @param timestamp    wall-clock instant the request was received
 * @param method       HTTP method (GET, POST, PUT, …)
 * @param url          full request URL including query string
 * @param headers      request headers; empty map when header capture is disabled
 * @param body         request body bytes; empty array when body capture is disabled or no body
 * @param responseCode HTTP response status code (0 if unavailable)
 * @param elapsedMs    round-trip time in milliseconds (0 if unavailable)
 */
public record CapturedRequest(
        String id,
        Instant timestamp,
        String method,
        String url,
        Map<String, String> headers,
        byte[] body,
        int responseCode,
        long elapsedMs
) {}
