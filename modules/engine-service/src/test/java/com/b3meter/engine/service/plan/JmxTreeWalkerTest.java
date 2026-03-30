package com.jmeternext.engine.service.plan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JmxTreeWalker}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Parsing all 20 JMX corpus files (5 files × 4 jmeter versions)</li>
 *   <li>Verifying node counts, property values, and nesting depth</li>
 *   <li>Round-trip: parse → write → re-parse produces structurally equivalent tree</li>
 *   <li>Malformed XML produces a {@link JmxParseException}</li>
 *   <li>Detailed checks on the jmeter-6.x/simple-http.jmx fixture</li>
 * </ul>
 */
class JmxTreeWalkerTest {

    /** Absolute path to the project root (two levels up from modules/engine-service). */
    private static final Path PROJECT_ROOT = Paths.get(
            System.getProperty("user.dir"))
            .resolve("../..")
            .normalize();

    private static final Path CORPUS_ROOT =
            PROJECT_ROOT.resolve("test-data/jmx-corpus");

    // =========================================================================
    // Corpus: parse all 20 JMX files
    // =========================================================================

    /**
     * Provides one argument per JMX file in the corpus (20 total).
     */
    static Stream<Arguments> corpusFiles() {
        String[] versions = {"jmeter-3.x", "jmeter-4.x", "jmeter-5.x", "jmeter-6.x"};
        String[] files    = {
                "simple-http.jmx",
                "complex-extractors.jmx",
                "distributed-config.jmx",
                "jdbc-parameterized.jmx",
                "jsr223-scripted.jmx"
        };
        Stream.Builder<Arguments> builder = Stream.builder();
        for (String version : versions) {
            for (String file : files) {
                builder.add(Arguments.of(version, file));
            }
        }
        return builder.build();
    }

    @ParameterizedTest(name = "{0}/{1}")
    @MethodSource("corpusFiles")
    void parsesCorpusFileWithoutError(String version, String fileName) throws Exception {
        Path jmxPath = CORPUS_ROOT.resolve(version).resolve(fileName);
        assertTrue(Files.exists(jmxPath),
                "Corpus file not found: " + jmxPath);

        try (InputStream is = Files.newInputStream(jmxPath)) {
            PlanNode root = JmxTreeWalker.parse(is);
            assertNotNull(root, "Root node must not be null");
            assertEquals("jmeterTestPlan", root.getTestClass(),
                    "Root testClass should be jmeterTestPlan");
        }
    }

    @ParameterizedTest(name = "{0}/{1}")
    @MethodSource("corpusFiles")
    void eachCorpusFileHasAtLeastOneChild(String version, String fileName) throws Exception {
        PlanNode root = parseCorpus(version, fileName);
        assertFalse(root.getChildren().isEmpty(),
                "Root must have at least one child (TestPlan element)");
    }

    @ParameterizedTest(name = "{0}/{1}")
    @MethodSource("corpusFiles")
    void firstChildIsTestPlan(String version, String fileName) throws Exception {
        PlanNode root = parseCorpus(version, fileName);
        PlanNode testPlan = root.getChildren().get(0);
        assertEquals("TestPlan", testPlan.getTestClass(),
                "First child of root must be TestPlan");
    }

    @ParameterizedTest(name = "{0}/{1}")
    @MethodSource("corpusFiles")
    void testPlanHasAtLeastOneThreadGroupChild(String version, String fileName) throws Exception {
        PlanNode testPlan = parseCorpus(version, fileName).getChildren().get(0);
        boolean hasThreadGroup = testPlan.getChildren().stream()
                .anyMatch(n -> "ThreadGroup".equals(n.getTestClass()));
        assertTrue(hasThreadGroup,
                "TestPlan must contain at least one ThreadGroup child");
    }

    // =========================================================================
    // simple-http.jmx: detailed property checks
    // =========================================================================

