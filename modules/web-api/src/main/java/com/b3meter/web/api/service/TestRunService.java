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

import com.b3meter.engine.service.EngineService;
import com.b3meter.engine.service.SampleBucket;
import com.b3meter.engine.service.SampleBucketConsumer;
import com.b3meter.engine.service.SampleStreamBroker;
import com.b3meter.engine.service.TestRunContext;
import com.b3meter.engine.service.TestRunContextRegistry;
import com.b3meter.engine.service.TestRunHandle;
import com.b3meter.web.api.controller.dto.MetricsDto;
import com.b3meter.web.api.controller.dto.StartRunRequest;
import com.b3meter.web.api.controller.dto.TestRunDto;
import com.b3meter.web.api.metrics.LoadTestMetrics;
import com.b3meter.web.api.repository.TestPlanRepository;
import com.b3meter.web.api.repository.TestRunEntity;
import com.b3meter.web.api.repository.TestRunRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Business logic for test run lifecycle management.
 *
 * <p>Handles starting, stopping, and querying test runs. Delegates execution to
 * the injected {@link EngineService} (backed by {@code EngineServiceImpl}) and
 * persists run state to the database via {@link TestRunRepository}.
 *
 * <p>Metrics are tracked via a {@link SampleStreamBroker} subscription that
 * captures the most-recent {@link SampleBucket} per run for polling-first access
 * (R-05).
 */
@Service
public class TestRunService {

    private static final Logger LOG = Logger.getLogger(TestRunService.class.getName());

    /** Default owner when none is specified. */
    private static final String DEFAULT_OWNER = "system";

    /** Default virtual-user count when not provided in the request. */
    private static final int DEFAULT_VIRTUAL_USERS = 1;

    /** Default duration — 0 means run until plan completes naturally. */
    private static final long DEFAULT_DURATION_SECONDS = 0L;

    private final TestRunRepository runRepository;
    private final TestPlanRepository testPlanRepository;
    private final SampleStreamBroker broker;
    private final EngineService engineService;
    private final com.b3meter.web.api.security.ResourceQuotaService quotaService;
    private final LoadTestMetrics loadTestMetrics;

    /**
     * Stores the most-recent {@link SampleBucket} per runId, keyed by runId.
     * Used to serve GET /metrics without subscribing a new broker consumer on
     * each poll request.
     */
    private final ConcurrentHashMap<String, SampleBucket> latestBuckets = new ConcurrentHashMap<>();

    /**
     * Consumer references keyed by runId so they can be unsubscribed when the run ends.
     */
    private final ConcurrentHashMap<String, SampleBucketConsumer> consumers = new ConcurrentHashMap<>();

    /**
     * SLA evaluators keyed by runId — created when SLA thresholds are provided in the start request.
     */
    private final ConcurrentHashMap<String, com.b3meter.engine.service.SlaEvaluator> slaEvaluators = new ConcurrentHashMap<>();

    public TestRunService(TestRunRepository runRepository,
                          TestPlanRepository testPlanRepository,
                          SampleStreamBroker broker,
                          EngineService engineService,
                          com.b3meter.web.api.security.ResourceQuotaService quotaService,
                          LoadTestMetrics loadTestMetrics) {
        this.runRepository = runRepository;
        this.testPlanRepository = testPlanRepository;
        this.broker = broker;
        this.engineService = engineService;
        this.quotaService = quotaService;
        this.loadTestMetrics = loadTestMetrics;
    }

    // -------------------------------------------------------------------------
    // Start
    // -------------------------------------------------------------------------

