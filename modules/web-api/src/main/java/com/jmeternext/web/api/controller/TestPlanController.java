package com.jmeternext.web.api.controller;

import com.jmeternext.web.api.controller.dto.CreatePlanRequest;
import com.jmeternext.web.api.controller.dto.TestPlanDto;
import com.jmeternext.web.api.controller.dto.TestPlanRevisionDto;
import com.jmeternext.web.api.controller.dto.UpdatePlanRequest;
import com.jmeternext.web.api.service.TestPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * REST controller for test plan CRUD, JMX import/export, and revision management.
 *
 * <p>All endpoints are under {@code /api/v1/plans}. Security is handled globally
 * by {@link com.jmeternext.web.api.config.SecurityConfig} — all requests are
 * permitted in single-user desktop mode.
 */
@RestController
@RequestMapping("/api/v1/plans")
@Tag(name = "Test Plans", description = "CRUD operations, JMX import/export, and revision history")
public class TestPlanController {

    private final TestPlanService service;

    public TestPlanController(TestPlanService service) {
        this.service = service;
    }

    @Operation(summary = "Create a test plan", description = "Creates a new test plan with the given name and tree data")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Plan created"),
            @ApiResponse(responseCode = "400", description = "Invalid request — name is blank")
    })
    @PostMapping
    public ResponseEntity<TestPlanDto> create(@RequestBody CreatePlanRequest request) {
        try {
            TestPlanDto plan = service.create(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(plan);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "List all plans", description = "Returns all non-deleted test plans")
    @GetMapping
    public List<TestPlanDto> list() {
        return service.listAll();
    }

    @Operation(summary = "Get plan by ID", description = "Returns a single test plan by its identifier")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plan found"),
            @ApiResponse(responseCode = "404", description = "Plan not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<TestPlanDto> getById(@PathVariable String id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Update a plan", description = "Updates name or tree data and records a new revision")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plan updated"),
            @ApiResponse(responseCode = "404", description = "Plan not found")
    })
    @PutMapping("/{id}")
    public ResponseEntity<TestPlanDto> update(
            @PathVariable String id,
            @RequestBody UpdatePlanRequest request) {
        return service.update(id, request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Delete a plan", description = "Soft-deletes a test plan")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Plan deleted"),
            @ApiResponse(responseCode = "404", description = "Plan not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        boolean deleted = service.delete(id);
        return deleted
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @Operation(summary = "Import JMX file", description = "Uploads a .jmx file and creates a new test plan from it")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Plan created from JMX"),
            @ApiResponse(responseCode = "413", description = "File exceeds 50 MB limit")
    })
    @PostMapping("/import")
    public ResponseEntity<TestPlanDto> importJmx(@RequestParam("file") MultipartFile file) {
        try {
            TestPlanDto plan = service.importJmx(file);
            return ResponseEntity.status(HttpStatus.CREATED).body(plan);
        } catch (TestPlanService.FileTooLargeException e) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
    }

    @Operation(summary = "Export as JMX", description = "Downloads the plan as an Apache JMeter .jmx file")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "JMX file returned"),
            @ApiResponse(responseCode = "404", description = "Plan not found")
    })
    @GetMapping("/{id}/export")
    public ResponseEntity<Resource> exportJmx(@PathVariable String id) {
        return service.exportJmx(id)
                .map(xml -> {
                    byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
                    Resource resource = new ByteArrayResource(bytes);
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_XML);
                    headers.setContentDisposition(
                            ContentDisposition.attachment()
                                    .filename(id + ".jmx")
                                    .build()
                    );
                    return ResponseEntity.ok()
                            .headers(headers)
                            .<Resource>body(resource);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get revision history", description = "Returns all revisions for a plan, most recent first")
    @GetMapping("/{id}/revisions")
    public List<TestPlanRevisionDto> revisions(@PathVariable String id) {
        return service.findRevisions(id);
    }

    @Operation(summary = "Restore a revision", description = "Restores a plan to the tree data of a specific revision")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plan restored"),
            @ApiResponse(responseCode = "404", description = "Plan or revision not found")
    })
    @PostMapping("/{id}/revisions/{revisionNumber}/restore")
    public ResponseEntity<TestPlanDto> restore(
            @PathVariable String id,
            @PathVariable int revisionNumber) {
        return service.restore(id, revisionNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
