package com.jmeternext.web.api.service;

import com.jmeternext.web.api.controller.dto.CreatePlanRequest;
import com.jmeternext.web.api.controller.dto.TestPlanDto;
import com.jmeternext.web.api.controller.dto.TestPlanRevisionDto;
import com.jmeternext.web.api.controller.dto.UpdatePlanRequest;
import com.jmeternext.web.api.repository.TestPlanEntity;
import com.jmeternext.web.api.repository.TestPlanRepository;
import com.jmeternext.web.api.repository.TestPlanRevisionEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Business logic for test plan CRUD, JMX import/export, and revision history.
 *
 * <p>JMX import stores the raw XML as {@code tree_data}; full XStream parsing
 * is deferred to a later integration task (see T014 task notes).
 *
 * <p>JMX export reads {@code tree_data} and returns it verbatim as XML.
 * When the stored data is plain JSON (not XML), a minimal JMX wrapper is generated
 * so the export contract always returns valid XML.
 */
@Service
public class TestPlanService {

    /** Maximum allowed JMX upload size: 50 MB. */
    static final long MAX_JMX_SIZE_BYTES = 50L * 1024L * 1024L;

    /** Default owner used when no owner is specified at plan creation time. */
    private static final String DEFAULT_OWNER = "system";

    private final TestPlanRepository repository;

    public TestPlanService(TestPlanRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates a new test plan with an empty tree.
     *
     * @param request the creation request; must not be null
     * @return the persisted plan as a DTO
     * @throws IllegalArgumentException if {@code request.name()} is blank
     */
    public TestPlanDto create(CreatePlanRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Plan name must not be blank");
        }
        String ownerId = request.ownerId() != null ? request.ownerId() : DEFAULT_OWNER;
        Instant now = Instant.now();
        TestPlanEntity entity = new TestPlanEntity(
                UUID.randomUUID().toString(),
                request.name(),
                ownerId,
                "{}",
                now,
                now,
                null
        );
        TestPlanEntity saved = repository.save(entity);
        return toDto(saved);
    }

    /**
     * Returns all non-deleted test plans.
     *
     * @return list of plans, possibly empty
     */
    public List<TestPlanDto> listAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    /**
     * Finds a plan by its identifier.
     *
     * @param id the plan identifier; must not be null
     * @return the plan, or empty if not found or soft-deleted
     */
    public Optional<TestPlanDto> findById(String id) {
        return repository.findById(id).map(this::toDto);
    }

    /**
     * Updates a plan's name and/or tree data, and records a revision.
     *
     * @param id      the plan identifier; must not be null
     * @param request the update request; must not be null
     * @return the updated plan, or empty if the plan does not exist
     */
    public Optional<TestPlanDto> update(String id, UpdatePlanRequest request) {
        Optional<TestPlanEntity> existing = repository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        TestPlanEntity current = existing.get();

        String newName = request.name() != null ? request.name() : current.name();
        String newTreeData = request.treeData() != null ? request.treeData() : current.treeData();
        Instant now = Instant.now();

        TestPlanEntity updated = new TestPlanEntity(
                current.id(),
                newName,
                current.ownerId(),
                newTreeData,
                current.createdAt(),
                now,
                null
        );
        repository.save(updated);

        int nextRevisionNumber = repository.findRevisions(id).size() + 1;
        String author = request.author() != null ? request.author() : DEFAULT_OWNER;
        TestPlanRevisionEntity revision = new TestPlanRevisionEntity(
                UUID.randomUUID().toString(),
                id,
                nextRevisionNumber,
                current.treeData(),
                now,
                author
        );
        repository.saveRevision(revision);

        return Optional.of(toDto(updated));
    }

    /**
     * Soft-deletes a plan.
     *
     * @param id the plan identifier; must not be null
     * @return {@code true} if the plan existed and was deleted, {@code false} if not found
     */
    public boolean delete(String id) {
        if (repository.findById(id).isEmpty()) {
            return false;
        }
        repository.deleteById(id);
        return true;
    }

