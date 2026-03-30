package com.jmeternext.web.api.controller;

import com.jmeternext.web.api.controller.dto.CreatePlanRequest;
import com.jmeternext.web.api.controller.dto.TestPlanDto;
import com.jmeternext.web.api.controller.dto.TestPlanRevisionDto;
import com.jmeternext.web.api.controller.dto.UpdatePlanRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link TestPlanController}.
 *
 * <p>Boots the full Spring context on a random port and exercises all endpoints
 * via {@link TestRestTemplate} to verify end-to-end CRUD, JMX import/export,
 * and revision history.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TestPlanControllerTest {

    @Autowired
    TestRestTemplate restTemplate;

    // -------------------------------------------------------------------------
    // POST /api/v1/plans — create
    // -------------------------------------------------------------------------

    @Test
    void createPlan_returns201WithId() {
        CreatePlanRequest request = new CreatePlanRequest("My Plan", "owner-1");

        ResponseEntity<TestPlanDto> response = restTemplate.postForEntity(
                "/api/v1/plans", request, TestPlanDto.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody(), "response body must not be null");
        assertNotNull(response.getBody().id(), "returned plan must have an id");
        assertEquals("My Plan", response.getBody().name());
    }

    @Test
    void createPlan_blankName_returns400() {
        CreatePlanRequest request = new CreatePlanRequest("", "owner-1");

        ResponseEntity<TestPlanDto> response = restTemplate.postForEntity(
                "/api/v1/plans", request, TestPlanDto.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void createPlan_nullName_returns400() {
        CreatePlanRequest request = new CreatePlanRequest(null, "owner-1");

        ResponseEntity<TestPlanDto> response = restTemplate.postForEntity(
                "/api/v1/plans", request, TestPlanDto.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/plans/{id} — getById
    // -------------------------------------------------------------------------

    @Test
    void getById_existingPlan_returns200WithPlanData() {
        TestPlanDto created = createPlan("GetById Plan", "owner-2");

        ResponseEntity<TestPlanDto> response = restTemplate.getForEntity(
                "/api/v1/plans/" + created.id(), TestPlanDto.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(created.id(), response.getBody().id());
        assertEquals("GetById Plan", response.getBody().name());
    }

    @Test
    void getById_nonexistentPlan_returns404() {
        ResponseEntity<TestPlanDto> response = restTemplate.getForEntity(
                "/api/v1/plans/nonexistent-id-12345", TestPlanDto.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/plans — list all
    // -------------------------------------------------------------------------

    @Test
    void listAll_returnsAllActivePlans() {
        createPlan("List Plan A", "owner-3");
        createPlan("List Plan B", "owner-3");

        ResponseEntity<TestPlanDto[]> response = restTemplate.getForEntity(
                "/api/v1/plans", TestPlanDto[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().length >= 2, "Should return at least 2 plans");
    }

    // -------------------------------------------------------------------------
    // PUT /api/v1/plans/{id} — update
    // -------------------------------------------------------------------------

    @Test
    void updatePlan_returns200AndCreatesRevision() {
        TestPlanDto created = createPlan("Original Name", "owner-4");
        UpdatePlanRequest update = new UpdatePlanRequest("Updated Name", "{\"v\":2}", "alice");

        ResponseEntity<TestPlanDto> response = restTemplate.exchange(
                "/api/v1/plans/" + created.id(),
                HttpMethod.PUT,
                new HttpEntity<>(update),
                TestPlanDto.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Updated Name", response.getBody().name());

        // Verify revision was created
        ResponseEntity<TestPlanRevisionDto[]> revisions = restTemplate.getForEntity(
                "/api/v1/plans/" + created.id() + "/revisions", TestPlanRevisionDto[].class);
        assertNotNull(revisions.getBody());
        assertEquals(1, revisions.getBody().length, "One revision must be created on update");
    }

    @Test
    void updatePlan_nonexistentPlan_returns404() {
        UpdatePlanRequest update = new UpdatePlanRequest("New Name", null, null);

        ResponseEntity<TestPlanDto> response = restTemplate.exchange(
                "/api/v1/plans/nonexistent-update-id",
                HttpMethod.PUT,
                new HttpEntity<>(update),
                TestPlanDto.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // DELETE /api/v1/plans/{id} — soft delete
    // -------------------------------------------------------------------------

    @Test
    void deletePlan_returns204AndSubsequentGetReturns404() {
        TestPlanDto created = createPlan("Plan To Delete", "owner-5");

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/api/v1/plans/" + created.id(),
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void.class
        );

        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());

        // Verify the plan is no longer accessible
        ResponseEntity<TestPlanDto> getResponse = restTemplate.getForEntity(
                "/api/v1/plans/" + created.id(), TestPlanDto.class);
        assertEquals(HttpStatus.NOT_FOUND, getResponse.getStatusCode());
    }

    @Test
    void deletePlan_deletedPlanNotInListAll() {
        TestPlanDto created = createPlan("Plan To Delete From List", "owner-6");
        restTemplate.delete("/api/v1/plans/" + created.id());

        ResponseEntity<TestPlanDto[]> listResponse = restTemplate.getForEntity(
                "/api/v1/plans", TestPlanDto[].class);
        assertNotNull(listResponse.getBody());
        boolean anyMatch = java.util.Arrays.stream(listResponse.getBody())
                .anyMatch(p -> p.id().equals(created.id()));
        assertFalse(anyMatch, "Soft-deleted plan must not appear in list");
    }

    @Test
    void deletePlan_nonexistentPlan_returns404() {
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/plans/nonexistent-delete-id",
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/plans/import — JMX import
    // -------------------------------------------------------------------------

    @Test
    void importJmx_validFile_returns201WithPlan() {
        String jmxContent = minimalJmx("Import Test Plan");
        ResponseEntity<TestPlanDto> response = uploadJmx("test-plan.jmx", jmxContent);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().id(), "Imported plan must have an id");
        assertEquals("test-plan", response.getBody().name());
    }

    @Test
    void importJmx_fileOver50MB_rejectedByServer() {
        // Build a byte array of exactly 50 MB + 1 byte to trigger the size enforcement.
        // Spring Boot's multipart limit causes the server to close the connection before
        // the client finishes uploading, so the client observes either:
        //   (a) HTTP 413 Payload Too Large, or
        //   (b) a connection-reset I/O exception (server closed socket mid-write)
        // Both outcomes confirm the server enforces the 50 MB limit.
        int sizeOver50Mb = 50 * 1024 * 1024 + 1;
        byte[] largeContent = new byte[sizeOver50Mb];
        java.util.Arrays.fill(largeContent, (byte) 'x');

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource fileResource = new ByteArrayResource(largeContent) {
            @Override
            public String getFilename() {
                return "large.jmx";
            }
        };
        body.add("file", fileResource);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        boolean serverEnforcedLimit = false;
        try {
            ResponseEntity<TestPlanDto> response = restTemplate.postForEntity(
                    "/api/v1/plans/import", requestEntity, TestPlanDto.class);
            // If we get a response, it must be 413
            serverEnforcedLimit = response.getStatusCode() == HttpStatus.PAYLOAD_TOO_LARGE;
        } catch (org.springframework.web.client.ResourceAccessException e) {
            // Server closed connection mid-upload — the limit IS enforced
            serverEnforcedLimit = true;
        }

        assertTrue(serverEnforcedLimit,
                "Server must reject uploads over 50 MB (expect 413 or connection closed)");
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/plans/{id}/export — JMX export
    // -------------------------------------------------------------------------

    @Test
    void exportJmx_existingPlan_returns200WithXmlContent() {
        String jmxContent = minimalJmx("Export Test Plan");
        ResponseEntity<TestPlanDto> importResponse = uploadJmx("export-test.jmx", jmxContent);
        assertNotNull(importResponse.getBody());
        String planId = importResponse.getBody().id();

        ResponseEntity<String> exportResponse = restTemplate.getForEntity(
                "/api/v1/plans/" + planId + "/export", String.class);

        assertEquals(HttpStatus.OK, exportResponse.getStatusCode());
        assertNotNull(exportResponse.getBody());
        assertTrue(exportResponse.getBody().contains("jmeterTestPlan"),
                "Exported content must be JMX XML");
    }

    @Test
    void exportJmx_nonexistentPlan_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/plans/nonexistent-export-id/export", String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/plans/{id}/revisions — revision list
    // -------------------------------------------------------------------------

    @Test
    void revisions_afterUpdate_returnsRevisionList() {
        TestPlanDto plan = createPlan("Revision Plan", "owner-7");

        // Two updates = two revisions
        restTemplate.exchange(
                "/api/v1/plans/" + plan.id(),
                HttpMethod.PUT,
                new HttpEntity<>(new UpdatePlanRequest("Rev 1", "{\"v\":1}", "alice")),
                TestPlanDto.class
        );
        restTemplate.exchange(
                "/api/v1/plans/" + plan.id(),
                HttpMethod.PUT,
                new HttpEntity<>(new UpdatePlanRequest("Rev 2", "{\"v\":2}", "bob")),
                TestPlanDto.class
        );

        ResponseEntity<TestPlanRevisionDto[]> response = restTemplate.getForEntity(
                "/api/v1/plans/" + plan.id() + "/revisions", TestPlanRevisionDto[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().length, "Two updates must produce two revisions");
    }

    @Test
    void revisions_noPriorUpdates_returnsEmptyList() {
        TestPlanDto plan = createPlan("No Revisions Plan", "owner-8");

        ResponseEntity<TestPlanRevisionDto[]> response = restTemplate.getForEntity(
                "/api/v1/plans/" + plan.id() + "/revisions", TestPlanRevisionDto[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().length, "Freshly created plan must have zero revisions");
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/plans/{id}/revisions/{n}/restore
    // -------------------------------------------------------------------------

    @Test
    void restore_validRevision_restoresPlanTreeData() {
        TestPlanDto plan = createPlan("Restore Plan", "owner-9");

        // Create revision by updating
        restTemplate.exchange(
                "/api/v1/plans/" + plan.id(),
                HttpMethod.PUT,
                new HttpEntity<>(new UpdatePlanRequest(null, "{\"v\":\"changed\"}", "alice")),
                TestPlanDto.class
        );

        // Restore revision 1 (which contains the original treeData = "{}")
        ResponseEntity<TestPlanDto> restoreResponse = restTemplate.postForEntity(
                "/api/v1/plans/" + plan.id() + "/revisions/1/restore",
                null,
                TestPlanDto.class
        );

        assertEquals(HttpStatus.OK, restoreResponse.getStatusCode());
        assertNotNull(restoreResponse.getBody());
        assertEquals("{}", restoreResponse.getBody().treeData(),
                "Restored plan must have the revision's tree data");
    }

    @Test
    void restore_nonexistentPlan_returns404() {
        ResponseEntity<TestPlanDto> response = restTemplate.postForEntity(
                "/api/v1/plans/no-such-plan/revisions/1/restore",
                null,
                TestPlanDto.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void restore_nonexistentRevision_returns404() {
        TestPlanDto plan = createPlan("Restore 404 Plan", "owner-10");

        ResponseEntity<TestPlanDto> response = restTemplate.postForEntity(
                "/api/v1/plans/" + plan.id() + "/revisions/999/restore",
                null,
                TestPlanDto.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private TestPlanDto createPlan(String name, String ownerId) {
        ResponseEntity<TestPlanDto> response = restTemplate.postForEntity(
                "/api/v1/plans", new CreatePlanRequest(name, ownerId), TestPlanDto.class);
        assertNotNull(response.getBody(), "createPlan helper: response body must not be null");
        return response.getBody();
    }

    private ResponseEntity<TestPlanDto> uploadJmx(String filename, String content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        ByteArrayResource fileResource = new ByteArrayResource(contentBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        return restTemplate.postForEntity("/api/v1/plans/import", requestEntity, TestPlanDto.class);
    }

    private String minimalJmx(String planName) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<jmeterTestPlan version=\"1.2\" properties=\"5.0\" jmeter=\"5.6.3\">\n"
                + "  <hashTree>\n"
                + "    <TestPlan guiclass=\"TestPlanGui\" testclass=\"TestPlan\" testname=\""
                + planName + "\" enabled=\"true\"/>\n"
                + "  </hashTree>\n"
                + "</jmeterTestPlan>\n";
    }
}
