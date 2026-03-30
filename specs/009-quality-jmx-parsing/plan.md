# Implementation Plan: T014 JMX Import Parsing

**Domain**: 009-quality-jmx-parsing
**Created**: 2026-03-29
**Status**: Draft

---

## Approach

The implementation follows a strict dependency order: build the serialization utilities first (they are pure Java, independently testable), then wire them into the service layer. The two utilities (`PlanNodeSerializer` and `JmxSerializer`) live in `engine-service` — the framework-free module — because `PlanNode` is defined there. The service layer in `web-api` depends on `engine-service` and orchestrates the conversion.

Key design decisions:

1. **PlanNodeSerializer uses Jackson with a custom type-tagging strategy**: The `properties` map holds heterogeneous values (`String`, `Integer`, `Long`, `Double`, `Boolean`, `PlanNode`, `List<Object>`). Plain Jackson cannot round-trip `Map<String,Object>` with type fidelity for `Long` vs `Integer` and `PlanNode` values. A `PlanNodeDto` record hierarchy (one record per value type variant) is used as the serialization intermediary. This avoids `@JsonTypeInfo(use = Id.CLASS)` which would embed fully-qualified class names in the JSON.

2. **JmxSerializer uses StAX XMLStreamWriter**: Consistent with `JmxTreeWalker` (StAX reader). No XStream in `engine-service`. Output mirrors the JMeter JMX XML structure (`<jmeterTestPlan>/<hashTree>` nesting).

3. **Format discriminator**: `treeData.trim().startsWith("{")` = JSON; `treeData.trim().startsWith("<")` = legacy XML. This is a stable heuristic — JMX always starts with `<` and JSON always starts with `{`.

4. **Jackson in engine-service**: Check if Jackson is already a transitive dependency of `engine-service`. If not, add `com.fasterxml.jackson.core:jackson-databind` to `modules/engine-service/build.gradle.kts`. Jackson is already present in `web-api` via Spring Boot.

---

## File Changes

### Files to Create

#### `modules/engine-service/src/main/java/com/b3meter/engine/service/plan/PlanNodeSerializer.java`

**Purpose**: Serialize/deserialize `PlanNode` trees to/from JSON using Jackson and a `PlanNodeDto` intermediary.

**Key implementation details**:
- Define `PlanNodeDto` as a `record` (or package-private inner class) with fields: `String testClass`, `String testName`, `Map<String,PlanPropertyValue> properties`, `List<PlanNodeDto> children`.
- Define `PlanPropertyValue` as a sealed interface with permitted implementations: `StringValue(String v)`, `IntValue(int v)`, `LongValue(long v)`, `DoubleValue(double v)`, `BoolValue(boolean v)`, `NodeValue(PlanNodeDto node)`, `ListValue(List<PlanPropertyValue> items)`.
- Use `@JsonTypeInfo(use = Id.NAME, property = "type")` + `@JsonSubTypes` on `PlanPropertyValue` to tag each variant. Type names: `"s"`, `"i"`, `"l"`, `"d"`, `"b"`, `"node"`, `"list"` (short codes to minimize JSON size).
- Static `ObjectMapper MAPPER` configured once with `findAndRegisterModules()`.
- `serialize(PlanNode root)`: converts tree to `PlanNodeDto` hierarchy, serializes to JSON string.
- `deserialize(String json)`: deserializes `PlanNodeDto` hierarchy, reconstructs `PlanNode` tree via `PlanNode.builder()`.
- Static factory methods `fromNode(PlanNode)` and `toNode(PlanNodeDto)` for the conversion.

#### `modules/engine-service/src/main/java/com/b3meter/engine/service/plan/JmxSerializer.java`

**Purpose**: Convert a `PlanNode` tree back to JMeter-compatible JMX XML.

**Key implementation details**:
- `public static String toJmx(PlanNode root)` — entry point.
- Use `javax.xml.stream.XMLOutputFactory` → `XMLStreamWriter` with `UTF-8` encoding.
- Produce the JMeter JMX envelope:
  ```xml
  <?xml version="1.0" encoding="UTF-8"?>
  <jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
    <hashTree>
      <!-- root node and its children recursively -->
    </hashTree>
  </jmeterTestPlan>
  ```
