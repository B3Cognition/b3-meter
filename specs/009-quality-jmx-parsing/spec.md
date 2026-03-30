# Specification: T014 JMX Import Parsing — Serialize PlanNode Tree at Import Time

**Domain**: 009-quality-jmx-parsing
**Created**: 2026-03-29
**Status**: Draft
**Dependencies**: 002-re-engine-service, 005-re-web-api

---

## Overview

At JMX import time, `TestPlanService.importJmx()` already invokes `JmxTreeWalker.parse()` which produces a fully-typed `PlanNode` tree. This tree is immediately discarded, and the raw XML is stored as `treeData` in `TestPlanEntity`. At execution time the engine re-parses the same XML via XStream (`JmxParser`). This spec defines the work to serialize the `PlanNode` tree to JSON at import time, store JSON as `treeData`, and update the export path to reconstruct JMX XML from the stored JSON — eliminating the redundant XStream parse-at-execution and establishing a clean separation between the storage format and the wire format.

---

## Problem Statement

### Current State

`modules/web-api/src/main/java/com/b3meter/web/api/service/TestPlanService.java` lines 156–189:

```java
public TestPlanDto importJmx(MultipartFile file) {
    // ... size check, read rawXml ...
    var planNode = JmxTreeWalker.parse(rawXml);  // fully parses the tree
    if (planNode == null || planNode.getChildren().isEmpty()) { throw ... }
    // planNode is discarded here — never used after validation
    TestPlanEntity entity = new TestPlanEntity(
        UUID.randomUUID().toString(), planName, DEFAULT_OWNER,
        rawXml,  // <-- raw XML stored, not the parsed tree
        now, now, null);
    return toDto(repository.save(entity));
}
```

`exportJmx()` (lines 198–211) detects if `treeData` starts with `<` and returns it as-is. If it does not start with `<`, it returns an empty JMX envelope — meaning any plan whose `treeData` is not raw XML produces an empty export.

### What Is Wrong

1. **Double parse at execution**: `JmxTreeWalker.parse()` runs at import time for validation. `JmxParser` (XStream) runs again at execution time. The typed tree is discarded between them.
2. **No typed representation in storage**: `treeData` stores opaque XML. The application has no structured representation of the plan between import and execution. Querying plan properties (e.g., "how many thread groups?") requires regex hacks (see `JmxSummary.tsx`).
3. **Export fragility**: `exportJmx()` returns raw XML directly. If `treeData` is ever stored as JSON (e.g., for plans created through the GUI tree editor), `exportJmx()` returns an empty envelope — confirmed at line 209: `return emptyJmxXml(entity.name())`.
4. **Mixed-format `treeData` field**: Plans created via GUI use JSON in `treeData`; plans created via JMX import use raw XML. `exportJmx()` already has a format discriminator but does not handle JSON-format plans.

### Impact

- `JmxSummary.tsx` counts elements using regex on raw XML — this breaks for JSON-format plans.
- The GUI-created plan export path silently returns an empty envelope instead of an error.
- Technical debt is explicitly called out in `specs/000-re-overview/constitution.md` §1.4: "JMX Full Parsing Deferred (T014)".
- Source evidence: `TestPlanService.java` lines 156–211; `constitution.md` §1.4.

---

## User Stories

### US-009.1 — Import JMX, Store Typed Representation (P1)

As a load tester importing a JMX file, I need the import to store a typed JSON representation of my test plan so that all downstream operations (export, GUI editing, execution) work against a structured format rather than opaque XML.

**Source evidence**: `TestPlanService.java` lines 169–184 — `planNode` is computed and discarded.

**Acceptance Scenarios**:
- Given a valid JMX file is uploaded to `POST /api/v1/plans/import`, when the import succeeds, then `TestPlanEntity.treeData` starts with `{` (JSON format).
- Given the imported plan's `treeData`, when it is deserialized with `PlanNodeSerializer.deserialize()`, then the resulting `PlanNode` tree has the same element count as the original JMX.
- Given the import fails (malformed JMX), then no entity is created and the HTTP response is 400.

### US-009.2 — Export JMX from Imported Plan (P1)

As a load tester who imported a JMX file, I need to export it back to JMX XML so that I can use it with other JMeter-compatible tools.

**Source evidence**: `TestPlanService.java` lines 198–211 — export only works for XML-format `treeData`.

**Acceptance Scenarios**:
- Given a plan imported from JMX (treeData is JSON), when I call `GET /api/v1/plans/{id}/export`, then the response is valid JMX XML.
- Given the exported XML is re-imported to JMeter 5.x, then the plan runs without error.
- Given the exported XML is re-imported into jMeter Next, then the thread group count and sampler count match the original.

### US-009.3 — Export JMX from Legacy Plans (P2)

As an administrator with plans that were imported before this fix (treeData is raw XML), I need exports to continue working without data migration so that upgrading does not break existing plans.

**Source evidence**: `TestPlanService.java` lines 204–207 — `treeData.trim().startsWith("<")` path remains valid.

