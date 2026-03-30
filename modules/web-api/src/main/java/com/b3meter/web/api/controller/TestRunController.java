package com.jmeternext.web.api.controller;

import com.jmeternext.web.api.controller.dto.MetricsDto;
import com.jmeternext.web.api.controller.dto.StartRunRequest;
import com.jmeternext.web.api.controller.dto.TestRunDto;
import com.jmeternext.web.api.service.TestRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for test run lifecycle management.
 *
 * <p>All endpoints are under {@code /api/v1/runs}. Supports starting runs,
 * querying status, stopping (graceful and immediate), listing by plan, and
 * polling latest metrics.
 *
 * <p>Security is handled globally by
 * {@link com.jmeternext.web.api.config.SecurityConfig} — all requests are
 * permitted in single-user desktop mode.
 */
@RestController
@RequestMapping("/api/v1/runs")
@Tag(name = "Test Runs", description = "Test run lifecycle — start, stop, status, metrics")
public class TestRunController {

    private final TestRunService service;

    public TestRunController(TestRunService service) {
        this.service = service;
    }

    @Operation(summary = "Start a new test run", description = "Launches a test run for the given plan ID")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Run started"),
            @ApiResponse(responseCode = "400", description = "Invalid plan ID")
    })
    @PostMapping
    public ResponseEntity<TestRunDto> startRun(@RequestBody StartRunRequest request) {
        try {
            TestRunDto run = service.startRun(request);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(run);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Get run status", description = "Returns current status and metadata for a test run")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Run found"),
            @ApiResponse(responseCode = "404", description = "Run not found")
    })
    @GetMapping("/{runId}")
    public ResponseEntity<TestRunDto> getRunStatus(@PathVariable String runId) {
        return service.getRunStatus(runId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Graceful stop", description = "Requests a graceful stop — finishes current iterations before stopping")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stop requested"),
            @ApiResponse(responseCode = "404", description = "Run not found")
    })
    @PostMapping("/{runId}/stop")
    public ResponseEntity<Void> stopRun(@PathVariable String runId) {
        boolean found = service.stopRun(runId);
        return found ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @Operation(summary = "Immediate stop", description = "Force-stops all virtual users immediately")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Run force-stopped"),
            @ApiResponse(responseCode = "404", description = "Run not found")
    })
    @PostMapping("/{runId}/stop-now")
    public ResponseEntity<Void> stopRunNow(@PathVariable String runId) {
        boolean found = service.stopRunNow(runId);
        return found ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @Operation(summary = "Get latest metrics", description = "Returns the most recent aggregated metrics snapshot for a run")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Metrics returned"),
            @ApiResponse(responseCode = "404", description = "Run not found")
    })
    @GetMapping("/{runId}/metrics")
    public ResponseEntity<MetricsDto> getMetrics(@PathVariable String runId) {
        return service.getMetrics(runId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get SLA status", description = "Returns real-time SLA evaluation status and any violations for a run")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SLA status returned"),
            @ApiResponse(responseCode = "404", description = "Run not found or no SLA configured")
    })
    @GetMapping("/{runId}/sla")
    public ResponseEntity<?> getSlaStatus(@PathVariable String runId) {
        return service.getSlaStatus(runId)
                .map(s -> ResponseEntity.ok((Object) s))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "List runs", description = "Returns all runs, optionally filtered by plan ID")
    @GetMapping
    public List<TestRunDto> listRuns(
            @RequestParam(required = false) String planId) {
        return service.listRuns(planId);
    }
}