    @Test
    void simpleHttpV6_threadGroupHas10Threads() throws Exception {
        PlanNode root = parseCorpus("jmeter-6.x", "simple-http.jmx");
        PlanNode threadGroup = findFirstByClass(root, "ThreadGroup");
        assertNotNull(threadGroup, "ThreadGroup must be present");
        assertEquals(10, threadGroup.getIntProp("ThreadGroup.num_threads"),
                "ThreadGroup.num_threads must be 10");
    }

    @Test
    void simpleHttpV6_threadGroupRampTime() throws Exception {
        PlanNode threadGroup = findFirstByClass(
                parseCorpus("jmeter-6.x", "simple-http.jmx"), "ThreadGroup");
        assertNotNull(threadGroup);
        assertEquals(1, threadGroup.getIntProp("ThreadGroup.ramp_time"),
                "ThreadGroup.ramp_time must be 1");
    }

    @Test
    void simpleHttpV6_threadGroupSameUserBoolProp() throws Exception {
        PlanNode threadGroup = findFirstByClass(
                parseCorpus("jmeter-6.x", "simple-http.jmx"), "ThreadGroup");
        assertNotNull(threadGroup);
        assertTrue(threadGroup.getBoolProp("ThreadGroup.same_user_on_next_iteration"),
                "ThreadGroup.same_user_on_next_iteration must be true");
    }

    @Test
    void simpleHttpV6_httpSamplerDomainIsExampleCom() throws Exception {
        PlanNode sampler = findFirstByClass(
                parseCorpus("jmeter-6.x", "simple-http.jmx"), "HTTPSamplerProxy");
        assertNotNull(sampler, "HTTPSamplerProxy must be present");
        assertEquals("example.com", sampler.getStringProp("HTTPSampler.domain"),
                "HTTPSampler.domain must be example.com");
    }

    @Test
    void simpleHttpV6_httpSamplerPath() throws Exception {
        PlanNode sampler = findFirstByClass(
                parseCorpus("jmeter-6.x", "simple-http.jmx"), "HTTPSamplerProxy");
        assertNotNull(sampler);
        assertEquals("/", sampler.getStringProp("HTTPSampler.path"),
                "HTTPSampler.path must be /");
    }

    @Test
    void simpleHttpV6_httpSamplerMethod() throws Exception {
        PlanNode sampler = findFirstByClass(
                parseCorpus("jmeter-6.x", "simple-http.jmx"), "HTTPSamplerProxy");
        assertNotNull(sampler);
        assertEquals("GET", sampler.getStringProp("HTTPSampler.method"),
                "HTTPSampler.method must be GET");
    }

    @Test
    void simpleHttpV6_testNameIsGETHomepage() throws Exception {
        PlanNode sampler = findFirstByClass(
                parseCorpus("jmeter-6.x", "simple-http.jmx"), "HTTPSamplerProxy");
        assertNotNull(sampler);
        assertEquals("GET Homepage", sampler.getTestName(),
                "HTTPSamplerProxy testname must be 'GET Homepage'");
    }

    @Test
    void simpleHttpV6_loopControllerLoopCount() throws Exception {
        PlanNode threadGroup = findFirstByClass(
                parseCorpus("jmeter-6.x", "simple-http.jmx"), "ThreadGroup");
        assertNotNull(threadGroup);
        PlanNode loopController = threadGroup.getElementProp("ThreadGroup.main_controller");
        assertNotNull(loopController, "ThreadGroup.main_controller elementProp must be present");
        assertEquals(5, loopController.getIntProp("LoopController.loops"),
                "LoopController.loops must be 5");
    }

    @Test
    void simpleHttpV6_nestingDepthIsAtLeastThree() throws Exception {
        PlanNode root = parseCorpus("jmeter-6.x", "simple-http.jmx");
        // root → TestPlan → ThreadGroup → HTTPSamplerProxy
        int depth = maxDepth(root);
        assertTrue(depth >= 3,
                "Tree must be at least 3 levels deep (TestPlan→ThreadGroup→Sampler), got " + depth);
    }

    // =========================================================================
    // complex-extractors.jmx: extractor node checks
    // =========================================================================

