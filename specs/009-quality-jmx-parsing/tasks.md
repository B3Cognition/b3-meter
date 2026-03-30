# Tasks: T014 JMX Import Parsing

**Domain**: 009-quality-jmx-parsing
**Created**: 2026-03-29
**Status**: Complete — all 10 tasks done; all tests pass (engine-service + web-api)

---

## Task List

### T-009-001 — Verify Prerequisites and Jackson Dependency

**Type**: modify
**Files affected**:
- `modules/engine-service/build.gradle.kts` (conditionally modify)
**Complexity**: S
**Dependencies**: none

**Description**: Confirm the baseline build is clean and add the Jackson databind dependency to `engine-service` if it is not already present.

**Steps**:
1. Run `./gradlew :engine-service:test` — confirm all existing tests pass.
2. Run `./gradlew :web-api:test` — confirm all existing tests pass.
3. Check `modules/engine-service/build.gradle.kts` for Jackson: `grep -i "jackson" modules/engine-service/build.gradle.kts`.
4. If Jackson is absent, add `implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")` to the `dependencies` block. Use the same version as found in the parent BOM (`gradle/libs.versions.toml` or root `build.gradle.kts`).
5. Verify `treeData` column has no truncating length constraint: read `TestPlanEntity.java` and the schema init SQL (check `src/main/resources/schema.sql` or similar). If a length limit exists that could truncate large JSON, note it for T-009-002.
6. Run `./gradlew :engine-service:compileJava` — must pass.

**Acceptance Criteria**:
- `./gradlew :engine-service:test` and `./gradlew :web-api:test` exit with code 0.
- Jackson databind is on the `engine-service` compile classpath.
- `treeData` column type is documented and confirmed to hold arbitrary-length strings.

---

### T-009-002 — Create PlanNodeSerializer with DTO Hierarchy

**Type**: create
**Files affected**:
- `modules/engine-service/src/main/java/com/b3meter/engine/service/plan/PlanNodeSerializer.java` (create)
**Complexity**: L
**Dependencies**: T-009-001

**Description**: Implement `PlanNodeSerializer` with a `PlanPropertyValue` sealed interface DTO hierarchy that preserves all `PlanNode` property types through a JSON round-trip.

**Steps**:
1. Create `PlanNodeSerializer.java` in `com.b3meter.engine.service.plan`.
2. Define the `PlanPropertyValue` sealed interface with `@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")` and `@JsonSubTypes` listing all 7 variants:
   - `record StringVal(String v) implements PlanPropertyValue` — type name `"s"`
   - `record IntVal(int v) implements PlanPropertyValue` — type name `"i"`
   - `record LongVal(long v) implements PlanPropertyValue` — type name `"l"`
   - `record DoubleVal(double v) implements PlanPropertyValue` — type name `"d"`
   - `record BoolVal(boolean v) implements PlanPropertyValue` — type name `"b"`
   - `record NodeVal(PlanNodeDto node) implements PlanPropertyValue` — type name `"node"`
   - `record ListVal(List<PlanPropertyValue> items) implements PlanPropertyValue` — type name `"list"`
3. Define `record PlanNodeDto(String testClass, String testName, Map<String,PlanPropertyValue> properties, List<PlanNodeDto> children)`.
4. Implement `static PlanNodeDto fromNode(PlanNode node)`:
   - For each entry in `node.getProperties()`, convert the value to the matching `PlanPropertyValue` subtype using `instanceof` pattern matching. For `List` values, recurse into items.
   - Recursively convert children.
5. Implement `static PlanNode toNode(PlanNodeDto dto)`:
   - Create `PlanNode.builder(dto.testClass(), dto.testName())`.
   - For each property entry in `dto.properties()`, extract the raw value from the `PlanPropertyValue` wrapper and call `.property(name, value)`. For `NodeVal`, recursively call `toNode()`. For `ListVal`, recursively convert items.
   - Add children via `.child(toNode(child))`.
   - Return `.build()`.
6. Implement `public static String serialize(PlanNode root) throws JsonProcessingException`: `return MAPPER.writeValueAsString(fromNode(root))`.
7. Implement `public static PlanNode deserialize(String json) throws JsonProcessingException`: `return toNode(MAPPER.readValue(json, PlanNodeDto.class))`.
8. Define `private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules()`.
9. Annotate the class `final` with private constructor.
10. Run `./gradlew :engine-service:compileJava`.

