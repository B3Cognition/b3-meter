package com.jmeternext.engine.adapter.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JmxCorpusLoader}.
 */
class JmxCorpusLoaderTest {

    // -------------------------------------------------------------------------
    // corpusFileCount — integrity
    // -------------------------------------------------------------------------

    @Test
    void corpusContainsExactly20Files() {
        assertEquals(JmxCorpusLoader.EXPECTED_CORPUS_SIZE, JmxCorpusLoader.corpusFileCount(),
                "JMX corpus should contain exactly " + JmxCorpusLoader.EXPECTED_CORPUS_SIZE
                        + " .jmx files (4 versions × 5 plans + 1 root-level plan)");
    }

    @Test
    void corpusFileCountMatchesAllCorpusFilesStreamSize() {
        long streamSize = JmxCorpusLoader.allCorpusFiles().count();
        assertEquals(JmxCorpusLoader.corpusFileCount(), streamSize,
                "corpusFileCount() and allCorpusFiles() should agree on file count");
    }

    // -------------------------------------------------------------------------
    // corpusRoot
    // -------------------------------------------------------------------------

    @Test
    void corpusRootIsNotNull() {
        assertNotNull(JmxCorpusLoader.corpusRoot());
    }

    @Test
    void corpusRootExists() {
        assertTrue(Files.exists(JmxCorpusLoader.corpusRoot()),
                "Corpus root directory should exist: " + JmxCorpusLoader.corpusRoot());
    }

    @Test
    void corpusRootIsADirectory() {
        assertTrue(Files.isDirectory(JmxCorpusLoader.corpusRoot()),
                "Corpus root should be a directory: " + JmxCorpusLoader.corpusRoot());
    }

    // -------------------------------------------------------------------------
    // allCorpusFiles — structure
    // -------------------------------------------------------------------------

    @Test
    void allCorpusFilesReturnsNonNullStream() {
        assertNotNull(JmxCorpusLoader.allCorpusFiles());
    }

    @Test
    void allCorpusFilesAllHaveJmxExtension() {
        JmxCorpusLoader.allCorpusFiles().forEach(args -> {
            Path p = (Path) args.get()[0];
            assertTrue(p.toString().endsWith(".jmx"),
                    "Expected .jmx extension but got: " + p);
        });
    }

    @Test
    void allCorpusFilesAllExistOnDisk() {
        JmxCorpusLoader.allCorpusFiles().forEach(args -> {
            Path p = (Path) args.get()[0];
            assertTrue(Files.exists(p), "Corpus file not found: " + p);
            assertTrue(Files.isRegularFile(p), "Corpus path is not a regular file: " + p);
        });
    }

    @Test
    void allCorpusFilesAreSorted() {
        List<Path> paths = JmxCorpusLoader.allCorpusFiles()
                .map(args -> (Path) args.get()[0])
                .collect(Collectors.toList());

        for (int i = 1; i < paths.size(); i++) {
            assertTrue(paths.get(i - 1).compareTo(paths.get(i)) <= 0,
                    "Files should be in sorted order, but found "
                            + paths.get(i - 1) + " before " + paths.get(i));
        }
    }

    // -------------------------------------------------------------------------
    // Version-directory coverage — all 4 JMeter version dirs represented
    // -------------------------------------------------------------------------

    @Test
    void corpusContainsJmeter3xFiles() {
        assertVersionDirectoryPresent("jmeter-3.x");
    }

    @Test
    void corpusContainsJmeter4xFiles() {
        assertVersionDirectoryPresent("jmeter-4.x");
    }

    @Test
    void corpusContainsJmeter5xFiles() {
        assertVersionDirectoryPresent("jmeter-5.x");
    }

    @Test
    void corpusContainsJmeter6xFiles() {
        assertVersionDirectoryPresent("jmeter-6.x");
    }

    @Test
    void eachVersionDirectoryContainsFiveFiles() {
        String[] versions = {"jmeter-3.x", "jmeter-4.x", "jmeter-5.x", "jmeter-6.x"};
        for (String version : versions) {
            long count = JmxCorpusLoader.allCorpusFiles()
                    .map(args -> (Path) args.get()[0])
                    .filter(p -> p.toString().contains(version))
                    .count();
            assertEquals(5L, count,
                    "Expected 5 corpus files for " + version + " but found " + count);
        }
    }

    // -------------------------------------------------------------------------
    // Plan-name coverage — all 5 plan names present in each version dir
    // -------------------------------------------------------------------------

    @Test
    void corpusContainsSimpleHttpPlanInEachVersion() {
        assertPlanNamePresentInAllVersions("simple-http.jmx");
    }

    @Test
    void corpusContainsComplexExtractorsPlanInEachVersion() {
        assertPlanNamePresentInAllVersions("complex-extractors.jmx");
    }

    @Test
    void corpusContainsJdbcParameterizedPlanInEachVersion() {
        assertPlanNamePresentInAllVersions("jdbc-parameterized.jmx");
    }

    @Test
    void corpusContainsJsr223ScriptedPlanInEachVersion() {
        assertPlanNamePresentInAllVersions("jsr223-scripted.jmx");
    }

    @Test
    void corpusContainsDistributedConfigPlanInEachVersion() {
        assertPlanNamePresentInAllVersions("distributed-config.jmx");
    }

    // -------------------------------------------------------------------------
    // Parameterized test — exercises each corpus file individually
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.jmeternext.engine.adapter.test.JmxCorpusLoader#allCorpusFiles")
    void eachCorpusFileIsReadable(Path jmxFile) throws Exception {
        assertTrue(Files.exists(jmxFile), "File should exist: " + jmxFile);
        assertTrue(Files.size(jmxFile) > 0, "File should not be empty: " + jmxFile);
        // Verify it is parseable XML (starts with '<')
        byte[] header = Files.readAllBytes(jmxFile);
        String content = new String(header).stripLeading();
        assertTrue(content.startsWith("<"),
                "JMX file should start with '<': " + jmxFile);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void assertVersionDirectoryPresent(String versionDir) {
        boolean found = JmxCorpusLoader.allCorpusFiles()
                .map(args -> (Path) args.get()[0])
                .anyMatch(p -> p.toString().contains(versionDir));
        assertTrue(found, "No corpus files found for version directory: " + versionDir);
    }

    private static void assertPlanNamePresentInAllVersions(String planFileName) {
        String[] versions = {"jmeter-3.x", "jmeter-4.x", "jmeter-5.x", "jmeter-6.x"};
        for (String version : versions) {
            boolean found = JmxCorpusLoader.allCorpusFiles()
                    .map(args -> (Path) args.get()[0])
                    .anyMatch(p -> p.toString().contains(version)
                            && p.getFileName().toString().equals(planFileName));
            assertTrue(found,
                    "Plan '" + planFileName + "' not found in version directory '" + version + "'");
        }
    }
}