**Acceptance Scenarios**:
- Given a plan whose `treeData` is raw XML (legacy), when I call `GET /api/v1/plans/{id}/export`, then the response is the original XML unchanged.
- Given a legacy plan, when the application starts with the upgraded code, then no migration job runs automatically.

### US-009.4 — Structured Plan Properties for Frontend (P2)

As a developer building the property panel, I need the backend to store structured plan data so that features like `JmxSummary` do not need to parse raw XML with regex.

**Source evidence**: `PropertyPanel.tsx` lines 1261–1332 — `JmxSummary` counts elements using regex on `planXmlMap[planId]`.

**Acceptance Scenarios**:
- Given a plan imported from JMX, when the frontend requests `GET /api/v1/plans/{id}`, then the response `treeData` field is valid JSON that the frontend can parse with `JSON.parse()`.
- Given the JSON `treeData`, when a frontend utility counts thread groups by filtering `children` by `testClass === 'ThreadGroup'`, then the count matches `JmxSummary`'s regex-based count.

### US-009.5 — No API Contract Change (P1)

As an API consumer, I need the import and export HTTP endpoints to remain structurally unchanged so that existing integrations do not break.

**Source evidence**: `modules/web-api/src/main/java/com/b3meter/web/api/controller/TestPlanController.java` (inferred — controller delegates to `TestPlanService`).

**Acceptance Scenarios**:
- Given the fix is deployed, when I POST a JMX file to `/api/v1/plans/import`, then the response shape (status code, `TestPlanDto` JSON structure) is identical to pre-fix behaviour.
- Given the fix is deployed, when I GET `/api/v1/plans/{id}/export`, then the response is `Content-Type: application/xml` with valid JMX content.

---

## Functional Requirements

### FR-009.001 — PlanNodeSerializer Utility

A new utility class `PlanNodeSerializer` must be created in `modules/engine-service/src/main/java/com/b3meter/engine/service/plan/PlanNodeSerializer.java` with the following contract:

```java
public final class PlanNodeSerializer {
    public static String serialize(PlanNode root) throws JsonProcessingException { ... }
    public static PlanNode deserialize(String json) throws JsonProcessingException { ... }
}
```

The serialized JSON format must faithfully represent `PlanNode.testClass`, `PlanNode.testName`, `PlanNode.properties` (including all value types: String, Integer, Long, Double, Boolean, nested PlanNode, List), and `PlanNode.children`.

**Source evidence**: `PlanNode.java` lines 32–311 — `testClass`, `testName`, `properties: Map<String,Object>`, `children: List<PlanNode>`.

### FR-009.002 — JSON Property Value Type Disambiguation

The serialized JSON must preserve the runtime type of each property value (String vs Integer vs Long vs Double vs Boolean vs PlanNode vs List). The deserializer must reconstruct the correct Java type for each value without requiring a schema hint per property name.

**Rationale**: `PlanNode.getIntProp(name)` returns 0 for values that are not `instanceof Integer`. If a stored integer is deserialized as a JSON number without type tagging, Jackson may deserialize it as `Integer` or `Long` depending on magnitude — this must be deterministic.

**Implementation guidance**: Use Jackson's `@JsonTypeInfo(use = Id.CLASS)` on a wrapper type, or use a custom `StdSerializer`/`StdDeserializer` for `Map<String,Object>` that writes a type tag for each non-primitive nested value (`PlanNode` instances and `List` values). Alternatively, define `PlanNodeDto` record types that map cleanly to Jackson default serialization and add a `fromNode()`/`toNode()` conversion layer.

### FR-009.003 — importJmx Stores JSON

After `JmxTreeWalker.parse()` returns a non-null, non-empty `PlanNode` tree, `TestPlanService.importJmx()` must serialize the tree to JSON using `PlanNodeSerializer.serialize()` and store the resulting JSON string as `treeData` in `TestPlanEntity` — not the raw XML.

**Source evidence**: `TestPlanService.java` lines 169–184.

### FR-009.004 — exportJmx Handles JSON Format

`TestPlanService.exportJmx()` must detect when `treeData` starts with `{` (JSON format), deserialize the `PlanNode` tree using `PlanNodeSerializer.deserialize()`, and convert it back to JMX XML using a new `JmxSerializer` utility.

### FR-009.005 — JmxSerializer Utility

A new utility class `JmxSerializer` must be created in `modules/engine-service/src/main/java/com/b3meter/engine/service/plan/JmxSerializer.java` with the following contract:

```java
public final class JmxSerializer {
    public static String toJmx(PlanNode root) { ... }
}
```

`toJmx()` must produce XML that is structurally equivalent to the original JMX for all property types. The output must be valid JMeter 5.x JMX (parseable by the existing `JmxTreeWalker.parse()` round-trip test).

**Implementation guidance**: Use StAX `XMLStreamWriter` to produce the output — consistent with the StAX-based `JmxTreeWalker` parser. Do not introduce XStream dependency in `engine-service` (Principle I: framework-free engine).

