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
package com.b3meter.engine.adapter.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LegacyPropertyBridge}.
 *
 * <p>Covers factory methods, property access (direct + mapped + default),
 * key translation, and snapshot export.
 */
class LegacyPropertyBridgeTest {

    // ------------------------------------------------------------------
    // fromProperties factory
    // ------------------------------------------------------------------

    @Test
    void fromPropertiesReadsDirectKey() {
        Properties props = new Properties();
        props.setProperty("custom.key", "custom-value");

        LegacyPropertyBridge bridge = LegacyPropertyBridge.fromProperties(props);

        assertEquals("custom-value", bridge.get("custom.key"));
    }

    @Test
    void fromPropertiesRejectsNull() {
        assertThrows(NullPointerException.class,
                () -> LegacyPropertyBridge.fromProperties(null));
    }

    @Test
    void fromPropertiesIsolatesOriginal() {
        Properties props = new Properties();
        props.setProperty("key", "original");
        LegacyPropertyBridge bridge = LegacyPropertyBridge.fromProperties(props);

        // Mutating the original Properties must not affect the bridge
        props.setProperty("key", "mutated");

        assertEquals("original", bridge.get("key"));
    }

    // ------------------------------------------------------------------
    // fromFile factory
    // ------------------------------------------------------------------

    @Test
    void fromFileLoadsPropertiesFromDisk(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("jmeter.properties");
        Files.writeString(file, "jmeter.proxy.port=8080\n");

        LegacyPropertyBridge bridge = LegacyPropertyBridge.fromFile(file);

        assertEquals("8080", bridge.get("jmeter.proxy.port"));
    }

    @Test
    void fromFileThrowsForMissingFile(@TempDir Path dir) {
        Path missing = dir.resolve("no-such.properties");
        assertThrows(IOException.class, () -> LegacyPropertyBridge.fromFile(missing));
    }

    @Test
    void fromFileRejectsNullPath() {
        assertThrows(NullPointerException.class,
                () -> LegacyPropertyBridge.fromFile(null));
    }

    // ------------------------------------------------------------------
    // get(key) — direct lookup
    // ------------------------------------------------------------------

    @Test
    void getReturnsNullForMissingKey() {
        LegacyPropertyBridge bridge = LegacyPropertyBridge.fromProperties(new Properties());
        assertNull(bridge.get("does.not.exist"));
    }

    @Test
    void getRejectsNullKey() {
        LegacyPropertyBridge bridge = LegacyPropertyBridge.fromProperties(new Properties());
        assertThrows(NullPointerException.class, () -> bridge.get(null));
    }

    // ------------------------------------------------------------------
    // get(key, defaultValue)
    // ------------------------------------------------------------------

    @Test
    void getWithDefaultReturnsValueWhenPresent() {
        Properties props = new Properties();
        props.setProperty("jmeter.proxy.port", "9090");
        LegacyPropertyBridge bridge = LegacyPropertyBridge.fromProperties(props);

        assertEquals("9090", bridge.get("jmeter.proxy.port", "8888"));
    }

    @Test
    void getWithDefaultReturnsDefaultWhenMissing() {
        LegacyPropertyBridge bridge = LegacyPropertyBridge.fromProperties(new Properties());
        assertEquals("8888", bridge.get("jmeter.proxy.port", "8888"));
    }

    @Test
    void getWithDefaultRejectsNullKey() {
        LegacyPropertyBridge bridge = LegacyPropertyBridge.fromProperties(new Properties());
        assertThrows(NullPointerException.class, () -> bridge.get(null, "default"));
    }

    // ------------------------------------------------------------------
    // newKeyFor — legacy key translation
    // ------------------------------------------------------------------

    @Test
    void newKeyForTranslatesKnownLegacyKey() {
        LegacyPropertyBridge bridge = LegacyPropertyBridge.fromProperties(new Properties());
        assertEquals(
                "com.b3meter.sampler.output.format",
                bridge.newKeyFor("jmeter.save.saveservice.output_format"));
    }

    @Test
    void newKeyForReturnsKeyItselfForUnknownKey() {
        LegacyPropertyBridge bridge = LegacyPropertyBridge.fromProperties(new Properties());
        assertEquals("unknown.custom.key", bridge.newKeyFor("unknown.custom.key"));
    }

    @Test
    void newKeyForRejectsNullKey() {
        LegacyPropertyBridge bridge = LegacyPropertyBridge.fromProperties(new Properties());
        assertThrows(NullPointerException.class, () -> bridge.newKeyFor(null));
    }

    // ------------------------------------------------------------------
    // asProperties — snapshot export
    // ------------------------------------------------------------------

    @Test
    void asPropertiesReturnsAllEntries() {
        Properties props = new Properties();
        props.setProperty("a", "1");
        props.setProperty("b", "2");
        LegacyPropertyBridge bridge = LegacyPropertyBridge.fromProperties(props);

        Properties snapshot = bridge.asProperties();

        assertEquals("1", snapshot.getProperty("a"));
        assertEquals("2", snapshot.getProperty("b"));
    }

    @Test
    void asPropertiesReturnsCopyNotLiveView() {
        Properties props = new Properties();
        props.setProperty("key", "original");
        LegacyPropertyBridge bridge = LegacyPropertyBridge.fromProperties(props);

        Properties snapshot = bridge.asProperties();
        snapshot.setProperty("key", "mutated");

        // Bridge must still return the original value
        assertEquals("original", bridge.get("key"));
    }

    // ------------------------------------------------------------------
    // Coverage of all known legacy key mappings (representative sample)
    // ------------------------------------------------------------------

    @Test
    void savserviceTimeMapsToElapsedMs() {
        LegacyPropertyBridge bridge = LegacyPropertyBridge.fromProperties(new Properties());
        assertEquals(
                "com.b3meter.sampler.save.elapsed_ms",
                bridge.newKeyFor("jmeter.save.saveservice.time"));
    }

    @Test
    void httpConnectTimeoutMapsCorrectly() {
        LegacyPropertyBridge bridge = LegacyPropertyBridge.fromProperties(new Properties());
        assertEquals(
                "com.b3meter.http.connect_timeout_ms",
                bridge.newKeyFor("HTTPSampler.connect_timeout"));
    }

    @Test
    void reportGeneratorTitleMapsCorrectly() {
        LegacyPropertyBridge bridge = LegacyPropertyBridge.fromProperties(new Properties());
        assertEquals(
                "com.b3meter.report.title",
                bridge.newKeyFor("jmeter.reportgenerator.report_title"));
    }
}
