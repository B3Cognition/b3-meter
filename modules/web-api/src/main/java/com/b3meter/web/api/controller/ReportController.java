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

import com.b3meter.web.api.service.ReportService;
import com.b3meter.web.api.service.ReportService.ReportStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * REST controller for HTML report generation and status polling.
 *
 * <p>All endpoints are nested under {@code /api/v1/runs/{runId}/report}.
 *
 * <p>Report generation is CPU-bound and may be slow for large runs.
 * The POST endpoint therefore accepts the request and delegates work to a
 * background thread, returning 202 Accepted immediately. Clients poll the
 * GET endpoint to check when the report is ready.
 *
 * <p>Security is handled globally by
 * {@link com.b3meter.web.api.config.SecurityConfig} — all requests are
 * permitted in single-user desktop mode.
 */
@RestController
@RequestMapping("/api/v1/runs/{runId}/report")
public class ReportController {

    private static final Logger LOG = Logger.getLogger(ReportController.class.getName());

    private final ReportService reportService;

    /**
     * Single-thread executor so that at most one report is generated at a time
     * per application instance, keeping memory usage predictable.
     */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "report-generator");
        t.setDaemon(true);
        return t;
    });

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/runs/{runId}/report — trigger generation
    // -------------------------------------------------------------------------

    /**
     * Triggers asynchronous HTML report generation for the specified run.
     *
     * <p>If the run does not exist, returns 404. Otherwise submits the generation
     * task to the background executor and returns 202 Accepted with a status payload.
     *
     * @param runId the run identifier from the URL
     * @return 202 with {@code {"runId": "...", "status": "GENERATING"}} on success,
     *         or 404 if the run is not found
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> triggerReport(@PathVariable String runId) {
        ReportStatus current = reportService.getStatus(runId);

        // If already ready, return 202 so clients know it was accepted (idempotent).
        // If already generating, also return 202 without submitting a second task.
        if (current == ReportStatus.NOT_FOUND) {
            // Validate the run exists before queuing (service throws IAE if not found).
            // We perform a lightweight status check here; if the run truly doesn't exist,
            // the service will throw and we surface a 404.
            try {
                executor.submit(() -> {
                    try {
                        reportService.generateReport(runId);
                    } catch (IllegalArgumentException e) {
                        LOG.warning("Report requested for non-existent run: " + runId);
                    } catch (Exception e) {
                        LOG.log(Level.SEVERE, "Async report generation failed for run " + runId, e);
                    }
                });
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Failed to submit report task for run " + runId, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("runId", runId, "status", "GENERATING"));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/runs/{runId}/report — check status / get path
    // -------------------------------------------------------------------------

    /**
     * Returns the current status of the report for the specified run.
     *
     * <p>When the report is ready, the response body includes a {@code path} field
     * with the absolute path of the report directory so clients can open it locally.
     *
     * @param runId the run identifier from the URL
     * @return <ul>
     *   <li>200 with {@code {"runId": "...", "status": "READY", "path": "..."}}
     *       when the report exists</li>
     *   <li>200 with {@code {"runId": "...", "status": "GENERATING"}} while in progress</li>
     *   <li>200 with {@code {"runId": "...", "status": "FAILED"}} on generation failure</li>
     *   <li>404 when no report has been requested for this run</li>
     * </ul>
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> getReportStatus(@PathVariable String runId) {
        ReportStatus status = reportService.getStatus(runId);

        if (status == ReportStatus.NOT_FOUND) {
            return ResponseEntity.notFound().build();
        }

        if (status == ReportStatus.READY) {
            Optional<Path> reportPath = reportService.getReportPath(runId);
            String pathStr = reportPath.map(p -> p.toString()).orElse("");
            return ResponseEntity.ok(Map.of(
                    "runId", runId,
                    "status", status.name(),
                    "path", pathStr
            ));
        }

        return ResponseEntity.ok(Map.of("runId", runId, "status", status.name()));
    }
}
