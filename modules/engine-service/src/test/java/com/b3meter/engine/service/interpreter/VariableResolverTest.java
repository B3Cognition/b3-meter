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
package com.b3meter.engine.service.interpreter;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link VariableResolver}.
 */
class VariableResolverTest {

    @Test
    void noReferences_returnsOriginal() {
        Map<String, String> vars = Map.of("x", "1");
        assertEquals("hello world", VariableResolver.resolve("hello world", vars));
    }

    @Test
    void simpleSubstitution() {
        Map<String, String> vars = Map.of("host", "example.com");
        assertEquals("http://example.com/api",
                VariableResolver.resolve("http://${host}/api", vars));
    }

    @Test
    void multipleSubstitutions() {
        Map<String, String> vars = Map.of("proto", "https", "host", "api.example.com", "path", "/v1");
        assertEquals("https://api.example.com/v1",
                VariableResolver.resolve("${proto}://${host}${path}", vars));
    }

    @Test
    void missingVariable_leftAsLiteral() {
        Map<String, String> vars = Map.of();
        assertEquals("${unknown}", VariableResolver.resolve("${unknown}", vars));
    }

    @Test
    void partiallyMissingVariables() {
        Map<String, String> vars = Map.of("known", "value");
        assertEquals("value-${missing}",
                VariableResolver.resolve("${known}-${missing}", vars));
    }

    @Test
    void propertyBuiltinFunction() {
        Map<String, String> vars = Map.of("server.host", "myserver");
        assertEquals("myserver",
                VariableResolver.resolve("${__property(server.host)}", vars));
    }

    @Test
    void propertyBuiltinMissing_leftAsLiteral() {
        Map<String, String> vars = Map.of();
        String result = VariableResolver.resolve("${__property(missing.key)}", vars);
        assertTrue(result.startsWith("${"), "should be left as literal: " + result);
    }

    @Test
    void emptyString_returnsEmpty() {
        assertEquals("", VariableResolver.resolve("", Map.of()));
    }

    @Test
    void variableWithSpacesInName() {
        // Spaces inside ${} are trimmed per implementation
        Map<String, String> vars = Map.of("myVar", "resolved");
        assertEquals("resolved", VariableResolver.resolve("${ myVar }", vars));
    }

    @Test
    void nullTemplate_throws() {
        assertThrows(NullPointerException.class,
                () -> VariableResolver.resolve(null, Map.of()));
    }

    @Test
    void nullVariables_throws() {
        assertThrows(NullPointerException.class,
                () -> VariableResolver.resolve("template", null));
    }

    @Test
    void variableAtStartAndEnd() {
        Map<String, String> vars = Map.of("a", "start", "b", "end");
        assertEquals("start-middle-end",
                VariableResolver.resolve("${a}-middle-${b}", vars));
    }

    @Test
    void resolveInUrl() {
        Map<String, String> vars = Map.of("userId", "42", "env", "stage");
        String template = "https://${env}.api.com/users/${userId}";
        assertEquals("https://stage.api.com/users/42",
                VariableResolver.resolve(template, vars));
    }
}
