package com.jmeternext.engine.service.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EngineUtils}.
 *
 * <p>Covers all utility methods: property access, variable substitution,
 * null-safe split, locale management, and classpath resource loading.
 */
class EngineUtilsTest {

    // ------------------------------------------------------------------
    // getProperty / getProperty(key, default)
    // ------------------------------------------------------------------

    @Test
    void getPropertyReturnsSystemProperty() {
        System.setProperty("jmeter.test.prop.t005", "hello");
        try {
            assertEquals("hello", EngineUtils.getProperty("jmeter.test.prop.t005"));
        } finally {
            System.clearProperty("jmeter.test.prop.t005");
        }
    }

    @Test
    void getPropertyReturnsNullWhenMissing() {
        System.clearProperty("jmeter.test.prop.missing.t005");
        assertNull(EngineUtils.getProperty("jmeter.test.prop.missing.t005"));
    }

    @Test
    void getPropertyWithDefaultReturnsValueWhenPresent() {
        System.setProperty("jmeter.test.prop.default.t005", "actual");
        try {
            assertEquals("actual", EngineUtils.getProperty("jmeter.test.prop.default.t005", "fallback"));
        } finally {
            System.clearProperty("jmeter.test.prop.default.t005");
        }
    }

    @Test
    void getPropertyWithDefaultReturnsDefaultWhenMissing() {
        System.clearProperty("jmeter.test.prop.absent.t005");
        assertEquals("fallback", EngineUtils.getProperty("jmeter.test.prop.absent.t005", "fallback"));
    }

    @Test
    void getPropertyWithNullKeyThrows() {
        assertThrows(NullPointerException.class, () -> EngineUtils.getProperty(null));
    }

    // ------------------------------------------------------------------
    // loadProperties
    // ------------------------------------------------------------------

    @Test
    void loadPropertiesReadsAllEntries(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("test.properties");
        Files.writeString(file, "key1=value1\nkey2=value2\n");

        Properties props = EngineUtils.loadProperties(file);

        assertEquals("value1", props.getProperty("key1"));
        assertEquals("value2", props.getProperty("key2"));
    }

    @Test
    void loadPropertiesReturnsEmptyForEmptyFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("empty.properties");
        Files.writeString(file, "");

        Properties props = EngineUtils.loadProperties(file);