- For each `PlanNode`, write the element tag using `testClass` as the element name with attribute `testname="{testName}" enabled="true"`.
- For each property entry in `node.getProperties()`:
  - `String` → `<stringProp name="{k}">{v}</stringProp>`
  - `Integer` → `<intProp name="{k}">{v}</intProp>`
  - `Long` → `<longProp name="{k}">{v}</longProp>`
  - `Double` → `<doubleProp name="{k}">{v}</doubleProp>`
  - `Boolean` → `<boolProp name="{k}">{v}</boolProp>`
  - `PlanNode` → `<elementProp name="{k}" elementType="{node.testClass}">` recursively + `</elementProp>`
  - `List<Object>` → `<collectionProp name="{k}">` with each item serialized by type
- After writing the element, write a `<hashTree>` block containing each child recursively.
- Private `writeNode(XMLStreamWriter writer, PlanNode node)` recursive helper.
- Throw `UncheckedIOException` wrapping `XMLStreamException` — no checked exceptions in public API.

#### `modules/engine-service/src/test/java/com/b3meter/engine/service/plan/PlanNodeSerializerTest.java`

JUnit 5 tests for `PlanNodeSerializer`:
- `serialize_thenDeserialize_preservesAllPropertyTypes()` — round-trip test with all 7 value types.
- `serialize_nestedNode_preservesHierarchy()` — tree with 3 levels.
- `deserialize_malformedJson_throwsJsonProcessingException()`.

#### `modules/engine-service/src/test/java/com/b3meter/engine/service/plan/JmxSerializerTest.java`

JUnit 5 tests for `JmxSerializer`:
- `toJmx_simpleNode_producesValidXml()` — output starts with `<?xml` or `<jmeterTestPlan`.
- `toJmx_thenParse_roundTrip_preservesNodeCount()` — serialize, then re-parse with `JmxTreeWalker.parse()`, compare root `testClass` and children count.
- `toJmx_withAllPropertyTypes_producesCorrectElements()` — verify element tags match property types.

#### `modules/web-api/src/test/java/com/b3meter/web/api/service/TestPlanServiceJmxTest.java`

JUnit 5 + Spring Boot integration tests for the updated `importJmx`/`exportJmx` flow:
- `importJmx_storesJsonTreeData()` — after import, entity `treeData` starts with `{`.
- `exportJmx_fromImportedPlan_returnsValidXml()` — export after import returns valid XML.
- `exportJmx_fromLegacyXmlPlan_returnsOriginalXml()` — legacy path unchanged.
- `importJmx_malformedFile_returns400()` — error handling unchanged.

---

### Files to Modify

#### `modules/engine-service/build.gradle.kts`

**Change**: Add Jackson databind dependency if not already present.

Check with: `grep -r "jackson" modules/engine-service/build.gradle.kts`

If absent, add:
```kotlin
dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
}
```

**Note**: Use the same Jackson version as the parent BOM or `web-api` module to avoid classpath conflicts.

#### `modules/web-api/src/main/java/com/b3meter/web/api/service/TestPlanService.java`

**Change 1** — `importJmx()` method (lines 156–189):

After `JmxTreeWalker.parse()` succeeds, serialize the tree:
```java
String treeData;
try {
    treeData = PlanNodeSerializer.serialize(planNode);
} catch (JsonProcessingException e) {
    throw new IllegalStateException("Failed to serialize parsed JMX tree", e);
}
```
Replace `rawXml` with `treeData` in the `TestPlanEntity` constructor call.

**Change 2** — `exportJmx()` method (lines 198–211):

Add JSON branch before the XML branch:
```java
if (treeData.trim().startsWith("{")) {
    try {
        PlanNode root = PlanNodeSerializer.deserialize(treeData);
        return JmxSerializer.toJmx(root);
    } catch (JsonProcessingException e) {
        throw new IllegalStateException("Failed to reconstruct JMX from stored tree", e);
    }
}
if (treeData.trim().startsWith("<")) {
    return treeData;
}
return emptyJmxXml(entity.name());
```

**Change 3** — Add imports:
```java
import com.b3meter.engine.service.plan.PlanNodeSerializer;
import com.b3meter.engine.service.plan.JmxSerializer;
import com.fasterxml.jackson.core.JsonProcessingException;
```

---

### Files to Leave Unchanged

| File | Reason |
|------|--------|
| `modules/engine-service/.../JmxTreeWalker.java` | Parser is unchanged; used only for import validation |
| `modules/engine-adapter/.../JmxParser.java` | XStream-based; execution path; not changed |
| `modules/web-api/.../TestPlanController.java` | HTTP contract unchanged |
| `modules/web-api/.../TestPlanEntity.java` | `treeData` column is already `TEXT`; no schema change |
| `modules/web-api/.../TestPlanDto.java` | Response DTO unchanged |
| All frontend files | JMX parsing is backend-only |

