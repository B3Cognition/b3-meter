package com.jmeternext.web.api.controller;

import com.jmeternext.web.api.service.CapturedRequest;
import com.jmeternext.web.api.service.ProxyRecorderConfig;
import com.jmeternext.web.api.service.ProxyRecorderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * REST controller for the proxy recorder.
 *
 * <p>All endpoints are under {@code /api/v1/proxy-recorder}. The plan identifier
 * is passed as a query parameter ({@code planId}) on every request so that
 * multiple plans can maintain independent recording sessions.
 *
 * <h2>Lifecycle</h2>
 * <pre>
 *   POST /start     — begin recording for a plan
 *   POST /stop      — end recording for a plan
 *   GET  /status    — check whether recording is active
 *   GET  /captured  — list captured requests for a plan
 *   POST /capture   — submit a single captured request (from browser extension / curl)
 *   POST /apply     — convert captured requests to HTTPSampler nodes
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/proxy-recorder")
public class ProxyRecorderController {

    private final ProxyRecorderService service;

    public ProxyRecorderController(ProxyRecorderService service) {
        this.service = service;
    }

    // -------------------------------------------------------------------------
    // Start recording
    // -------------------------------------------------------------------------

    /**
     * Starts a recording session for the given plan.
     *
     * @param planId  the plan to record for
     * @param request optional configuration; defaults are applied when absent
     * @return 200 OK with the effective configuration
     */
    @PostMapping("/start")
    public ResponseEntity<ProxyRecorderConfig> start(
            @RequestParam String planId,
            @RequestBody(required = false) ProxyRecorderConfig request) {
        if (planId == null || planId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        service.startRecording(planId, request);
        return ResponseEntity.ok(service.getConfig(planId));
    }

    // -------------------------------------------------------------------------
    // Stop recording
    // -------------------------------------------------------------------------

    /**
     * Stops the active recording session for the given plan.
     *
     * @param planId the plan identifier
     * @return 200 OK
     */
    @PostMapping("/stop")
    public ResponseEntity<Void> stop(@RequestParam String planId) {
        if (planId == null || planId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        service.stopRecording(planId);
        return ResponseEntity.ok().build();
    }

    // -------------------------------------------------------------------------
    // Status
    // -------------------------------------------------------------------------

    /**
     * Returns whether recording is currently active for the given plan.
     *
     * @param planId the plan identifier
     * @return 200 OK with {@code { "recording": true|false, "config": {...} }}
     */
    @GetMapping("/status")
    public ResponseEntity<StatusResponse> status(@RequestParam String planId) {
        if (planId == null || planId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        boolean recording = service.isRecording(planId);
        ProxyRecorderConfig config = service.getConfig(planId);
        return ResponseEntity.ok(new StatusResponse(recording, config));
    }

    public record StatusResponse(boolean recording, ProxyRecorderConfig config) {}

    // -------------------------------------------------------------------------
    // List captured requests
    // -------------------------------------------------------------------------

    /**
     * Returns all requests captured for the given plan in the current session.
     *
     * @param planId the plan identifier
     * @return 200 OK with the list of captured requests (may be empty)
     */
    @GetMapping("/captured")
    public ResponseEntity<List<CapturedRequestDto>> captured(@RequestParam String planId) {
        if (planId == null || planId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        List<CapturedRequest> list = service.getCaptured(planId);
        List<CapturedRequestDto> dtos = list.stream().map(CapturedRequestDto::from).toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * JSON-safe DTO for a {@link CapturedRequest}.
     *
     * <p>{@code body} is Base64-encoded so arbitrary bytes survive JSON serialisation.
     */
    public record CapturedRequestDto(
            String id,
            Instant timestamp,
            String method,
            String url,
            Map<String, String> headers,
            String bodyBase64,
            int responseCode,
            long elapsedMs
    ) {
        public static CapturedRequestDto from(CapturedRequest req) {
            String encoded = (req.body() != null && req.body().length > 0)
                    ? Base64.getEncoder().encodeToString(req.body())
                    : null;
            return new CapturedRequestDto(
                    req.id(),
                    req.timestamp(),
                    req.method(),
                    req.url(),
                    req.headers(),
                    encoded,
                    req.responseCode(),
                    req.elapsedMs()
            );
        }
    }

    // -------------------------------------------------------------------------
    // Submit a captured request
    // -------------------------------------------------------------------------

    /**
     * Accepts a single captured request from an external agent (browser extension,
     * proxy sidecar, or curl) and stores it if the URL passes the active filter.
     *
     * @param planId  the plan identifier
     * @param payload the incoming request payload
     * @return 200 OK when stored, 202 Accepted when filtered out, 409 when not recording
     */
    @PostMapping("/capture")
    public ResponseEntity<Void> capture(
            @RequestParam String planId,
            @RequestBody CapturePayload payload) {
        if (planId == null || planId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (!service.isRecording(planId)) {
            return ResponseEntity.status(409).build(); // 409 Conflict — not recording
        }
        byte[] body = (payload.bodyBase64() != null && !payload.bodyBase64().isBlank())
                ? Base64.getDecoder().decode(payload.bodyBase64())
                : new byte[0];
        CapturedRequest req = ProxyRecorderService.newCapturedRequest(
                payload.method(),
                payload.url(),
                payload.headers() != null ? payload.headers() : Map.of(),
                body,
                payload.responseCode(),
                payload.elapsedMs()
        );
        boolean stored = service.capture(planId, req);
        return stored ? ResponseEntity.ok().build() : ResponseEntity.accepted().build();
    }

    /**
     * Incoming payload for {@code POST /capture}.
     *
     * <p>Body bytes must be Base64-encoded in {@code bodyBase64}; headers are a
     * flat string-to-string map.
     */
    public record CapturePayload(
            String method,
            String url,
            Map<String, String> headers,
            String bodyBase64,
            int responseCode,
            long elapsedMs
    ) {}

    // -------------------------------------------------------------------------
    // Apply to test plan
    // -------------------------------------------------------------------------

    /**
     * Converts the captured requests for a plan into HTTPSampler test-plan nodes.
     *
     * <p>The returned nodes are ready to be merged into the plan's {@code treeData}
     * by the caller (typically the frontend).
     *
     * @param planId the plan identifier
     * @return 200 OK with a list of node maps
     */
    @PostMapping("/apply")
    public ResponseEntity<List<Map<String, Object>>> apply(@RequestParam String planId) {
        if (planId == null || planId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        List<Map<String, Object>> nodes = service.applyToTestPlan(planId);
        return ResponseEntity.ok(nodes);
    }
}