        assertTrue(props.isEmpty());
    }

    @Test
    void loadPropertiesThrowsForNonExistentFile(@TempDir Path dir) {
        Path missing = dir.resolve("no-such-file.properties");
        assertThrows(IOException.class, () -> EngineUtils.loadProperties(missing));
    }

    @Test
    void loadPropertiesThrowsForNullPath() {
        assertThrows(NullPointerException.class, () -> EngineUtils.loadProperties(null));
    }

    // ------------------------------------------------------------------
    // getJMeterVersion
    // ------------------------------------------------------------------

    @Test
    void getJMeterVersionReturnsNonNullString() {
        assertNotNull(EngineUtils.getJMeterVersion());
    }

    @Test
    void getJMeterVersionIsNotEmpty() {
        assertFalse(EngineUtils.getJMeterVersion().isBlank());
    }

    // ------------------------------------------------------------------
    // getResourceFileAsText
    // ------------------------------------------------------------------

    @Test
    void getResourceFileAsTextReadsKnownResource() throws IOException {
        // Uses a file that exists on the test classpath (standard JUnit resource)
        String content = EngineUtils.getResourceFileAsText("engine-utils-test.properties");
        assertNotNull(content);
        assertTrue(content.contains("engine.utils.test=true"));
    }

    @Test
    void getResourceFileAsTextThrowsForMissingResource() {
        assertThrows(IOException.class,
                () -> EngineUtils.getResourceFileAsText("nonexistent-resource-xyz.txt"));
    }

    @Test
    void getResourceFileAsTextThrowsForNullResource() {
        assertThrows(NullPointerException.class,
                () -> EngineUtils.getResourceFileAsText(null));
    }

    // ------------------------------------------------------------------
    // replaceVariables
    // ------------------------------------------------------------------

    @Test
    void replaceVariablesSubstitutesSingleVariable() {
        String result = EngineUtils.replaceVariables("Hello ${name}!", Map.of("name", "World"));
        assertEquals("Hello World!", result);
    }

    @Test
    void replaceVariablesSubstitutesMultipleVariables() {
        String result = EngineUtils.replaceVariables(
                "${greeting}, ${target}!",
                Map.of("greeting", "Hello", "target", "JMeter"));
        assertEquals("Hello, JMeter!", result);
    }

    @Test
    void replaceVariablesLeavesUnknownVariablesUnchanged() {
        String result = EngineUtils.replaceVariables("${known} and ${unknown}", Map.of("known", "A"));
        assertEquals("A and ${unknown}", result);
    }

    @Test
    void replaceVariablesWithEmptyMapReturnsTemplate() {
        String template = "no vars here";
        assertEquals(template, EngineUtils.replaceVariables(template, Map.of()));
    }

    @Test
    void replaceVariablesWithNullTemplateThrows() {
        assertThrows(NullPointerException.class,
                () -> EngineUtils.replaceVariables(null, Map.of()));
    }

    @Test
    void replaceVariablesWithNullMapThrows() {
        assertThrows(NullPointerException.class,
                () -> EngineUtils.replaceVariables("template", null));
    }

    // ------------------------------------------------------------------
    // split
    // ------------------------------------------------------------------

    @Test
    void splitReturnsTokensForNormalInput() {
        List<String> parts = EngineUtils.split("a,b,c", ",");
        assertEquals(List.of("a", "b", "c"), parts);
    }

    @Test
    void splitReturnsEmptyListForNullValue() {
        List<String> parts = EngineUtils.split(null, ",");
        assertTrue(parts.isEmpty());
    }

    @Test
    void splitReturnsEmptyListForEmptyValue() {
        List<String> parts = EngineUtils.split("", ",");
        assertTrue(parts.isEmpty());
    }

    @Test
    void splitReturnsSingleElementWhenNoSeparatorPresent() {
        List<String> parts = EngineUtils.split("onlyone", ",");
        assertEquals(List.of("onlyone"), parts);
    }

    @Test
    void splitThrowsForNullSeparator() {
        assertThrows(NullPointerException.class, () -> EngineUtils.split("a,b", null));
    }

    // ------------------------------------------------------------------
    // getLocale / setLocale — thread-safety
    // ------------------------------------------------------------------

    @Test
    void getLocaleReturnsNonNull() {
        assertNotNull(EngineUtils.getLocale());
    }

    @Test
    void setLocaleUpdatesGetLocale() {
        Locale original = EngineUtils.getLocale();
        try {
            EngineUtils.setLocale(Locale.FRENCH);
            assertEquals(Locale.FRENCH, EngineUtils.getLocale());
        } finally {
            EngineUtils.setLocale(original);
        }
    }

    @Test
    void setLocaleWithNullThrows() {
        assertThrows(NullPointerException.class, () -> EngineUtils.setLocale(null));
    }

    @Test
    void localeIsThreadLocalIsolated() throws InterruptedException {
        // Set locale on main thread; verify a child thread sees JVM default, not main thread's locale
        EngineUtils.setLocale(Locale.JAPANESE);
        Locale[] childLocale = new Locale[1];
        Thread child = new Thread(() -> childLocale[0] = EngineUtils.getLocale());
        child.start();
        child.join();
        // Child thread starts fresh — should see the JVM default locale (not JAPANESE set above)
        assertNotEquals(Locale.JAPANESE, childLocale[0],
                "Thread-local locale must not leak across threads");
        EngineUtils.setLocale(Locale.getDefault()); // restore
    }
}