    /**
     * Imports a JMX file, storing its raw XML content as the plan's tree data.
     *
     * @param file the uploaded JMX file; must not be null
     * @return the created plan as a DTO
     * @throws IllegalArgumentException if the file exceeds {@link #MAX_JMX_SIZE_BYTES}
     * @throws IllegalStateException    if reading the file fails
     */
    public TestPlanDto importJmx(MultipartFile file) {
        if (file.getSize() > MAX_JMX_SIZE_BYTES) {
            throw new FileTooLargeException("JMX file exceeds 50 MB limit");
        }
        String rawXml;
        try {
            rawXml = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read uploaded JMX file", e);
        }

        // Validate JMX using StAX-based JmxTreeWalker (no XStream, no class resolution)
        // This parses JMX natively without needing old jMeter classes on classpath
        try {
            var planNode = com.jmeternext.engine.service.plan.JmxTreeWalker.parse(rawXml);
            if (planNode == null || planNode.getChildren().isEmpty()) {
                throw new IllegalArgumentException("JMX file rejected: empty or invalid test plan");
            }
        } catch (com.jmeternext.engine.service.plan.JmxParseException e) {
            throw new IllegalArgumentException("JMX file rejected: malformed XML — " + e.getMessage(), e);
        }

        String planName = derivePlanName(file.getOriginalFilename());
        Instant now = Instant.now();
        TestPlanEntity entity = new TestPlanEntity(
                UUID.randomUUID().toString(),
                planName,
                DEFAULT_OWNER,
                rawXml,
                now,
                now,
                null
        );
        return toDto(repository.save(entity));
    }

    /**
     * Exports a plan's tree data as JMX XML.
     *
     * @param id the plan identifier; must not be null
     * @return the raw XML content, or empty if the plan does not exist
     */
    public Optional<String> exportJmx(String id) {
        return repository.findById(id).map(entity -> {
            String treeData = entity.treeData();
            if (treeData == null || treeData.isBlank()) {
                return emptyJmxXml(entity.name());
            }
            // If stored data looks like XML already, return it directly.
            // Otherwise wrap in a minimal JMX envelope.
            if (treeData.trim().startsWith("<")) {
                return treeData;
            }
            return emptyJmxXml(entity.name());
        });
    }

    /**
     * Returns all revisions for a plan, most recent first.
     *
     * @param id the plan identifier; must not be null
     * @return list of revisions, possibly empty
     */
    public List<TestPlanRevisionDto> findRevisions(String id) {
        return repository.findRevisions(id).stream().map(this::toRevisionDto).toList();
    }

    /**
     * Restores a plan to a specific revision's tree data.
     *
     * @param id             the plan identifier; must not be null
     * @param revisionNumber the revision number to restore; must be positive
     * @return the restored plan, or empty if the plan or revision does not exist
     */
    public Optional<TestPlanDto> restore(String id, int revisionNumber) {
        Optional<TestPlanEntity> existing = repository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        Optional<TestPlanRevisionEntity> target = repository.findRevisions(id).stream()
                .filter(r -> r.revisionNumber() == revisionNumber)
                .findFirst();
        if (target.isEmpty()) {
            return Optional.empty();
        }

        TestPlanEntity current = existing.get();
        Instant now = Instant.now();
        TestPlanEntity restored = new TestPlanEntity(
                current.id(),
                current.name(),
                current.ownerId(),
                target.get().treeData(),
                current.createdAt(),
                now,
                null
        );
        repository.save(restored);

        int nextRevisionNumber = repository.findRevisions(id).size() + 1;
        TestPlanRevisionEntity revision = new TestPlanRevisionEntity(
                UUID.randomUUID().toString(),
                id,
                nextRevisionNumber,
                current.treeData(),
                now,
                "restore"
        );
        repository.saveRevision(revision);

        return Optional.of(toDto(restored));
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private TestPlanDto toDto(TestPlanEntity entity) {
        return new TestPlanDto(
                entity.id(),
                entity.name(),
                entity.ownerId(),
                entity.treeData(),
                entity.createdAt(),
                entity.updatedAt()
        );
    }

    private TestPlanRevisionDto toRevisionDto(TestPlanRevisionEntity entity) {
        return new TestPlanRevisionDto(
                entity.id(),
                entity.planId(),
                entity.revisionNumber(),
                entity.treeData(),
                entity.createdAt(),
                entity.author()
        );
    }

    private String derivePlanName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "Imported Plan";
        }
        String name = originalFilename;
        if (name.toLowerCase().endsWith(".jmx")) {
            name = name.substring(0, name.length() - 4);
        }
        return name.isBlank() ? "Imported Plan" : name;
    }

    private String emptyJmxXml(String planName) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<jmeterTestPlan version=\"1.2\" properties=\"5.0\" jmeter=\"5.6.3\">\n"
                + "  <hashTree>\n"
                + "    <TestPlan guiclass=\"TestPlanGui\" testclass=\"TestPlan\" testname=\""
                + escapeXml(planName)
                + "\" enabled=\"true\"/>\n"
                + "  </hashTree>\n"
                + "</jmeterTestPlan>\n";
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    // -------------------------------------------------------------------------
    // Exception types
    // -------------------------------------------------------------------------

    /**
     * Thrown when an uploaded file exceeds the allowed size limit.
     */
    public static final class FileTooLargeException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public FileTooLargeException(String message) {
            super(message);
        }
    }
}
