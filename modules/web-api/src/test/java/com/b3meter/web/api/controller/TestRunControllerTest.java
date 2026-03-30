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

import com.b3meter.web.api.controller.dto.CreatePlanRequest;
import com.b3meter.web.api.controller.dto.MetricsDto;
import com.b3meter.web.api.controller.dto.StartRunRequest;
import com.b3meter.web.api.controller.dto.TestPlanDto;
import com.b3meter.web.api.controller.dto.TestRunDto;
import com.b3meter.engine.service.TestRunContextRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link TestRunController}.
 *
 * <p>Boots the full Spring context on a random port and exercises all endpoints
 * via {@link TestRestTemplate} to verify the test run lifecycle.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TestRunControllerTest {

    @Autowired
    TestRestTemplate restTemplate;

    @AfterEach
    void clearRegistry() {
        TestRunContextRegistry.clear();
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/runs — start run
    // -------------------------------------------------------------------------

    @Test
    void startRun_validPlanId_returns202WithRunId() {
        String planId = createPlan("Run Test Plan");

        StartRunRequest request = new StartRunRequest(planId, 1, 0L, null, null, null, null, null);
        ResponseEntity<TestRunDto> response = restTemplate.postForEntity(
                "/api/v1/runs", request, TestRunDto.class);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNotNull(response.getBody(), "response body must not be null");
        assertNotNull(response.getBody().id(), "returned run must have an id");
        assertEquals(planId, response.getBody().planId());
    }

    @Test
    void startRun_blankPlanId_returns400() {
        StartRunRequest request = new StartRunRequest("", 1, 0L, null, null, null, null, null);
        ResponseEntity<TestRunDto> response = restTemplate.postForEntity(
                "/api/v1/runs", request, TestRunDto.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void startRun_nullPlanId_returns400() {
        StartRunRequest request = new StartRunRequest(null, null, null, null, null, null, null, null);
        ResponseEntity<TestRunDto> response = restTemplate.postForEntity(
                "/api/v1/runs", request, TestRunDto.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void startRun_defaultVirtualUsersAndDuration_returns202() {
        String planId = createPlan("Defaults Plan");

        // null virtualUsers and durationSeconds should use defaults
        StartRunRequest request = new StartRunRequest(planId, null, null, null, null, null, null, null);
        ResponseEntity<TestRunDto> response = restTemplate.postForEntity(
                "/api/v1/runs", request, TestRunDto.class);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().id());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/runs/{runId} — get status
    // -------------------------------------------------------------------------

    @Test
    void getRunStatus_existingRun_returns200WithStatus() {
        String planId = createPlan("Status Plan");
        TestRunDto created = startRun(planId);

        ResponseEntity<TestRunDto> response = restTemplate.getForEntity(
                "/api/v1/runs/" + created.id(), TestRunDto.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(created.id(), response.getBody().id());
        assertNotNull(response.getBody().status(), "status must not be null");
    }

    @Test
    void getRunStatus_nonexistentRun_returns404() {
        ResponseEntity<TestRunDto> response = restTemplate.getForEntity(
                "/api/v1/runs/nonexistent-run-id-99999", TestRunDto.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/runs/{runId}/stop — graceful stop
    // -------------------------------------------------------------------------

    @Test
    void stopRun_existingRun_returns200() {
        String planId = createPlan("Stop Plan");
        TestRunDto run = startRun(planId);

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/v1/runs/" + run.id() + "/stop", null, Void.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void stopRun_nonexistentRun_returns404() {
        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/v1/runs/no-such-run/stop", null, Void.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/runs/{runId}/stop-now — immediate stop
    // -------------------------------------------------------------------------

    @Test
    void stopRunNow_existingRun_returns200() {
        String planId = createPlan("StopNow Plan");
        TestRunDto run = startRun(planId);

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/v1/runs/" + run.id() + "/stop-now", null, Void.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void stopRunNow_nonexistentRun_returns404() {
        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/v1/runs/no-such-run-now/stop-now", null, Void.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/runs/{runId}/metrics — latest metrics
    // -------------------------------------------------------------------------

    @Test
    void getMetrics_existingRun_returns200WithMetrics() {
        String planId = createPlan("Metrics Plan");
        TestRunDto run = startRun(planId);

        ResponseEntity<MetricsDto> response = restTemplate.getForEntity(
                "/api/v1/runs/" + run.id() + "/metrics", MetricsDto.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(run.id(), response.getBody().runId());
    }

    @Test
    void getMetrics_nonexistentRun_returns404() {
        ResponseEntity<MetricsDto> response = restTemplate.getForEntity(
                "/api/v1/runs/no-such-metrics-run/metrics", MetricsDto.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/runs — list runs
    // -------------------------------------------------------------------------

    @Test
    void listRuns_filterByPlanId_returnsOnlyMatchingRuns() {
        String planId = createPlan("List Plan");
        startRun(planId);
        startRun(planId);

        ResponseEntity<TestRunDto[]> response = restTemplate.getForEntity(
                "/api/v1/runs?planId=" + planId, TestRunDto[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().length >= 2,
                "Should return at least 2 runs for the plan");
    }

    @Test
    void listRuns_noPlanFilter_returnsAllRuns() {
        String planId = createPlan("All Runs Plan");
        startRun(planId);

        ResponseEntity<TestRunDto[]> response = restTemplate.getForEntity(
                "/api/v1/runs", TestRunDto[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().length >= 1,
                "Should return at least 1 run when no filter is applied");
    }

    @Test
    void listRuns_unknownPlanId_returnsEmptyList() {
        ResponseEntity<TestRunDto[]> response = restTemplate.getForEntity(
                "/api/v1/runs?planId=nonexistent-plan-id", TestRunDto[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().length,
                "Unknown plan should yield an empty run list");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a test plan and returns its ID.
     * The plan must exist so the FK constraint on test_runs is satisfied.
     */
    private String createPlan(String name) {
        ResponseEntity<TestPlanDto> response = restTemplate.postForEntity(
                "/api/v1/plans",
                new CreatePlanRequest(name, "owner-runs-test"),
                TestPlanDto.class);
        assertNotNull(response.getBody(), "createPlan helper: body must not be null");
        return response.getBody().id();
    }

    private TestRunDto startRun(String planId) {
        ResponseEntity<TestRunDto> response = restTemplate.postForEntity(
                "/api/v1/runs",
                new StartRunRequest(planId, 1, 0L, null, null, null, null, null),
                TestRunDto.class);
        assertNotNull(response.getBody(), "startRun helper: body must not be null");
        return response.getBody();
    }
}