    /**
     * Starts a new test run for the given plan.
     *
     * <p>Delegates execution to the injected {@link EngineService}. The engine
     * creates and registers its own {@link TestRunContext}; this method persists
     * the initial run entity to the database and subscribes a broker consumer to
     * capture metrics.
     *
     * <p>The {@link TestRunHandle#completion()} future is observed asynchronously
     * to update the run's final status in the database when the engine finishes.
     *
     * @param request start parameters; {@code planId} must not be null or blank
     * @return the persisted run as a DTO in RUNNING state
     * @throws IllegalArgumentException if {@code planId} is null or blank
     */
    public TestRunDto startRun(StartRunRequest request) {
        if (request.planId() == null || request.planId().isBlank()) {
            throw new IllegalArgumentException("planId must not be blank");
        }

        int virtualUsers = request.virtualUsers() != null
                ? request.virtualUsers()
                : DEFAULT_VIRTUAL_USERS;
        long durationSeconds = request.durationSeconds() != null
                ? request.durationSeconds()
                : DEFAULT_DURATION_SECONDS;

        // Enforce resource quotas before starting (FR-SEC-007)
        quotaService.checkQuota(DEFAULT_OWNER, virtualUsers, (int) durationSeconds);

        // Build property overrides for the engine
        Properties overrides = new Properties();
        overrides.setProperty("jmeter.threads",   String.valueOf(virtualUsers));
        overrides.setProperty("jmeter.duration",  String.valueOf(durationSeconds));

        // Load actual plan tree data from the database — auto-create if missing
        Map<String, Object> treeData = Collections.emptyMap();
        var planOpt = testPlanRepository.findById(request.planId());
        if (planOpt.isEmpty()) {
            // Plan was created in the UI but never persisted — create a stub record
            var stub = new com.b3meter.web.api.repository.TestPlanEntity(
                    request.planId(),
                    request.planId(),
                    DEFAULT_OWNER,
                    "{}",
                    java.time.Instant.now(),
                    java.time.Instant.now(),
                    null
            );
            testPlanRepository.save(stub);
            LOG.log(Level.INFO, "Auto-created test_plans record for planId={0}", request.planId());
        } else if (planOpt.get().treeData() != null) {
            treeData = Map.of(
                "planName", planOpt.get().name(),
                "jmxContent", planOpt.get().treeData(),
                "treeData", planOpt.get().treeData(),
                "virtualUsers", virtualUsers,
                "durationSeconds", durationSeconds
            );
        }

        // Delegate to engine — it creates the TestRunContext and registers it
        TestRunHandle handle = engineService.startRun(
                request.planId(),
                treeData,
                overrides
        );
        String runId = handle.runId();

        // Record run start in Prometheus metrics.
        loadTestMetrics.recordRunStarted();

        // Subscribe to broker to capture latest bucket for metrics polling (R-05).
        SampleBucketConsumer consumer = bucket -> {
            latestBuckets.put(runId, bucket);
            loadTestMetrics.recordSamples(bucket.sampleCount(), bucket.errorCount(), bucket.avgResponseTime());
        };
        consumers.put(runId, consumer);
        broker.subscribe(runId, consumer);

        // Subscribe SLA evaluator if thresholds are provided
        if (request.slaP95Ms() != null || request.slaP99Ms() != null
                || request.slaAvgMs() != null || request.slaMaxErrorPercent() != null) {
            var sla = new com.b3meter.engine.service.SlaEvaluator(
                    request.slaP95Ms() != null ? request.slaP95Ms() : 0,
                    request.slaP99Ms() != null ? request.slaP99Ms() : 0,
                    request.slaAvgMs() != null ? request.slaAvgMs() : 0,
                    request.slaMaxErrorPercent() != null ? request.slaMaxErrorPercent() : -1
            );
            slaEvaluators.put(runId, sla);
            broker.subscribe(runId, sla);
            LOG.log(Level.INFO, "SLA evaluator active for run {0}: p95<{1}ms p99<{2}ms avg<{3}ms err<{4}%",
                    new Object[]{runId, request.slaP95Ms(), request.slaP99Ms(), request.slaAvgMs(), request.slaMaxErrorPercent()});
        }

        // Persist initial RUNNING state.
        TestRunEntity entity = new TestRunEntity(
                runId,
                request.planId(),
                TestRunContext.TestRunStatus.RUNNING.name(),
                handle.startedAt(),
                null,
                0L,
                0L,
                DEFAULT_OWNER
        );
        runRepository.create(entity);

        // Observe completion to update DB status when the engine finishes
        handle.completion().whenComplete((result, ex) -> {
            try {
                String finalStatus = result != null
                        ? result.finalStatus().name()
                        : TestRunContext.TestRunStatus.ERROR.name();
                long totalSamples = result != null ? result.totalSamples() : 0L;
                long errorCount   = result != null ? result.errorCount()   : 0L;
                runRepository.updateStatus(runId, finalStatus);
                // Unsubscribe broker consumer
                SampleBucketConsumer c = consumers.remove(runId);
                if (c != null) {
                    broker.unsubscribe(runId, c);
                }
                // Record run completion in Prometheus metrics.
                loadTestMetrics.recordRunCompleted();
            } catch (Exception updateEx) {
                LOG.log(Level.WARNING, "Failed to update run status for " + runId, updateEx);
            }
        });

        return toDto(entity);
    }

    // -------------------------------------------------------------------------
    // Get status
    // -------------------------------------------------------------------------

    /**
     * Returns the current status of a run.
     *
     * @param runId the run identifier; must not be null
     * @return the run DTO, or empty if not found
     */
    public Optional<TestRunDto> getRunStatus(String runId) {
        return runRepository.findById(runId).map(this::toDto);
    }

    // -------------------------------------------------------------------------
    // Stop
    // -------------------------------------------------------------------------

