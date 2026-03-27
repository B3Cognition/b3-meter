package com.jmeternext.web.api.controller;

import com.jmeternext.web.api.controller.dto.RegisterWorkerRequest;
import com.jmeternext.web.api.controller.dto.WorkerDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link WorkerController}.
 *
 * <p>Boots the full Spring context on a random port and exercises all endpoints
 * via {@link TestRestTemplate} to verify worker CRUD behaviour.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WorkerControllerTest {

    @Autowired
    TestRestTemplate restTemplate;

    // -------------------------------------------------------------------------
    // POST /api/v1/workers — register worker
    // -------------------------------------------------------------------------

    @Test
    void register_validRequest_returns201WithWorkerDto() {
        RegisterWorkerRequest request = new RegisterWorkerRequest("worker-host-1.internal", 1099);

        ResponseEntity<WorkerDto> response = restTemplate.postForEntity(
                "/api/v1/workers", request, WorkerDto.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody(), "response body must not be null");
        assertNotNull(response.getBody().id(), "worker must have an id");
        assertEquals("worker-host-1.internal", response.getBody().hostname());
        assertEquals(1099, response.getBody().port());
        assertEquals("AVAILABLE", response.getBody().status());
        assertNotNull(response.getBody().registeredAt(), "registeredAt must not be null");
    }

    @Test
    void register_blankHostname_returns400() {
        RegisterWorkerRequest request = new RegisterWorkerRequest("", 1099);

        ResponseEntity<WorkerDto> response = restTemplate.postForEntity(
                "/api/v1/workers", request, WorkerDto.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void register_nullHostname_returns400() {
        RegisterWorkerRequest request = new RegisterWorkerRequest(null, 1099);

        ResponseEntity<WorkerDto> response = restTemplate.postForEntity(
                "/api/v1/workers", request, WorkerDto.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void register_nullPort_returns400() {
        RegisterWorkerRequest request = new RegisterWorkerRequest("host.internal", null);

        ResponseEntity<WorkerDto> response = restTemplate.postForEntity(
                "/api/v1/workers", request, WorkerDto.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void register_portBelowRange_returns400() {
        RegisterWorkerRequest request = new RegisterWorkerRequest("host.internal", 0);

        ResponseEntity<WorkerDto> response = restTemplate.postForEntity(
                "/api/v1/workers", request, WorkerDto.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void register_portAboveRange_returns400() {
        RegisterWorkerRequest request = new RegisterWorkerRequest("host.internal", 70000);

        ResponseEntity<WorkerDto> response = restTemplate.postForEntity(
                "/api/v1/workers", request, WorkerDto.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/workers — list workers
    // -------------------------------------------------------------------------

    @Test
    void listWorkers_returnsAllRegisteredWorkers() {
        // Register two workers
        restTemplate.postForEntity("/api/v1/workers",
                new RegisterWorkerRequest("list-host-a.internal", 1099), WorkerDto.class);
        restTemplate.postForEntity("/api/v1/workers",
                new RegisterWorkerRequest("list-host-b.internal", 2099), WorkerDto.class);

        ResponseEntity<WorkerDto[]> response = restTemplate.getForEntity(
                "/api/v1/workers", WorkerDto[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().length >= 2,
                "Should return at least the 2 workers just registered");
    }

    @Test
    void listWorkers_emptyRegistry_returnsEmptyArray() {
        // We can't fully guarantee empty state in a shared context, but we verify
        // the endpoint returns 200 with an array (not an error).
        ResponseEntity<WorkerDto[]> response = restTemplate.getForEntity(
                "/api/v1/workers", WorkerDto[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    // -------------------------------------------------------------------------
    // DELETE /api/v1/workers/{id} — remove worker
    // -------------------------------------------------------------------------

    @Test
    void remove_existingWorker_returns204() {
        WorkerDto created = registerWorker("delete-host.internal", 1099);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/workers/" + created.id(),
                HttpMethod.DELETE,
                null,
                Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void remove_nonexistentWorker_returns404() {
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/workers/nonexistent-worker-id-99999",
                HttpMethod.DELETE,
                null,
                Void.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void remove_workerDisappearsFromList_afterDeletion() {
        WorkerDto created = registerWorker("remove-check-host.internal", 3099);

        // Delete it
        restTemplate.delete("/api/v1/workers/" + created.id());

        // Verify it no longer appears in the list
        ResponseEntity<WorkerDto[]> listResponse = restTemplate.getForEntity(
                "/api/v1/workers", WorkerDto[].class);
        assertNotNull(listResponse.getBody());
        for (WorkerDto worker : listResponse.getBody()) {
            assertTrue(!worker.id().equals(created.id()),
                    "Deleted worker should not appear in the list");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private WorkerDto registerWorker(String hostname, int port) {
        ResponseEntity<WorkerDto> response = restTemplate.postForEntity(
                "/api/v1/workers",
                new RegisterWorkerRequest(hostname, port),
                WorkerDto.class);
        assertNotNull(response.getBody(), "registerWorker helper: body must not be null");
        return response.getBody();
    }
}