    @Test
    void complexExtractors_jsonPathExtractorPresent() throws Exception {
        PlanNode root = parseCorpus("jmeter-6.x", "complex-extractors.jmx");
        PlanNode extractor = findFirstByClass(root, "JSONPathExtractor");
        assertNotNull(extractor, "JSONPathExtractor must be parsed");
        assertEquals("$.data.id",
                extractor.getStringProp("JSONPathExtractor.jsonPathExprs"),
                "jsonPathExprs must be $.data.id");
    }

    @Test
    void complexExtractors_regexExtractorPresent() throws Exception {
        PlanNode extractor = findFirstByClass(
                parseCorpus("jmeter-6.x", "complex-extractors.jmx"), "RegexExtractor");
        assertNotNull(extractor, "RegexExtractor must be parsed");
        assertEquals("AUTH_TOKEN", extractor.getStringProp("RegexExtractor.refname"));
        assertEquals(1, extractor.getIntProp("RegexExtractor.match_number"));
    }

    @Test
    void complexExtractors_xpathExtractorPresent() throws Exception {
        PlanNode extractor = findFirstByClass(
                parseCorpus("jmeter-6.x", "complex-extractors.jmx"), "XPath2Extractor");
        assertNotNull(extractor, "XPath2Extractor must be parsed");
        assertEquals("//response/status/text()",
                extractor.getStringProp("XPathExtractor.xpathQuery"));
    }

    @Test
    void complexExtractors_boundaryExtractorPresent() throws Exception {
        PlanNode extractor = findFirstByClass(
                parseCorpus("jmeter-6.x", "complex-extractors.jmx"), "BoundaryExtractor");
        assertNotNull(extractor, "BoundaryExtractor must be parsed");
        assertEquals("sessionId=", extractor.getStringProp("BoundaryExtractor.lboundary"));
        assertEquals(";",          extractor.getStringProp("BoundaryExtractor.rboundary"));
    }

    // =========================================================================
    // distributed-config.jmx: collectionProp and boolProp checks
    // =========================================================================

    @Test
    void distributedConfig_serializeThreadGroupsFalse() throws Exception {
        PlanNode testPlan = parseCorpus("jmeter-6.x", "distributed-config.jmx")
                .getChildren().get(0);
        assertFalse(testPlan.getBoolProp("TestPlan.serialize_threadgroups"),
                "TestPlan.serialize_threadgroups must be false");
    }

    @Test
    void distributedConfig_userDefinedVariablesElementProp() throws Exception {
        PlanNode testPlan = parseCorpus("jmeter-6.x", "distributed-config.jmx")
                .getChildren().get(0);
        PlanNode args = testPlan.getElementProp("TestPlan.user_defined_variables");
        assertNotNull(args, "user_defined_variables elementProp must be present");
    }

    @Test
    void distributedConfig_collectionPropHasItems() throws Exception {
        PlanNode testPlan = parseCorpus("jmeter-6.x", "distributed-config.jmx")
                .getChildren().get(0);
        PlanNode args = testPlan.getElementProp("TestPlan.user_defined_variables");
        assertNotNull(args);
        List<Object> items = args.getCollectionProp("Arguments.arguments");
        assertFalse(items.isEmpty(), "Arguments.arguments collectionProp must have items");
    }

    @Test
    void distributedConfig_responseAssertionPresent() throws Exception {
        PlanNode root = parseCorpus("jmeter-6.x", "distributed-config.jmx");
        PlanNode assertion = findFirstByClass(root, "ResponseAssertion");
        assertNotNull(assertion, "ResponseAssertion must be parsed");
        assertEquals("Status 200", assertion.getTestName());
    }

    // =========================================================================
    // jdbc-parameterized.jmx: CSVDataSet and JDBCSampler
    // =========================================================================

    @Test
    void jdbcParameterized_csvDataSetPresent() throws Exception {
        PlanNode csv = findFirstByClass(
                parseCorpus("jmeter-6.x", "jdbc-parameterized.jmx"), "CSVDataSet");
        assertNotNull(csv, "CSVDataSet must be parsed");
        assertEquals("test-data/user-ids.csv", csv.getStringProp("filename"));
        assertTrue(csv.getBoolProp("recycle"), "recycle must be true");
    }