**Acceptance Criteria**:
- `./gradlew :engine-service:compileJava` exits with code 0.
- `PlanNodeSerializer.serialize()` and `PlanNodeSerializer.deserialize()` are public static methods.
- No Spring or JMeter imports in the file.

---

### T-009-003 — Test PlanNodeSerializer Round-Trip

**Type**: test
**Files affected**:
- `modules/engine-service/src/test/java/com/b3meter/engine/service/plan/PlanNodeSerializerTest.java` (create)
**Complexity**: M
**Dependencies**: T-009-002

**Description**: Write JUnit 5 unit tests that verify `PlanNodeSerializer` faithfully preserves all property types through a serialize→deserialize round trip.

**Tests to implement**:

```java
@Test
void serialize_thenDeserialize_allPrimitiveTypes_preserved() {
    PlanNode original = PlanNode.builder("TestPlan", "My Plan")
        .property("str", "hello")
        .property("intVal", 42)
        .property("longVal", 9_000_000_000L)
        .property("dbl", 1.5)
        .property("flag", true)
        .build();
    String json = PlanNodeSerializer.serialize(original);
    PlanNode restored = PlanNodeSerializer.deserialize(json);
    assertThat(restored.getStringProp("str")).isEqualTo("hello");
    assertThat(restored.getIntProp("intVal")).isEqualTo(42);
    assertThat(restored.getLongProp("longVal")).isEqualTo(9_000_000_000L);
    assertThat(restored.getDoubleProp("dbl")).isEqualTo(1.5);
    assertThat(restored.getBoolProp("flag")).isTrue();
}

@Test
void serialize_thenDeserialize_nestedPlanNode_preserved() {
    PlanNode inner = PlanNode.builder("Arguments", "User Vars").build();
    PlanNode original = PlanNode.builder("TestPlan", "Plan")
        .property("args", inner)
        .build();
    PlanNode restored = PlanNodeSerializer.deserialize(PlanNodeSerializer.serialize(original));
    assertThat(restored.getElementProp("args")).isNotNull();
    assertThat(restored.getElementProp("args").getTestClass()).isEqualTo("Arguments");
}

@Test
void serialize_thenDeserialize_collectionProp_preserved() {
    PlanNode original = PlanNode.builder("ThreadGroup", "TG")
        .property("items", List.of("a", "b", 3))
        .build();
    PlanNode restored = PlanNodeSerializer.deserialize(PlanNodeSerializer.serialize(original));
    assertThat(restored.getCollectionProp("items")).hasSize(3);
}

@Test
void serialize_thenDeserialize_childHierarchy_preserved() {
    PlanNode child = PlanNode.builder("HTTPSamplerProxy", "Login").build();
    PlanNode original = PlanNode.builder("ThreadGroup", "TG").child(child).build();
    PlanNode restored = PlanNodeSerializer.deserialize(PlanNodeSerializer.serialize(original));
    assertThat(restored.getChildren()).hasSize(1);
    assertThat(restored.getChildren().get(0).getTestClass()).isEqualTo("HTTPSamplerProxy");
}

@Test
void deserialize_malformedJson_throwsJsonProcessingException() {
    assertThatThrownBy(() -> PlanNodeSerializer.deserialize("not-json"))
        .isInstanceOf(JsonProcessingException.class);
}

@Test
void serialize_producesJsonStartingWithBrace() throws Exception {
    PlanNode node = PlanNode.builder("TestPlan", "P").build();
    assertThat(PlanNodeSerializer.serialize(node).trim()).startsWith("{");
}
```

**Acceptance Criteria**:
- All tests pass: `./gradlew :engine-service:test --tests "*.PlanNodeSerializerTest"`.
- No test uses `@Disabled` or `Assumptions.assumeTrue`.

---

### T-009-004 — Create JmxSerializer

**Type**: create
**Files affected**:
- `modules/engine-service/src/main/java/com/b3meter/engine/service/plan/JmxSerializer.java` (create)
**Complexity**: L
**Dependencies**: T-009-001

**Description**: Implement `JmxSerializer.toJmx()` using StAX `XMLStreamWriter` to produce JMeter-compatible JMX XML from a `PlanNode` tree.

