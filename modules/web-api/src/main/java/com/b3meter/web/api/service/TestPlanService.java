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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.b3meter.engine.service.plan.JmxSerializer;
import com.b3meter.engine.service.plan.PlanNode;
import com.b3meter.engine.service.plan.PlanNodeSerializer;
import com.b3meter.web.api.controller.dto.CreatePlanRequest;
import com.b3meter.web.api.controller.dto.TestPlanDto;
import com.b3meter.web.api.controller.dto.TestPlanRevisionDto;
import com.b3meter.web.api.controller.dto.UpdatePlanRequest;
import com.b3meter.web.api.repository.TestPlanEntity;
import com.b3meter.web.api.repository.TestPlanRepository;
import com.b3meter.web.api.repository.TestPlanRevisionEntity;
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
 * <p>JMX import parses the uploaded file with {@link com.b3meter.engine.service.plan.JmxTreeWalker},
 * serializes the resulting {@link PlanNode} tree to JSON using {@link PlanNodeSerializer},
 * and stores that JSON string as {@code tree_data} in {@link TestPlanEntity}
 * (T014 / spec 009-quality-jmx-parsing).
 *
 * <p>JMX export detects the {@code tree_data} format:
 * <ul>
 *   <li>Starts with {@code {}: JSON format (new) — deserialize and reconstruct JMX XML.</li>
 *   <li>Starts with {@code <}: legacy raw XML — return unchanged.</li>
 *   <li>Otherwise: return an empty JMX envelope.</li>
 * </ul>
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
     * Imports a JMX file, parsing it into a {@link PlanNode} tree and storing
     * the tree as JSON in {@code treeData} (T014 / spec 009-quality-jmx-parsing).
     *
     * <p>The raw XML is used only for parsing/validation and is not stored.
     *
     * @param file the uploaded JMX file; must not be null
     * @return the created plan as a DTO
     * @throws IllegalArgumentException if the file exceeds {@link #MAX_JMX_SIZE_BYTES}
     *                                  or if the JMX is malformed / empty
     * @throws IllegalStateException    if reading the file or serializing the tree fails
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

        // Parse JMX using StAX-based JmxTreeWalker (no XStream, no class resolution).
        // This validates the JMX and produces a fully-typed PlanNode tree.
        PlanNode planNode;
        try {
            planNode = com.b3meter.engine.service.plan.JmxTreeWalker.parse(rawXml);
            if (planNode == null || planNode.getChildren().isEmpty()) {
                throw new IllegalArgumentException("JMX file rejected: empty or invalid test plan");
            }
        } catch (com.b3meter.engine.service.plan.JmxParseException e) {
            throw new IllegalArgumentException("JMX file rejected: malformed XML — " + e.getMessage(), e);
        }

        // Serialize the parsed tree to JSON for storage (T014 / spec 009-quality-jmx-parsing).
        // rawXml is only used above for parsing/validation — not stored.
        String treeData;
        try {
            treeData = PlanNodeSerializer.serialize(planNode);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize parsed JMX tree to JSON", e);
        }

        String planName = derivePlanName(file.getOriginalFilename());
        Instant now = Instant.now();
        TestPlanEntity entity = new TestPlanEntity(
                UUID.randomUUID().toString(),
                planName,
                DEFAULT_OWNER,
                treeData,
                now,
                now,
                null
        );
        return toDto(repository.save(entity));
    }

    /**
     * Exports a plan's tree data as JMX XML.
     *
     * <p>Format detection:
     * <ul>
     *   <li>Starts with {@code {}: JSON-format treeData (new, post-T014) — deserialize
     *       the {@link PlanNode} tree and reconstruct JMX XML via {@link JmxSerializer}.</li>
     *   <li>Starts with {@code <}: legacy raw XML — return unchanged (backward compat).</li>
     *   <li>Otherwise (blank or unknown): return an empty JMX envelope.</li>
     * </ul>
     *
     * @param id the plan identifier; must not be null
     * @return the JMX XML content, or empty if the plan does not exist
     * @throws IllegalStateException if JSON treeData cannot be deserialized
     */
    public Optional<String> exportJmx(String id) {
        return repository.findById(id).map(entity -> {
            String treeData = entity.treeData();
            if (treeData == null || treeData.isBlank()) {
                return emptyJmxXml(entity.name());
            }
            // JSON format (new, post-T014): deserialize PlanNode tree and reconstruct JMX XML.
            if (treeData.trim().startsWith("{")) {
                try {
                    PlanNode root = PlanNodeSerializer.deserialize(treeData);
                    return JmxSerializer.toJmx(root);
                } catch (JsonProcessingException e) {
                    throw new IllegalStateException(
                            "Failed to reconstruct JMX XML from stored JSON tree for plan "
                            + entity.id(), e);
                }
            }
            // Legacy XML format: return verbatim (FR-009.006).
            if (treeData.trim().startsWith("<")) {
                return treeData;
            }
            // Unknown format: return minimal envelope.
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