    @Test
    void jdbcParameterized_jdbcSamplerPresent() throws Exception {
        PlanNode jdbc = findFirstByClass(
                parseCorpus("jmeter-6.x", "jdbc-parameterized.jmx"), "JDBCSampler");
        assertNotNull(jdbc, "JDBCSampler must be parsed");
        assertEquals("pg_pool", jdbc.getStringProp("dataSource"));
        assertEquals("Prepared Select Statement", jdbc.getStringProp("queryType"));
    }

    // =========================================================================
    // jsr223-scripted.jmx: JSR223 nodes
    // =========================================================================

    @Test
    void jsr223Scripted_preProcessorPresent() throws Exception {
        PlanNode pre = findFirstByClass(
                parseCorpus("jmeter-6.x", "jsr223-scripted.jmx"), "JSR223PreProcessor");
        assertNotNull(pre, "JSR223PreProcessor must be parsed");
        assertEquals("groovy", pre.getStringProp("scriptLanguage"));
        assertEquals("setTimestamp", pre.getStringProp("cacheKey"));
    }

    @Test
    void jsr223Scripted_samplerPresent() throws Exception {
        PlanNode sampler = findFirstByClass(
                parseCorpus("jmeter-6.x", "jsr223-scripted.jmx"), "JSR223Sampler");
        assertNotNull(sampler, "JSR223Sampler must be parsed");
        assertEquals("groovy", sampler.getStringProp("scriptLanguage"));
        assertNotNull(sampler.getStringProp("script"),
                "script property must be present");
    }

    @Test
    void jsr223Scripted_postProcessorPresent() throws Exception {
        PlanNode post = findFirstByClass(
                parseCorpus("jmeter-6.x", "jsr223-scripted.jmx"), "JSR223PostProcessor");
        assertNotNull(post, "JSR223PostProcessor must be parsed");
        assertEquals("groovy", post.getStringProp("scriptLanguage"));
    }

    // =========================================================================
    // Round-trip: parse → write → parse produces equivalent structure
    // =========================================================================

    @ParameterizedTest(name = "round-trip {0}/{1}")
    @MethodSource("corpusFiles")
    void roundTripPreservesTestClassAndTestName(String version, String fileName) throws Exception {
        PlanNode original = parseCorpus(version, fileName);
        String xml = JmxWriter.write(original);
        PlanNode reparsed = JmxTreeWalker.parse(xml);

        assertEquals(original.getTestClass(), reparsed.getTestClass(),
                "Root testClass must survive round-trip");
        assertEquals(original.getTestName(), reparsed.getTestName(),
                "Root testName must survive round-trip");
    }

    @ParameterizedTest(name = "round-trip children {0}/{1}")
    @MethodSource("corpusFiles")
    void roundTripPreservesChildCount(String version, String fileName) throws Exception {
        PlanNode original = parseCorpus(version, fileName);
        String xml = JmxWriter.write(original);
        PlanNode reparsed = JmxTreeWalker.parse(xml);

        assertEquals(original.getChildren().size(), reparsed.getChildren().size(),
                "Root child count must survive round-trip");
    }

    @Test
    void roundTripSimpleHttp_threadGroupProps() throws Exception {
        PlanNode original = parseCorpus("jmeter-6.x", "simple-http.jmx");
        String xml = JmxWriter.write(original);
        PlanNode reparsed = JmxTreeWalker.parse(xml);

        PlanNode origTg    = findFirstByClass(original, "ThreadGroup");
        PlanNode reparsedTg = findFirstByClass(reparsed, "ThreadGroup");

        assertNotNull(origTg);
        assertNotNull(reparsedTg);
        assertEquals(origTg.getIntProp("ThreadGroup.num_threads"),
                     reparsedTg.getIntProp("ThreadGroup.num_threads"),
                "Thread count must survive round-trip");
        assertEquals(origTg.getBoolProp("ThreadGroup.same_user_on_next_iteration"),
                     reparsedTg.getBoolProp("ThreadGroup.same_user_on_next_iteration"),
                "Boolean prop must survive round-trip");
    }

