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
package com.b3meter.engine.adapter.jmx;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("security")
class JmxParserTest {

    private JmxParser parser;

    @BeforeEach
    void setUp() {
        parser = new JmxParser();
    }

    @Test
    void parsesValidMapXml() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("name", "Test Plan");
        original.put("threads", 10);
        String xml = parser.serialize(original);

        Map<String, Object> parsed = parser.parse(xml);
        assertEquals("Test Plan", parsed.get("name"));
        assertEquals(10, parsed.get("threads"));
    }

    @Test
    void roundTripPreservesStructure() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("planName", "HTTP Load Test");
        original.put("virtualUsers", 100);
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("url", "http://example.com");
        nested.put("method", "GET");
        original.put("sampler", nested);

        String xml = parser.serialize(original);
        Map<String, Object> parsed = parser.parse(xml);

        assertEquals(original.get("planName"), parsed.get("planName"));
        assertEquals(original.get("virtualUsers"), parsed.get("virtualUsers"));
        assertNotNull(parsed.get("sampler"));
    }

    @Test
    void parsesFromInputStream() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("key", "value");
        String xml = parser.serialize(original);
        ByteArrayInputStream stream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> parsed = parser.parse(stream);
        assertEquals("value", parsed.get("key"));
    }

    @Test
    void blocksCommonsCollectionsGadgetChain() {
        String gadgetXml = "<object-stream>" +
            "<org.apache.commons.collections4.functors.InvokerTransformer>" +
            "<iMethodName>exec</iMethodName>" +
            "</org.apache.commons.collections4.functors.InvokerTransformer>" +
            "</object-stream>";

        // Should throw either SecurityException (ForbiddenClassException wrapped)
        // or JmxParseException (CannotResolveClassException if class not on classpath)
        assertThrows(Exception.class, () -> parser.parse(gadgetXml));
    }

    @Test
    void blocksGroovyMethodClosure() {
        String gadgetXml = "<org.codehaus.groovy.runtime.MethodClosure>" +
            "<delegate class=\"java.lang.Runtime\" />" +
            "<method>exec</method>" +
            "</org.codehaus.groovy.runtime.MethodClosure>";

        assertThrows(Exception.class, () -> parser.parse(gadgetXml));
    }

    @Test
    void throwsJmxParseExceptionForMalformedXml() {
        String malformed = "<unclosed><tag>";
        assertThrows(JmxParseException.class, () -> parser.parse(malformed));
    }

    @Test
    void throwsOnNullInputStream() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse((java.io.InputStream) null));
    }

    @Test
    void throwsOnNullString() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse((String) null));
    }

    @Test
    void throwsOnBlankString() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("   "));
    }

    @Test
    void throwsOnNullTreeData() {
        assertThrows(IllegalArgumentException.class, () -> parser.serialize(null));
    }

    @Test
    void serializeProducesValidXml() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("test", "value");
        String xml = parser.serialize(data);
        assertNotNull(xml);
        assertTrue(xml.contains("test"));
        assertTrue(xml.contains("value"));
    }
}