**Steps**:
1. Create `JmxSerializer.java` in `com.b3meter.engine.service.plan`.
2. Implement `public static String toJmx(PlanNode root)`:
   - Create `StringWriter`, then `XMLOutputFactory.newInstance().createXMLStreamWriter(writer, "UTF-8")`.
   - Write XML declaration: `writer.writeStartDocument("UTF-8", "1.0")`.
   - Write root element: `<jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">`.
   - Write `<hashTree>`.
   - Call `writeNode(writer, root)`.
   - Close `</hashTree>`, `</jmeterTestPlan>`.
   - `writer.flush(); writer.close()`.
   - Return `stringWriter.toString()`.
3. Implement `private static void writeNode(XMLStreamWriter w, PlanNode node)`:
   - Write start element `node.getTestClass()` with attributes `testname=node.getTestName()`, `enabled="true"`, `guiclass="{node.getTestClass()}BeanInfo"`, `testclass="{node.getTestClass()}"`.
   - Iterate `node.getProperties().entrySet()` and call `writeProperty(w, entry.getKey(), entry.getValue())`.
   - Write `</TestClassElement>`.
   - Write `<hashTree>`.
   - For each child: `writeNode(w, child)`.
   - Write `</hashTree>`.
4. Implement `private static void writeProperty(XMLStreamWriter w, String name, Object value)`:
   - `String` → `<stringProp name="{name}">{value}</stringProp>`
   - `Integer` → `<intProp name="{name}">{value}</intProp>`
   - `Long` → `<longProp name="{name}">{value}</longProp>`
   - `Double` → `<doubleProp name="{name}">{value}</doubleProp>`
   - `Boolean` → `<boolProp name="{name}">{value}</boolProp>`
   - `PlanNode` → `<elementProp name="{name}" elementType="{node.testClass}">` + write properties recursively + `</elementProp>`
   - `List<?>` → `<collectionProp name="{name}">` + each item with `writeProperty(w, "", item)` + `</collectionProp>`
5. Wrap `XMLStreamException` in `UncheckedIOException`.
6. Annotate the class `final` with private constructor.
7. Run `./gradlew :engine-service:compileJava`.

**Acceptance Criteria**:
- `./gradlew :engine-service:compileJava` exits with code 0.
- No XStream, no Spring, no JMeter imports.
- `toJmx()` is a public static method.

---

### T-009-005 — Test JmxSerializer Round-Trip

**Type**: test
**Files affected**:
- `modules/engine-service/src/test/java/com/b3meter/engine/service/plan/JmxSerializerTest.java` (create)
**Complexity**: M
**Dependencies**: T-009-004

**Description**: Write JUnit 5 tests verifying `JmxSerializer` produces valid XML and survives a round-trip through `JmxTreeWalker.parse()`.

**Tests to implement**:

```java
@Test
void toJmx_simpleNode_producesXmlDeclaration() {
    PlanNode node = PlanNode.builder("TestPlan", "My Plan").build();
    String xml = JmxSerializer.toJmx(node);
    assertThat(xml).startsWith("<?xml");
    assertThat(xml).contains("<jmeterTestPlan");
    assertThat(xml).contains("TestPlan");
}

@Test
void toJmx_thenParse_roundTrip_preservesRootTestClass() {
    PlanNode threadGroup = PlanNode.builder("ThreadGroup", "TG 1")
        .property("ThreadGroup.num_threads", 10)
        .property("ThreadGroup.ramp_time", 5)
        .build();
    PlanNode sampler = PlanNode.builder("HTTPSamplerProxy", "Login")
        .property("HTTPSampler.path", "/login")
        .build();
    PlanNode root = PlanNode.builder("TestPlan", "Plan")
        .child(threadGroup.toBuilder()... // use child builder approach)
        .build();
    // Note: PlanNode has no toBuilder; use PlanNode.builder().child() pattern
    PlanNode plan = PlanNode.builder("TestPlan", "Plan").child(threadGroup).build();
    String xml = JmxSerializer.toJmx(plan);
    PlanNode reparsed = JmxTreeWalker.parse(xml);
    assertThat(reparsed).isNotNull();
    assertThat(reparsed.getTestClass()).isEqualTo("TestPlan");
}

@Test
void toJmx_allPropertyTypes_producesCorrectTags() {
    PlanNode node = PlanNode.builder("TestPlan", "P")
        .property("str", "hello")
        .property("intVal", 1)
        .property("longVal", 1L)
        .property("dbl", 1.0)
        .property("flag", false)
        .build();
    String xml = JmxSerializer.toJmx(node);
    assertThat(xml).contains("<stringProp name=\"str\">hello</stringProp>");
    assertThat(xml).contains("<intProp name=\"intVal\">1</intProp>");
    assertThat(xml).contains("<longProp name=\"longVal\">1</longProp>");
    assertThat(xml).contains("<doubleProp name=\"dbl\">1.0</doubleProp>");
    assertThat(xml).contains("<boolProp name=\"flag\">false</boolProp>");
}

@Test
void toJmx_withChildren_producesHashTreeBlocks() {
    PlanNode child = PlanNode.builder("ThreadGroup", "TG").build();
    PlanNode root = PlanNode.builder("TestPlan", "Plan").child(child).build();
    String xml = JmxSerializer.toJmx(root);
    assertThat(xml).contains("<hashTree>");
    assertThat(xml).contains("ThreadGroup");
}
```