    @Test
    void roundTripSimpleHttp_httpSamplerProps() throws Exception {
        PlanNode original = parseCorpus("jmeter-6.x", "simple-http.jmx");
        String xml = JmxWriter.write(original);
        PlanNode reparsed = JmxTreeWalker.parse(xml);

        PlanNode origSampler    = findFirstByClass(original, "HTTPSamplerProxy");
        PlanNode reparsedSampler = findFirstByClass(reparsed, "HTTPSamplerProxy");

        assertNotNull(origSampler);
        assertNotNull(reparsedSampler);
        assertEquals(origSampler.getStringProp("HTTPSampler.domain"),
                     reparsedSampler.getStringProp("HTTPSampler.domain"),
                "domain must survive round-trip");
        assertEquals(origSampler.getStringProp("HTTPSampler.method"),
                     reparsedSampler.getStringProp("HTTPSampler.method"),
                "method must survive round-trip");
    }

    @Test
    void roundTripDistributedConfig_preservesCollectionProp() throws Exception {
        PlanNode original = parseCorpus("jmeter-6.x", "distributed-config.jmx");
        String xml = JmxWriter.write(original);
        PlanNode reparsed = JmxTreeWalker.parse(xml);

        PlanNode origPlan    = original.getChildren().get(0);
        PlanNode reparsedPlan = reparsed.getChildren().get(0);

        PlanNode origArgs    = origPlan.getElementProp("TestPlan.user_defined_variables");
        PlanNode reparsedArgs = reparsedPlan.getElementProp("TestPlan.user_defined_variables");

        assertNotNull(origArgs);
        assertNotNull(reparsedArgs);

        List<Object> origItems    = origArgs.getCollectionProp("Arguments.arguments");
        List<Object> reparsedItems = reparsedArgs.getCollectionProp("Arguments.arguments");

        assertEquals(origItems.size(), reparsedItems.size(),
                "Collection item count must survive round-trip");
    }

    // =========================================================================
    // Inline XML parsing (string API)
    // =========================================================================

    @Test
    void parseString_minimalJmx() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<jmeterTestPlan version=\"1.2\" properties=\"5.0\" jmeter=\"6.0\">\n"
                + "  <hashTree>\n"
                + "    <TestPlan guiclass=\"TestPlanGui\" testclass=\"TestPlan\" testname=\"My Plan\">\n"
                + "      <stringProp name=\"TestPlan.comments\">A test</stringProp>\n"
                + "      <boolProp name=\"TestPlan.functional_mode\">false</boolProp>\n"
                + "      <elementProp name=\"TestPlan.user_defined_variables\" elementType=\"Arguments\"/>\n"
                + "    </TestPlan>\n"
                + "    <hashTree/>\n"
                + "  </hashTree>\n"
                + "</jmeterTestPlan>\n";

        PlanNode root = JmxTreeWalker.parse(xml);
        assertNotNull(root);
        assertEquals("jmeterTestPlan", root.getTestClass());

