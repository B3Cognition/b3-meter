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

import com.b3meter.engine.service.plan.JmxTreeWalker;
import com.b3meter.engine.service.plan.PlanNode;
import com.b3meter.web.api.controller.dto.TestPlanDto;
import com.b3meter.web.api.repository.TestPlanEntity;
import com.b3meter.web.api.repository.TestPlanRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;

import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the JMX import/export cycle in {@link TestPlanService}.
 *
 * <p>Boots the full Spring context with the H2 in-memory database. Tests verify:
 * <ul>
 *   <li>After import, {@code treeData} starts with {@code {} (JSON format — SC-009.002)</li>
 *   <li>Export from a JSON-format plan returns valid XML starting with {@code <?xml}
 *       (SC-009.003)</li>
 *   <li>Export from a legacy XML plan returns the original XML unchanged (SC-009.004)</li>
 *   <li>Malformed JMX upload throws {@link IllegalArgumentException} (US-009.1)</li>
 *   <li>Import then export round-trip preserves tree structure (SC-009.001)</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TestPlanServiceJmxTest {

    @Autowired
    TestPlanService service;

    @Autowired
    TestPlanRepository repository;

    // =========================================================================
    // T-009-007: importJmx stores JSON treeData (SC-009.002)
    // =========================================================================

    @Test
    void importJmx_storesJsonTreeData() throws Exception {
        MockMultipartFile file = loadSampleJmx("sample-roundtrip.jmx");
        TestPlanDto dto = service.importJmx(file);

        assertNotNull(dto.treeData(), "treeData must not be null after import");
        assertTrue(dto.treeData().trim().startsWith("{"),
                "treeData must be JSON (start with '{') after import");
    }

    @Test
    void importJmx_returnedDto_hasNonNullId() throws Exception {
        MockMultipartFile file = loadSampleJmx("sample-roundtrip.jmx");
        TestPlanDto dto = service.importJmx(file);
        assertNotNull(dto.id(), "Imported plan must have an ID");
    }

    @Test
    void importJmx_derivesNameFromFilename() throws Exception {
        MockMultipartFile file = loadSampleJmx("sample-roundtrip.jmx");
        TestPlanDto dto = service.importJmx(file);
        assertEquals("sample-roundtrip", dto.name(),
                "Plan name must be derived from the filename without extension");
    }

    // =========================================================================
    // T-009-008: exportJmx from JSON-format plan returns valid XML (SC-009.003)
    // =========================================================================

    @Test
    void exportJmx_fromImportedPlan_returnsPresent() throws Exception {
        MockMultipartFile file = loadSampleJmx("sample-roundtrip.jmx");
        TestPlanDto dto = service.importJmx(file);

        Optional<String> exported = service.exportJmx(dto.id());
        assertTrue(exported.isPresent(), "exportJmx must return a value for an existing plan");
    }

    @Test
    void exportJmx_fromImportedPlan_returnsValidXml() throws Exception {
        MockMultipartFile file = loadSampleJmx("sample-roundtrip.jmx");
        TestPlanDto dto = service.importJmx(file);

        Optional<String> exported = service.exportJmx(dto.id());
        assertTrue(exported.isPresent());
        String xml = exported.get().trim();
        assertTrue(xml.startsWith("<?xml") || xml.startsWith("<jmeterTestPlan"),
                "Exported content must be XML (start with '<?xml' or '<jmeterTestPlan')");
    }

    @Test
    void exportJmx_fromImportedPlan_containsJmeterTestPlanElement() throws Exception {
        MockMultipartFile file = loadSampleJmx("sample-roundtrip.jmx");
        TestPlanDto dto = service.importJmx(file);

        String xml = service.exportJmx(dto.id()).orElseThrow();
        assertTrue(xml.contains("<jmeterTestPlan"), "Exported XML must contain <jmeterTestPlan>");
    }

    // =========================================================================
    // Legacy XML format: export returns original XML unchanged (SC-009.004)
    // =========================================================================

    @Test
    void exportJmx_fromLegacyXmlPlan_returnsOriginalXmlUnchanged() {
        String rawXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<jmeterTestPlan version=\"1.2\" properties=\"5.0\" jmeter=\"5.6.3\">\n"
                + "  <hashTree/>\n"
                + "</jmeterTestPlan>\n";
        String id = UUID.randomUUID().toString();
        repository.save(new TestPlanEntity(id, "Legacy Plan", "system", rawXml,
                Instant.now(), Instant.now(), null));

        Optional<String> exported = service.exportJmx(id);
        assertTrue(exported.isPresent());
        assertEquals(rawXml, exported.get(),
                "Legacy XML treeData must be returned verbatim without modification");
    }

    // =========================================================================
    // Error handling
    // =========================================================================

    @Test
    void importJmx_malformedJmx_throwsIllegalArgumentException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "bad.jmx", "text/xml", "not xml at all".getBytes());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.importJmx(file),
                "Malformed JMX must throw IllegalArgumentException");
        assertTrue(ex.getMessage().toLowerCase().contains("malformed")
                || ex.getMessage().toLowerCase().contains("rejected"),
                "Exception message must mention 'malformed' or 'rejected'");
    }

    @Test
    void exportJmx_nonExistentPlan_returnsEmpty() {
        Optional<String> exported = service.exportJmx("plan-that-does-not-exist-" + UUID.randomUUID());
        assertTrue(exported.isEmpty(),
                "exportJmx must return empty Optional for a non-existent plan");
    }

    // =========================================================================
    // Round-trip fidelity (SC-009.001)
    // =========================================================================

    @Test
    void importThenExport_roundTrip_preservesStructure() throws Exception {
        MockMultipartFile file = loadSampleJmx("sample-roundtrip.jmx");
        TestPlanDto dto = service.importJmx(file);

        String exportedXml = service.exportJmx(dto.id()).orElseThrow();

        // Re-parse the exported XML — must succeed (non-null result)
        PlanNode reparsed = JmxTreeWalker.parse(exportedXml);
        assertNotNull(reparsed, "Re-parsed exported XML must produce non-null PlanNode");
        // The sample JMX has at least the jmeterTestPlan root with at least 1 child (TestPlan)
        assertFalse(reparsed.getChildren().isEmpty(),
                "Re-parsed tree must have at least one child (TestPlan)");
    }

    @Test
    void importThenExport_roundTrip_preservesThreadGroupCount() throws Exception {
        MockMultipartFile file = loadSampleJmx("sample-roundtrip.jmx");
        TestPlanDto dto = service.importJmx(file);

        String exportedXml = service.exportJmx(dto.id()).orElseThrow();
        PlanNode reparsed = JmxTreeWalker.parse(exportedXml);

        // The sample JMX has 2 ThreadGroups under TestPlan
        assertNotNull(reparsed);
        assertFalse(reparsed.getChildren().isEmpty());
        PlanNode testPlan = reparsed.getChildren().get(0);
        long threadGroupCount = testPlan.getChildren().stream()
                .filter(n -> "ThreadGroup".equals(n.getTestClass()))
                .count();
        assertEquals(2, threadGroupCount,
                "Re-parsed tree must contain 2 ThreadGroups matching the original JMX");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Loads a JMX test resource from the test classpath as a {@link MockMultipartFile}.
     *
     * @param resourceName the filename (looked up in {@code src/test/resources/})
     * @return multipart file wrapping the resource bytes
     * @throws Exception if the resource cannot be found or read
     */
    private MockMultipartFile loadSampleJmx(String resourceName) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            assertNotNull(is, "Test resource not found on classpath: " + resourceName);
            byte[] bytes = is.readAllBytes();
            return new MockMultipartFile("file", resourceName, "text/xml", bytes);
        }
    }
}