**Acceptance Criteria**:
- All tests pass: `./gradlew :engine-service:test --tests "*.JmxSerializerTest"`.
- The round-trip test parses successfully with `JmxTreeWalker.parse()` (non-null result).

---

### T-009-006 — Create Test Resource JMX File

**Type**: create
**Files affected**:
- `modules/web-api/src/test/resources/sample-roundtrip.jmx` (create)
**Complexity**: S
**Dependencies**: none

**Description**: Create a representative JMX test resource used by integration tests.

**Content requirements**:
- Valid JMeter 5.x JMX XML.
- Contains: 1 `TestPlan`, 2 `ThreadGroup` (one with scheduler enabled), 2 `HTTPSamplerProxy`, 1 `ResponseAssertion`, 1 `CookieManager`.
- File size: ≤ 10KB.

**Acceptance Criteria**:
- `JmxTreeWalker.parse(Files.readString(Path.of("sample-roundtrip.jmx")))` returns a non-null `PlanNode` with `getChildren().size() >= 2`.
- The file is valid XML (no parse errors with standard Java XML parsers).

---

### T-009-007 — Wire PlanNodeSerializer into TestPlanService.importJmx

**Type**: modify
**Files affected**:
- `modules/web-api/src/main/java/com/b3meter/web/api/service/TestPlanService.java` (modify)
**Complexity**: S
**Dependencies**: T-009-002, T-009-004

**Description**: Update `importJmx()` to serialize the `PlanNode` tree to JSON and store JSON as `treeData` instead of raw XML.

**Steps**:
1. In `TestPlanService.java`, add imports:
   ```java
   import com.b3meter.engine.service.plan.PlanNodeSerializer;
   import com.fasterxml.jackson.core.JsonProcessingException;
   ```
2. After the `JmxTreeWalker.parse()` validation block (line ~173), add:
   ```java
   String treeData;
   try {
       treeData = PlanNodeSerializer.serialize(planNode);
   } catch (JsonProcessingException e) {
       throw new IllegalStateException("Failed to serialize parsed JMX tree to JSON", e);
   }
   ```
3. Replace the `rawXml` argument in the `TestPlanEntity` constructor with `treeData`.
4. Remove the `rawXml` variable from the entity construction — `rawXml` is still needed for the `JmxTreeWalker.parse()` call above, but not for storage.
5. Run `./gradlew :web-api:compileJava`.

**Acceptance Criteria**:
- `./gradlew :web-api:compileJava` exits with code 0.
- The `TestPlanEntity` is created with `treeData` containing JSON (starts with `{`).
- `rawXml` is still used for the `JmxTreeWalker.parse()` validation call.

---

### T-009-008 — Wire JmxSerializer into TestPlanService.exportJmx

**Type**: modify
**Files affected**:
- `modules/web-api/src/main/java/com/b3meter/web/api/service/TestPlanService.java` (modify)
**Complexity**: S
**Dependencies**: T-009-004, T-009-007

**Description**: Update `exportJmx()` to detect JSON-format `treeData` and convert it back to JMX XML using `JmxSerializer`.

**Steps**:
1. Add import: `import com.b3meter.engine.service.plan.JmxSerializer;`
2. Modify the `exportJmx()` method body. Replace:
   ```java
   if (treeData.trim().startsWith("<")) {
       return treeData;
   }
   return emptyJmxXml(entity.name());
   ```
   With:
   ```java
   if (treeData.trim().startsWith("{")) {
       try {
           PlanNode root = PlanNodeSerializer.deserialize(treeData);
           return JmxSerializer.toJmx(root);
       } catch (JsonProcessingException e) {
           throw new IllegalStateException(
               "Failed to reconstruct JMX XML from stored JSON tree for plan " + entity.id(), e);
       }
   }
   if (treeData.trim().startsWith("<")) {
       return treeData;
   }
   return emptyJmxXml(entity.name());
   ```