        PlanNode testPlan = root.getChildren().get(0);
        assertEquals("TestPlan", testPlan.getTestClass());
        assertEquals("My Plan",  testPlan.getTestName());
        assertEquals("A test",   testPlan.getStringProp("TestPlan.comments"));
        assertFalse(testPlan.getBoolProp("TestPlan.functional_mode"));
    }

    @Test
    void parseString_withThreadGroupAndSampler() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<jmeterTestPlan version=\"1.2\" properties=\"5.0\" jmeter=\"6.0\">\n"
                + "  <hashTree>\n"
                + "    <TestPlan testclass=\"TestPlan\" testname=\"My Plan\">\n"
                + "    </TestPlan>\n"
                + "    <hashTree>\n"
                + "      <ThreadGroup testname=\"Users\" testclass=\"ThreadGroup\">\n"
                + "        <intProp name=\"ThreadGroup.num_threads\">10</intProp>\n"
                + "        <intProp name=\"ThreadGroup.ramp_time\">5</intProp>\n"
                + "        <elementProp name=\"ThreadGroup.main_controller\" elementType=\"LoopController\">\n"
                + "          <intProp name=\"LoopController.loops\">1</intProp>\n"
                + "        </elementProp>\n"
                + "      </ThreadGroup>\n"
                + "      <hashTree>\n"
                + "        <HTTPSamplerProxy testname=\"GET Home\" testclass=\"HTTPSamplerProxy\">\n"
                + "          <stringProp name=\"HTTPSampler.domain\">example.com</stringProp>\n"
                + "          <stringProp name=\"HTTPSampler.path\">/</stringProp>\n"
                + "          <stringProp name=\"HTTPSampler.method\">GET</stringProp>\n"
                + "        </HTTPSamplerProxy>\n"
                + "        <hashTree/>\n"
                + "      </hashTree>\n"
                + "    </hashTree>\n"
                + "  </hashTree>\n"
                + "</jmeterTestPlan>\n";

        PlanNode root = JmxTreeWalker.parse(xml);
        PlanNode threadGroup = findFirstByClass(root, "ThreadGroup");
        assertNotNull(threadGroup);
        assertEquals(10, threadGroup.getIntProp("ThreadGroup.num_threads"));
        assertEquals(5,  threadGroup.getIntProp("ThreadGroup.ramp_time"));

        PlanNode loopCtrl = threadGroup.getElementProp("ThreadGroup.main_controller");
        assertNotNull(loopCtrl);
        assertEquals(1, loopCtrl.getIntProp("LoopController.loops"));

        PlanNode sampler = findFirstByClass(root, "HTTPSamplerProxy");
        assertNotNull(sampler);
        assertEquals("example.com", sampler.getStringProp("HTTPSampler.domain"));
        assertEquals("GET",          sampler.getStringProp("HTTPSampler.method"));
    }

    // =========================================================================
    // Property type handling
    // =========================================================================

    @Test
    void parsesAllPropertyTypes() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<jmeterTestPlan version=\"1.2\" properties=\"5.0\" jmeter=\"6.0\">\n"
                + "  <hashTree>\n"
                + "    <TestPlan testclass=\"TestPlan\" testname=\"Types\">\n"
                + "      <stringProp name=\"s\">hello</stringProp>\n"
                + "      <intProp name=\"i\">42</intProp>\n"
                + "      <longProp name=\"l\">9999999999</longProp>\n"
                + "      <boolProp name=\"b\">true</boolProp>\n"
                + "      <doubleProp name=\"d\">3.14</doubleProp>\n"
                + "    </TestPlan>\n"
                + "    <hashTree/>\n"
                + "  </hashTree>\n"
                + "</jmeterTestPlan>\n";

        PlanNode plan = JmxTreeWalker.parse(xml).getChildren().get(0);
        assertEquals("hello",      plan.getStringProp("s"));
        assertEquals(42,           plan.getIntProp("i"));
        assertEquals(9999999999L,  plan.getLongProp("l"));
        assertTrue(plan.getBoolProp("b"));
        assertEquals(3.14,         plan.getDoubleProp("d"), 0.001);
    }

    @Test
    void absentPropertyReturnsDefault() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<jmeterTestPlan version=\"1.2\" properties=\"5.0\" jmeter=\"6.0\">\n"
                + "  <hashTree>\n"
                + "    <TestPlan testclass=\"TestPlan\" testname=\"Empty\"/>\n"
                + "    <hashTree/>\n"
                + "  </hashTree>\n"
                + "</jmeterTestPlan>\n";

        PlanNode plan = JmxTreeWalker.parse(xml).getChildren().get(0);
        assertNull(plan.getStringProp("missing"));
        assertEquals("default", plan.getStringProp("missing", "default"));
        assertEquals(0, plan.getIntProp("missing"));
        assertEquals(7, plan.getIntProp("missing", 7));
        assertFalse(plan.getBoolProp("missing"));
        assertTrue(plan.getBoolProp("missing", true));
        assertEquals(0L,  plan.getLongProp("missing"));
        assertEquals(0.0, plan.getDoubleProp("missing"), 0.0001);
    }

    // =========================================================================
    // Malformed XML
    // =========================================================================

    @Test
    void malformedXml_throwsJmxParseException() {
        String broken = "<?xml version=\"1.0\"?><jmeterTestPlan><hashTree><unclosed>";
        assertThrows(JmxParseException.class,
                () -> JmxTreeWalker.parse(broken),
                "Malformed XML must throw JmxParseException");
    }

    @Test
    void emptyDocument_throwsJmxParseException() {
        assertThrows(JmxParseException.class,
                () -> JmxTreeWalker.parse(""),
                "Empty document must throw JmxParseException");
    }

    @Test
    void nullInputStream_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> JmxTreeWalker.parse((java.io.InputStream) null),
                "null InputStream must throw NullPointerException");
    }

    @Test
    void nullString_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> JmxTreeWalker.parse((String) null),
                "null String must throw NullPointerException");
    }

    // =========================================================================
    // Special characters in property values
    // =========================================================================

    @Test
    void xmlEntitiesInPropertiesAreParsedCorrectly() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<jmeterTestPlan version=\"1.2\" properties=\"5.0\" jmeter=\"6.0\">\n"
                + "  <hashTree>\n"
                + "    <TestPlan testclass=\"TestPlan\" testname=\"Entities\">\n"
                + "      <stringProp name=\"q\">SELECT * FROM t WHERE a &amp; b &lt; 10</stringProp>\n"
                + "    </TestPlan>\n"
                + "    <hashTree/>\n"
                + "  </hashTree>\n"
                + "</jmeterTestPlan>\n";

        PlanNode plan = JmxTreeWalker.parse(xml).getChildren().get(0);
        assertEquals("SELECT * FROM t WHERE a & b < 10", plan.getStringProp("q"),
                "XML entities must be decoded by the parser");
    }

    // =========================================================================
    // Version coverage: older formats parse successfully
    // =========================================================================

    @ParameterizedTest(name = "version coverage {0}")
    @MethodSource("allVersionsProvider")
    void simpleHttpParsesAcrossAllVersions(String version) throws Exception {
        PlanNode root = parseCorpus(version, "simple-http.jmx");
        PlanNode tg = findFirstByClass(root, "ThreadGroup");
        assertNotNull(tg, version + "/simple-http.jmx must contain a ThreadGroup");
        // Each version's simple-http has 10 threads
        assertEquals(10, tg.getIntProp("ThreadGroup.num_threads"),
                "All versions must have 10 threads");
    }

    static Stream<Arguments> allVersionsProvider() {
        return Stream.of(
                Arguments.of("jmeter-3.x"),
                Arguments.of("jmeter-4.x"),
                Arguments.of("jmeter-5.x"),
                Arguments.of("jmeter-6.x")
        );
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private PlanNode parseCorpus(String version, String fileName) throws Exception {
        Path path = CORPUS_ROOT.resolve(version).resolve(fileName);
        try (InputStream is = Files.newInputStream(path)) {
            return JmxTreeWalker.parse(is);
        }
    }

    /**
     * Depth-first search for the first node with the given {@code testClass}.
     */
    static PlanNode findFirstByClass(PlanNode root, String testClass) {
        if (testClass.equals(root.getTestClass())) return root;
        for (PlanNode child : root.getChildren()) {
            PlanNode found = findFirstByClass(child, testClass);
            if (found != null) return found;
        }
        // Also search elementProp nested nodes
        for (Object value : root.getProperties().values()) {
            if (value instanceof PlanNode) {
                PlanNode found = findFirstByClass((PlanNode) value, testClass);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Returns the maximum depth of the tree (root = depth 1).
     */
    private static int maxDepth(PlanNode node) {
        if (node.getChildren().isEmpty()) return 1;
        return 1 + node.getChildren().stream()
                .mapToInt(JmxTreeWalkerTest::maxDepth)
                .max()
                .orElse(0);
    }
}