    /**
     * Requests a graceful stop of the given run.
     *
     * <p>Delegates to the engine and transitions the run to {@code STOPPING} in the DB.
     * Returns {@code false} if the run does not exist.
     *
     * @param runId the run identifier; must not be null
     * @return {@code true} if the run was found and stop was requested
     */
    public boolean stopRun(String runId) {
        if (runRepository.findById(runId).isEmpty()) {
            return false;
        }
        engineService.stopRun(runId);
        return updateStatus(runId, TestRunContext.TestRunStatus.STOPPING);
    }

    /**
     * Requests an immediate (forced) stop of the given run.
     *
     * <p>Delegates to the engine and transitions directly to {@code STOPPED} in the DB.
     *
     * @param runId the run identifier; must not be null
     * @return {@code true} if the run was found and stopped
     */
    public boolean stopRunNow(String runId) {
        if (runRepository.findById(runId).isEmpty()) {
            return false;
        }
        engineService.stopRunNow(runId);
        return finishRun(runId, TestRunContext.TestRunStatus.STOPPED);
    }

    // -------------------------------------------------------------------------
    // Metrics
    // -------------------------------------------------------------------------

    /**
     * Returns the latest metrics snapshot for a run.
     *
     * <p>Reads the most-recently delivered {@link SampleBucket} from the in-memory
     * cache populated by the broker subscription. Returns an empty snapshot when no
     * samples have been collected yet.
     *
     * @param runId the run identifier; must not be null
     * @return the metrics DTO, or empty if the run does not exist
     */
    public Optional<MetricsDto> getMetrics(String runId) {
        if (runRepository.findById(runId).isEmpty()) {
            return Optional.empty();
        }
        SampleBucket latest = latestBuckets.get(runId);
        if (latest == null) {
            return Optional.of(MetricsDto.empty(runId));
        }
        return Optional.of(toMetricsDto(runId, latest));
    }

    // -------------------------------------------------------------------------
    // SLA
    // -------------------------------------------------------------------------

    public Optional<com.b3meter.engine.service.SlaEvaluator.SlaStatus> getSlaStatus(String runId) {
        var evaluator = slaEvaluators.get(runId);
        if (evaluator == null) return Optional.empty();
        return Optional.of(evaluator.getStatus());
    }

    // -------------------------------------------------------------------------
    // List
    // -------------------------------------------------------------------------

    /**
     * Lists all runs, optionally filtered by plan identifier.
     *
     * @param planId filter by plan; if null, returns all runs across all plans
     * @return list of run DTOs, possibly empty
     */
    public List<TestRunDto> listRuns(String planId) {
        List<TestRunEntity> entities = planId != null
                ? runRepository.findByPlanId(planId)
                : runRepository.findAll();
        return entities.stream().map(this::toDto).toList();
    }

    // -------------------------------------------------------------------------
    // Lifecycle helpers
    // -------------------------------------------------------------------------

    private boolean finishRun(String runId, TestRunContext.TestRunStatus terminalStatus) {
        if (runRepository.findById(runId).isEmpty()) {
            return false;
        }
        TestRunContext context = TestRunContextRegistry.get(runId);
        if (context != null) {
            context.setStatus(terminalStatus);
            context.setRunning(false);
            TestRunContextRegistry.remove(runId);
        }

        // Unsubscribe broker consumer.
        SampleBucketConsumer consumer = consumers.remove(runId);
        if (consumer != null) {
            broker.unsubscribe(runId, consumer);
        }

        runRepository.updateStatus(runId, terminalStatus.name());
        loadTestMetrics.recordRunCompleted();
        return true;
    }

    private boolean updateStatus(String runId, TestRunContext.TestRunStatus newStatus) {
        if (runRepository.findById(runId).isEmpty()) {
            return false;
        }
        TestRunContext context = TestRunContextRegistry.get(runId);
        if (context != null) {
            context.setStatus(newStatus);
        }
        runRepository.updateStatus(runId, newStatus.name());
        return true;
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private TestRunDto toDto(TestRunEntity entity) {
        return new TestRunDto(
                entity.id(),
                entity.planId(),
                entity.status(),
                entity.startedAt(),
                entity.endedAt(),
                entity.totalSamples(),
                entity.errorCount(),
                entity.ownerId()
        );
    }

    private MetricsDto toMetricsDto(String runId, SampleBucket bucket) {
        return new MetricsDto(
                runId,
                bucket.timestamp(),
                bucket.samplerLabel(),
                bucket.sampleCount(),
                bucket.errorCount(),
                bucket.avgResponseTime(),
                bucket.minResponseTime(),
                bucket.maxResponseTime(),
                bucket.percentile90(),
                bucket.percentile95(),
                bucket.percentile99(),
                bucket.samplesPerSecond(),
                bucket.errorPercent()
        );
    }

}
