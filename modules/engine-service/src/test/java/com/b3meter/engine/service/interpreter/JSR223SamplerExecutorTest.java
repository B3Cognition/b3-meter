package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JSR223SamplerExecutor}.
 */
class JSR223SamplerExecutorTest {

    // =========================================================================
    // Null guards
    // =========================================================================

    @Test
    void execute_throwsOnNullNode() {
        SampleResult result = new SampleResult("jsr223-test");
        assertThrows(NullPointerException.class,
                () -> JSR223SamplerExecutor.execute(null, result, Map.of()));
    }

    @Test
    void execute_throwsOnNullResult() {
        PlanNode node = scriptNode("1 + 1", "javascript");
        assertThrows(NullPointerException.class,
                () -> JSR223SamplerExecutor.execute(node, null, Map.of()));
    }

    @Test
    void execute_throwsOnNullVariables() {
        PlanNode node = scriptNode("1 + 1", "javascript");
        SampleResult result = new SampleResult("jsr223-test");
        assertThrows(NullPointerException.class,
                () -> JSR223SamplerExecutor.execute(node, result, null));
    }

    // =========================================================================
    // Empty/missing script
    // =========================================================================

    @Test
    void execute_failsOnEmptyScript() {
        PlanNode node = scriptNode("", "javascript");
        SampleResult result = new SampleResult("jsr223-empty");

        JSR223SamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("no script"));
    }

    @Test
    void execute_failsOnBlankScript() {
        PlanNode node = scriptNode("   ", "javascript");
        SampleResult result = new SampleResult("jsr223-blank");

        JSR223SamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("no script"));
    }

    // =========================================================================
    // Script execution (JavaScript via Nashorn/GraalJS if available)
    // =========================================================================

    @Test
    void execute_runsJavaScriptAndSetsResponseBody() {
        // Skip if no JS engine available (JDK 21 removed Nashorn)
        javax.script.ScriptEngineManager mgr = new javax.script.ScriptEngineManager();
        if (mgr.getEngineByName("javascript") == null && mgr.getEngineByName("js") == null) {
            return; // No JS engine available
        }

        PlanNode node = PlanNode.builder("JSR223Sampler", "js-test")
                .property("JSR223Sampler.script",
                        "SampleResult.setResponseBody('hello from js');\n"
                        + "SampleResult.setStatusCode(200);")
                .property("JSR223Sampler.scriptLanguage", "javascript")
                .build();

        SampleResult result = new SampleResult("js-test");
        JSR223SamplerExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess());
        assertEquals("hello from js", result.getResponseBody());
        assertEquals(200, result.getStatusCode());
        assertTrue(result.getTotalTimeMs() >= 0);
    }

    @Test
    void execute_returnValueBecomesResponseBody() {
        javax.script.ScriptEngineManager mgr = new javax.script.ScriptEngineManager();
        if (mgr.getEngineByName("javascript") == null) return;

        PlanNode node = scriptNode("'computed result'", "javascript");
        SampleResult result = new SampleResult("js-return");

        JSR223SamplerExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess());
        assertEquals("computed result", result.getResponseBody());
    }

    // =========================================================================
    // Variable bindings
    // =========================================================================

    @Test
    void execute_exposesVariablesToScript() {
        javax.script.ScriptEngineManager mgr = new javax.script.ScriptEngineManager();
        if (mgr.getEngineByName("javascript") == null) return;

        Map<String, String> vars = new HashMap<>();
        vars.put("greeting", "hello");

        PlanNode node = PlanNode.builder("JSR223Sampler", "vars-test")
                .property("JSR223Sampler.script",
                        "SampleResult.setResponseBody(vars.get('greeting'));")
                .property("JSR223Sampler.scriptLanguage", "javascript")
                .build();

        SampleResult result = new SampleResult("vars-test");
        JSR223SamplerExecutor.execute(node, result, vars);

        assertTrue(result.isSuccess());
        assertEquals("hello", result.getResponseBody());
    }

    @Test
    void execute_scriptCanWriteVariables() {
        javax.script.ScriptEngineManager mgr = new javax.script.ScriptEngineManager();
        if (mgr.getEngineByName("javascript") == null) return;

        Map<String, String> vars = new HashMap<>();

        PlanNode node = PlanNode.builder("JSR223Sampler", "write-vars-test")
                .property("JSR223Sampler.script",
                        "vars.put('newVar', 'newValue');")
                .property("JSR223Sampler.scriptLanguage", "javascript")
                .build();

        SampleResult result = new SampleResult("write-vars-test");
        JSR223SamplerExecutor.execute(node, result, vars);

        assertTrue(result.isSuccess());
        assertEquals("newValue", vars.get("newVar"));
    }

    @Test
    void execute_exposesLabelBinding() {
        javax.script.ScriptEngineManager mgr = new javax.script.ScriptEngineManager();
        if (mgr.getEngineByName("javascript") == null) return;

        PlanNode node = PlanNode.builder("JSR223Sampler", "My Label")
                .property("JSR223Sampler.script",
                        "SampleResult.setResponseBody(Label);")
                .property("JSR223Sampler.scriptLanguage", "javascript")
                .build();

        SampleResult result = new SampleResult("label-test");
        JSR223SamplerExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess());
        assertEquals("My Label", result.getResponseBody());
    }

    @Test
    void execute_exposesParameterBindings() {
        javax.script.ScriptEngineManager mgr = new javax.script.ScriptEngineManager();
        if (mgr.getEngineByName("javascript") == null) return;

        PlanNode node = PlanNode.builder("JSR223Sampler", "params-test")
                .property("JSR223Sampler.script",
                        "SampleResult.setResponseBody(Parameters + '|' + args.length);")
                .property("JSR223Sampler.scriptLanguage", "javascript")
                .property("JSR223Sampler.parameters", "foo bar baz")
                .build();

        SampleResult result = new SampleResult("params-test");
        JSR223SamplerExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess());
        assertEquals("foo bar baz|3", result.getResponseBody());
    }

    // =========================================================================
    // Script error handling
    // =========================================================================

    @Test
    void execute_handlesScriptError() {
        javax.script.ScriptEngineManager mgr = new javax.script.ScriptEngineManager();
        if (mgr.getEngineByName("javascript") == null) return;

        PlanNode node = scriptNode("throw new Error('intentional failure');", "javascript");
        SampleResult result = new SampleResult("error-test");

        JSR223SamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("script error"));
        assertTrue(result.getTotalTimeMs() >= 0);
    }

    @Test
    void execute_scriptCanSetFailure() {
        javax.script.ScriptEngineManager mgr = new javax.script.ScriptEngineManager();
        if (mgr.getEngineByName("javascript") == null) return;

        PlanNode node = PlanNode.builder("JSR223Sampler", "fail-test")
                .property("JSR223Sampler.script",
                        "SampleResult.setSuccess(false);\n"
                        + "SampleResult.setResponseBody('script chose to fail');")
                .property("JSR223Sampler.scriptLanguage", "javascript")
                .build();

        SampleResult result = new SampleResult("fail-test");
        JSR223SamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertEquals("script chose to fail", result.getResponseBody());
    }

    // =========================================================================
    // Script file loading
    // =========================================================================

    @Test
    void execute_loadsScriptFromFile(@TempDir Path tempDir) throws IOException {
        javax.script.ScriptEngineManager mgr = new javax.script.ScriptEngineManager();
        if (mgr.getEngineByName("javascript") == null) return;

        Path scriptFile = tempDir.resolve("test.js");
        Files.writeString(scriptFile,
                "SampleResult.setResponseBody('from file');", StandardCharsets.UTF_8);

        PlanNode node = PlanNode.builder("JSR223Sampler", "file-test")
                .property("JSR223Sampler.scriptFile", scriptFile.toString())
                .property("JSR223Sampler.scriptLanguage", "javascript")
                .build();

        SampleResult result = new SampleResult("file-test");
        JSR223SamplerExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess());
        assertEquals("from file", result.getResponseBody());
    }

    @Test
    void execute_failsOnMissingScriptFile() {
        PlanNode node = PlanNode.builder("JSR223Sampler", "missing-file")
                .property("JSR223Sampler.scriptFile", "/nonexistent/path/script.js")
                .property("JSR223Sampler.scriptLanguage", "javascript")
                .build();

        SampleResult result = new SampleResult("missing-file");
        JSR223SamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("script file"));
    }

    // =========================================================================
    // Unknown engine
    // =========================================================================

    @Test
    void execute_failsOnUnknownEngine() {
        PlanNode node = PlanNode.builder("JSR223Sampler", "unknown-engine")
                .property("JSR223Sampler.script", "print('hi')")
                .property("JSR223Sampler.scriptLanguage", "cobol99")
                .build();

        SampleResult result = new SampleResult("unknown-engine");
        JSR223SamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("no ScriptEngine"));
    }

    // =========================================================================
    // Engine name resolution
    // =========================================================================

    @Test
    void resolveEngineName_mapsCommonAliases() {
        assertEquals("javascript", JSR223SamplerExecutor.resolveEngineName("javascript"));
        assertEquals("javascript", JSR223SamplerExecutor.resolveEngineName("js"));
        assertEquals("javascript", JSR223SamplerExecutor.resolveEngineName("ecmascript"));
        assertEquals("groovy", JSR223SamplerExecutor.resolveEngineName("groovy"));
        assertEquals("python", JSR223SamplerExecutor.resolveEngineName("python"));
        assertEquals("python", JSR223SamplerExecutor.resolveEngineName("jython"));
        assertEquals("beanshell", JSR223SamplerExecutor.resolveEngineName("beanshell"));
        assertEquals("beanshell", JSR223SamplerExecutor.resolveEngineName("bsh"));
    }

    @Test
    void resolveEngineName_isCaseInsensitive() {
        assertEquals("javascript", JSR223SamplerExecutor.resolveEngineName("JavaScript"));
        assertEquals("groovy", JSR223SamplerExecutor.resolveEngineName("GROOVY"));
    }

    @Test
    void resolveEngineName_passesUnknownThrough() {
        assertEquals("ruby", JSR223SamplerExecutor.resolveEngineName("ruby"));
    }

    @Test
    void resolveEngineName_handlesNull() {
        assertEquals("javascript", JSR223SamplerExecutor.resolveEngineName(null));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static PlanNode scriptNode(String script, String language) {
        return PlanNode.builder("JSR223Sampler", "jsr223-test")
                .property("JSR223Sampler.script", script)
                .property("JSR223Sampler.scriptLanguage", language)
                .build();
    }
}