---

## Dependencies / Prerequisites

1. Confirm Jackson is on the `engine-service` classpath: `grep -r "jackson" modules/engine-service/build.gradle.kts`. If absent, add the dependency and verify `./gradlew :engine-service:compileJava` passes before proceeding.
2. Confirm `JmxTreeWalker.parse()` is the authoritative parser by reading its package-private API (do not add new public methods to it for this task).
3. Confirm `TestPlanEntity` `treeData` field is a String column with no length constraint that would truncate large JSON (H2 `VARCHAR(MAX)` or `CLOB` equivalent). Check `TestPlanEntity.java` and the schema DDL.

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Jackson not on `engine-service` classpath | Medium | Compilation failure | Check `build.gradle.kts` first; add dependency in T-009-001 |
| `treeData` column has a VARCHAR length limit that truncates large JSON | Low | Silent data corruption on large plans | Check DDL; if limited, alter to `TEXT` or `CLOB` in schema migration |
| `PlanPropertyValue` sealed interface requires Java 17+ sealed classes | Low | Java 21 is the target — this is fine | Confirm Java 21 in `build.gradle.kts` (`sourceCompatibility = JavaVersion.VERSION_21`) |
| `JmxSerializer.toJmx()` produces XML that `JmxTreeWalker.parse()` rejects | Medium | Round-trip test fails | Add round-trip test T-009-004; iteratively fix until green |
| `Long` values serialized as JSON `number` are deserialized as `Integer` by Jackson | High | `getLongProp()` returns 0 for values that should be non-zero | Use `LongValue` DTO variant with explicit `@JsonDeserialize` to enforce `long` type |
| Existing plans with XML `treeData` are passed to the new JSON branch | Very Low | No — format discriminator is checked first | The `startsWith("{")` check is unambiguous for well-formed inputs |

---

## Rollback

1. Revert changes to `TestPlanService.java` with `git checkout HEAD -- modules/web-api/src/main/java/com/b3meter/web/api/service/TestPlanService.java`.
2. Delete the new files: `PlanNodeSerializer.java`, `JmxSerializer.java` and their test files.
3. Revert `engine-service/build.gradle.kts` if Jackson was added.
4. Run `./gradlew :web-api:test` to confirm restored.

No database migration is needed — legacy plans stored with JSON `treeData` will have their export return an empty JMX envelope (the old `exportJmx()` behaviour for non-XML `treeData`). This is acceptable for rollback since the plans were just imported and could be re-imported.

---

## Testing Strategy

### Unit Tests — `PlanNodeSerializerTest`

Round-trip fidelity is the primary concern:
- Test all 7 property value types survive serialize→deserialize.
- Test nested `PlanNode` (elementProp) survives.
- Test `List<Object>` (collectionProp) with mixed types survives.
- Test null/empty children list.
- Test empty properties map.

### Unit Tests — `JmxSerializerTest`

- Simple single-node serialization produces valid XML.
- Multi-level tree serializes all nodes.
- Re-parse with `JmxTreeWalker.parse()` produces tree matching original (round-trip).
- All property types produce correct XML element tags.

### Integration Tests — `TestPlanServiceJmxTest`

- `importJmx()` stores JSON.
- `exportJmx()` on a JSON-stored plan returns valid XML.
- `exportJmx()` on a legacy XML-stored plan returns original XML unchanged.
- Import of a corrupted JMX file returns 400.

### Regression Test

After implementation:
1. `./gradlew :engine-service:test` — all new and existing engine-service tests pass.
2. `./gradlew :web-api:test` — all new and existing web-api tests pass.
3. Manual test: upload `test-resources/sample.jmx` → verify `treeData` is JSON → export → verify XML is valid.

### Test Resource

Create `modules/web-api/src/test/resources/sample-roundtrip.jmx` — a minimal but representative JMX file with:
- 1 `TestPlan`
- 2 `ThreadGroup` (one with scheduler)
- 2 `HTTPSamplerProxy` per thread group
- 1 `ResponseAssertion`
- 1 `CookieManager`

This file is used in `TestPlanServiceJmxTest.importJmx_storesJsonTreeData()` and the round-trip test.
