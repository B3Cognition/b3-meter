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

import com.b3meter.web.api.controller.dto.RegisterWorkerRequest;
import com.b3meter.web.api.controller.dto.WorkerDto;
import com.b3meter.web.api.repository.JdbcWorkerRepository;
import com.b3meter.web.api.repository.WorkerEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for distributed worker node management.
 *
 * <p>All endpoints are under {@code /api/v1/workers}. Workers are remote JMeter
 * nodes that can be selected to participate in distributed test runs.
 *
 * <p>In single-user desktop mode all requests are permitted. The "admin only"
 * designation is documented intent for future multi-user deployments.
 *
 * @see com.b3meter.web.api.config.SecurityConfig
 */
@RestController
@RequestMapping("/api/v1/workers")
@Tag(name = "Workers", description = "Distributed worker node registration, health checks, and removal")
public class WorkerController {

    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(3);

    private final JdbcWorkerRepository repository;
    private final HttpClient httpClient;

    public WorkerController(JdbcWorkerRepository repository) {
        this.repository = repository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HEALTH_TIMEOUT)
                .build();
    }

    @Operation(summary = "Register a worker", description = "Registers a new worker node for distributed test runs")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Worker registered"),
            @ApiResponse(responseCode = "400", description = "Invalid hostname or port")
    })
    @PostMapping
    public ResponseEntity<WorkerDto> register(@RequestBody RegisterWorkerRequest request) {
        if (request.hostname() == null || request.hostname().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (request.port() == null || request.port() < 1 || request.port() > 65535) {
            return ResponseEntity.badRequest().build();
        }

        WorkerEntity entity = new WorkerEntity(
                UUID.randomUUID().toString(),
                request.hostname().trim(),
                request.port(),
                "AVAILABLE",
                null,
                Instant.now()
        );
        WorkerEntity saved = repository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved));
    }

    @Operation(summary = "List all workers", description = "Returns all registered worker nodes with current status")
    @GetMapping
    public List<WorkerDto> listWorkers() {
        return repository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Operation(summary = "Remove a worker", description = "Unregisters a worker node")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Worker removed"),
            @ApiResponse(responseCode = "404", description = "Worker not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable String id) {
        boolean deleted = repository.deleteById(id);
        return deleted
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @Operation(summary = "Health check a worker", description = "Probes the worker's health endpoint and updates its status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Health check completed"),
            @ApiResponse(responseCode = "404", description = "Worker not found")
    })
    @GetMapping("/{id}/health")
    public ResponseEntity<Map<String, Object>> healthCheck(@PathVariable String id) {
        var workerOpt = repository.findById(id);
        if (workerOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        WorkerEntity worker = workerOpt.get();
        int healthPort = worker.port() > 1000 ? worker.port() - 1000 : worker.port() + 1000;
        String healthUrl = "http://" + worker.hostname() + ":" + healthPort + "/health";

        boolean healthy = false;
        String detail = "unreachable";
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .timeout(HEALTH_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            healthy = resp.statusCode() >= 200 && resp.statusCode() < 300;
            detail = healthy ? "ok" : "unhealthy (HTTP " + resp.statusCode() + ")";
        } catch (Exception e) {
            detail = "unreachable: " + e.getMessage();
        }

        String newStatus = healthy ? "AVAILABLE" : "OFFLINE";
        repository.updateStatus(id, newStatus);

        return ResponseEntity.ok(Map.of(
                "id", id,
                "hostname", worker.hostname(),
                "port", worker.port(),
                "status", newStatus,
                "detail", detail,
                "checkedAt", Instant.now().toString()
        ));
    }

    private WorkerDto toDto(WorkerEntity e) {
        return new WorkerDto(
                e.id(),
                e.hostname(),
                e.port(),
                e.status(),
                e.lastHeartbeat(),
                e.registeredAt()
        );
    }
}
