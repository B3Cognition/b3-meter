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
package com.b3meter.web.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.b3meter.engine.service.SampleBucketConsumer;
import com.b3meter.engine.service.SampleStreamBroker;
import com.b3meter.web.api.repository.TestRunRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SSE streaming controller for live test-run results.
 *
 * <p>Each GET request opens a Server-Sent Events stream for the specified run.
 * The stream delivers {@code sample.bucket} events whenever the broker publishes
 * a new 1-second aggregation bucket for that run.
 *
 * <p>Uses Spring MVC {@link SseEmitter} (not WebFlux) as specified by ADR.
 */
@RestController
@RequestMapping("/api/v1/stream")
@Tag(name = "Streaming", description = "Server-Sent Events (SSE) streams for live test-run results")
public class StreamingController {

    static final String EVENT_SAMPLE_BUCKET = "sample.bucket";
    private static final long SSE_TIMEOUT_MS = 30L * 60L * 1_000L;
    private static final Logger LOG = Logger.getLogger(StreamingController.class.getName());

    private final SampleStreamBroker broker;
    private final TestRunRepository runRepository;
    private final ObjectMapper objectMapper;

    public StreamingController(SampleStreamBroker broker,
                               TestRunRepository runRepository,
                               ObjectMapper objectMapper) {
        this.broker = broker;
        this.runRepository = runRepository;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Stream live results", description = "Opens an SSE stream delivering real-time sample buckets for a running test")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SSE stream opened — delivers sample.bucket events"),
            @ApiResponse(responseCode = "404", description = "Run not found (error event sent, then stream closes)")
    })
    @GetMapping(value = "/{runId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamResults(@PathVariable String runId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        if (runRepository.findById(runId).isEmpty()) {
            sendErrorEvent(emitter, "Run not found: " + runId);
            emitter.complete();
            return emitter;
        }

        SampleBucketConsumer[] consumerRef = new SampleBucketConsumer[1];
        SampleBucketConsumer consumer = bucket -> {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("runId",            runId);
                payload.put("timestamp",        bucket.timestamp().toString());
                payload.put("samplerLabel",     bucket.samplerLabel());
                payload.put("sampleCount",      bucket.sampleCount());
                payload.put("errorCount",       bucket.errorCount());
                payload.put("avgResponseTime",  bucket.avgResponseTime());
                payload.put("minResponseTime",  bucket.minResponseTime());
                payload.put("maxResponseTime",  bucket.maxResponseTime());
                payload.put("percentile90",     bucket.percentile90());
                payload.put("percentile95",     bucket.percentile95());
                payload.put("percentile99",     bucket.percentile99());
                payload.put("samplesPerSecond", bucket.samplesPerSecond());
                payload.put("errorPercent",     bucket.errorPercent());

                String json = objectMapper.writeValueAsString(payload);
                emitter.send(SseEmitter.event().name(EVENT_SAMPLE_BUCKET).data(json));
            } catch (IOException e) {
                unsubscribeAndComplete(emitter, runId, consumerRef[0]);
            }
        };
        consumerRef[0] = consumer;

        emitter.onCompletion(() -> broker.unsubscribe(runId, consumer));
        emitter.onTimeout(()   -> broker.unsubscribe(runId, consumer));
        emitter.onError(ex     -> broker.unsubscribe(runId, consumer));

        broker.subscribe(runId, consumer);
        return emitter;
    }

    private void unsubscribeAndComplete(SseEmitter emitter,
                                        String runId,
                                        SampleBucketConsumer consumer) {
        if (consumer != null) {
            broker.unsubscribe(runId, consumer);
        }
        emitter.complete();
    }

    private void sendErrorEvent(SseEmitter emitter, String message) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("message", message);
            String json = objectMapper.writeValueAsString(payload);
            emitter.send(SseEmitter.event().name("error").data(json));
        } catch (IOException e) {
            LOG.log(Level.FINE, "Failed to send error SSE event: {0}", e.getMessage());
        }
    }
}