### FR-009.006 — Legacy XML Format Preserved

`TestPlanService.exportJmx()` must retain the existing branch: if `treeData.trim().startsWith("<")`, return the XML directly without modification. No migration of legacy plans is performed.

**Source evidence**: `TestPlanService.java` lines 204–207.

### FR-009.007 — engine-service Module Has No Spring Dependency

`PlanNodeSerializer` and `JmxSerializer` must be framework-free (plain Java, no Spring annotations). Jackson `ObjectMapper` may be used as it is a general-purpose library, but it must be declared as a dependency of `engine-service` only if not already present.

**Source evidence**: `specs/000-re-overview/constitution.md` §Principle I — "Framework-free engine".

### FR-009.008 — XStream Security Policy Unchanged

`JmxParser` (XStream-based) in `engine-adapter` must not be modified. `XStreamSecurityPolicy.apply()` must remain enforced.

**Source evidence**: `constitution.md` §Principle IV.

---

## Success Criteria

### SC-009.001 — Round-Trip Fidelity

A test imports a representative JMX file (containing at minimum: TestPlan, 2 ThreadGroups, 3 HTTPSamplerProxy, 1 CookieManager, 1 ResponseAssertion). The exported JMX XML is then re-imported. The second import produces a `PlanNode` tree whose element count (by `testClass`) matches the first.

### SC-009.002 — JSON Format on Import

After calling `importJmx()`, the stored `TestPlanEntity.treeData` starts with `{`. Verified by an integration test that reads the entity from the repository after import.

### SC-009.003 — Export Returns Valid XML

After importing a JMX file (treeData is JSON), calling `exportJmx()` returns a non-empty string that starts with `<?xml` or `<jmeterTestPlan`. Verified by a unit test on `TestPlanService`.

### SC-009.004 — Legacy Plan Export Unchanged

A `TestPlanEntity` with `treeData` starting with `<` (legacy XML) returns the original XML unchanged from `exportJmx()`. No transformation applied.

### SC-009.005 — Type Preservation After Deserialization

A `PlanNode` with properties of all types (String, Integer, Long, Double, Boolean, nested PlanNode, List) survives a serialize→deserialize round trip such that `getIntProp`, `getLongProp`, `getBoolProp`, `getDoubleProp`, `getStringProp`, `getElementProp`, `getCollectionProp` return the same values before and after.

**Source evidence**: `PlanNode.java` lines 132–247.

### SC-009.006 — No New Required Infrastructure

The fix works with H2 embedded database, no additional services, no schema migration scripts (the `treeData` column is already `TEXT`/`VARCHAR(MAX)` — JSON fits).

### SC-009.007 — No API Contract Change

`POST /api/v1/plans/import` returns HTTP 201 with the same `TestPlanDto` fields. `GET /api/v1/plans/{id}/export` returns HTTP 200 with `Content-Type: application/xml`. Response shapes are unchanged.

---

## Non-Functional Requirements

### NFR-009.001 — engine-service Remains Framework-Free

`PlanNodeSerializer` and `JmxSerializer` must have zero Spring Boot / Spring Framework imports. Jackson `com.fasterxml.jackson.*` is permitted.

### NFR-009.002 — JmxSerializer Produces Deterministic Output

Given the same `PlanNode` tree, `JmxSerializer.toJmx()` must produce byte-for-byte identical output across JVM restarts (no unordered sets in serialization, no timestamp insertion).

### NFR-009.003 — Serialization Performance

`PlanNodeSerializer.serialize()` for a plan with 100 nodes must complete in under 50ms on the CI build host. No performance regression in `importJmx()` compared to the current implementation (which already calls `JmxTreeWalker.parse()` for validation).

### NFR-009.004 — Virtual Threads Compatibility

`PlanNodeSerializer` and `JmxSerializer` must be stateless and thread-safe. If a shared `ObjectMapper` instance is used, it must be created once as a `static final` field (Jackson `ObjectMapper` is thread-safe for read operations after configuration).

### NFR-009.005 — No Checked Exceptions Across Module Boundary

`PlanNodeSerializer` may throw `JsonProcessingException` (checked). `TestPlanService` must catch and wrap this as an `IllegalStateException` so the service layer does not expose Jackson exceptions in its public API.

---

## Out of Scope

- Migrating existing plans from XML to JSON format (no migration job).
- Changing the `TestPlanDto` or `TestPlanRevisionDto` API response shapes.
- Replacing `JmxParser` (XStream) with `JmxTreeWalker` in the execution path (separate T014 sub-task).
- Adding a plan diff or merge capability.
- Changing how the GUI tree editor stores plans (it already uses JSON).
- Adding database schema changes (the `treeData` column is already `TEXT`).
- Changing the `JmxSummary.tsx` frontend component (it will benefit from JSON format but is a separate task).
- Any changes to `worker-proto` or `engine-adapter`.
