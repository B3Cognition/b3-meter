/*
 * Copyright 2024-2026 b3meter Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.b3meter.web.api.service;

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