3. Run `./gradlew :web-api:compileJava`.

**Acceptance Criteria**:
- `./gradlew :web-api:compileJava` exits with code 0.
- Legacy XML plans return original XML.
- JSON-format plans return reconstructed JMX XML.
- Plans with neither format return the empty envelope (unchanged fallback).

---

### T-009-009 — Write Integration Tests for TestPlanService

**Type**: test
**Files affected**:
- `modules/web-api/src/test/java/com/b3meter/web/api/service/TestPlanServiceJmxTest.java` (create)
**Complexity**: M
**Dependencies**: T-009-006, T-009-007, T-009-008

**Description**: Write integration tests covering the full import→store→export cycle for both JSON (new) and XML (legacy) formats.

**Tests to implement**:

```java
@SpringBootTest
class TestPlanServiceJmxTest {

    @Autowired TestPlanService service;

    @Test
    void importJmx_storesJsonTreeData() throws Exception {
        byte[] jmx = Files.readAllBytes(Path.of("src/test/resources/sample-roundtrip.jmx"));
        MockMultipartFile file = new MockMultipartFile("file", "sample.jmx", "text/xml", jmx);
        TestPlanDto dto = service.importJmx(file);
        // Re-read entity to inspect treeData
        // (access via repository or a test helper)
        assertThat(treeData).startsWith("{");
    }

    @Test
    void exportJmx_fromImportedPlan_returnsValidXml() throws Exception {
        byte[] jmx = Files.readAllBytes(Path.of("src/test/resources/sample-roundtrip.jmx"));
        MockMultipartFile file = new MockMultipartFile("file", "sample.jmx", "text/xml", jmx);
        TestPlanDto dto = service.importJmx(file);
        Optional<String> exported = service.exportJmx(dto.id());
        assertThat(exported).isPresent();
        assertThat(exported.get().trim()).startsWith("<?xml");
    }

    @Test
    void exportJmx_fromLegacyXmlPlan_returnsOriginalXml() {
        // Create entity directly in repo with rawXml treeData
        String rawXml = "<jmeterTestPlan><hashTree/></jmeterTestPlan>";
        // Save entity with raw XML treeData...
        Optional<String> exported = service.exportJmx(savedId);
        assertThat(exported).hasValue(rawXml);
    }

    @Test
    void importJmx_malformedJmx_throwsIllegalArgumentException() {
        MockMultipartFile file = new MockMultipartFile("file", "bad.jmx", "text/xml",
            "not xml at all".getBytes());
        assertThatThrownBy(() -> service.importJmx(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("malformed");
    }

    @Test
    void importThenExport_roundTrip_preservesElementCount() throws Exception {
        byte[] jmx = Files.readAllBytes(Path.of("src/test/resources/sample-roundtrip.jmx"));
        MockMultipartFile file = new MockMultipartFile("file", "sample.jmx", "text/xml", jmx);
        TestPlanDto dto = service.importJmx(file);
        Optional<String> exported = service.exportJmx(dto.id());
        PlanNode reparsed = JmxTreeWalker.parse(exported.get());
        assertThat(reparsed.getChildren()).hasSizeGreaterThanOrEqualTo(2);
    }
}
```

**Acceptance Criteria**:
- All 5 tests pass: `./gradlew :web-api:test --tests "*.TestPlanServiceJmxTest"`.
- No tests use `@Disabled`.

---

### T-009-010 — Full Test Suite Validation

**Type**: test
**Files affected**: none
**Complexity**: S
**Dependencies**: T-009-009

**Description**: Run the full multi-module test suite and confirm no regressions.

**Steps**:
1. Run `./gradlew :engine-service:test` — all tests pass.
2. Run `./gradlew :web-api:test` — all tests pass.
3. Verify `./gradlew :engine-service:test` output shows new test classes `PlanNodeSerializerTest` and `JmxSerializerTest`.
4. Verify `./gradlew :web-api:test` output shows new test class `TestPlanServiceJmxTest`.
5. Commit with message: `feat(engine-service,web-api): T014 serialize PlanNode tree to JSON at JMX import time`.

**Acceptance Criteria**:
- Zero test failures across all modules.
- Zero compilation warnings introduced by new code (check `./gradlew :engine-service:compileJava --warning-mode all`).
- `SC-009.001` through `SC-009.007` all pass.
