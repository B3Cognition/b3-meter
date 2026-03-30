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
package com.b3meter.engine.service.plan;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Serializes and deserializes {@link PlanNode} trees to/from JSON.
 *
 * <p>Uses a {@link PlanNodeDto} intermediary with a {@link PlanPropertyValue}
 * sealed-interface DTO hierarchy to preserve all property types through a
 * JSON round-trip — including the distinction between {@link Integer} and
 * {@link Long} values, and nested {@link PlanNode} (elementProp) and
 * {@link List} (collectionProp) values.
 *
 * <p>This class is framework-free (no Spring imports). The shared
 * {@link ObjectMapper} is stateless after construction and safe for
 * concurrent use (see NFR-009.004).
 */
public final class PlanNodeSerializer {

    /** Thread-safe shared mapper — configured once at class-load time. */
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private PlanNodeSerializer() {
        // utility class — not instantiable
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Serializes the {@link PlanNode} tree rooted at {@code root} to a JSON string.
     *
     * @param root the root node; must not be {@code null}
     * @return JSON string; never {@code null}
     * @throws JsonProcessingException if serialization fails
     */
    public static String serialize(PlanNode root) throws JsonProcessingException {
        return MAPPER.writeValueAsString(fromNode(root));
    }

    /**
     * Deserializes a {@link PlanNode} tree from a JSON string produced by
     * {@link #serialize}.
     *
     * @param json the JSON string; must not be {@code null}
     * @return the root {@link PlanNode}; never {@code null}
     * @throws JsonProcessingException if the JSON is malformed or the structure is invalid
     */
    public static PlanNode deserialize(String json) throws JsonProcessingException {
        return toNode(MAPPER.readValue(json, PlanNodeDto.class));
    }

    // =========================================================================
    // Conversion helpers
    // =========================================================================

    static PlanNodeDto fromNode(PlanNode node) {
        Map<String, PlanPropertyValue> props = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : node.getProperties().entrySet()) {
            PlanPropertyValue wrapped = wrapValue(e.getValue());
            if (wrapped != null) {
                props.put(e.getKey(), wrapped);
            }
        }
        List<PlanNodeDto> children = node.getChildren().stream()
                .map(PlanNodeSerializer::fromNode)
                .collect(Collectors.toList());
        return new PlanNodeDto(node.getTestClass(), node.getTestName(), props, children);
    }

    static PlanNode toNode(PlanNodeDto dto) {
        PlanNode.Builder builder = PlanNode.builder(dto.testClass(), dto.testName());
        for (Map.Entry<String, PlanPropertyValue> e : dto.properties().entrySet()) {
            Object raw = unwrapValue(e.getValue());
            if (raw != null) {
                builder.property(e.getKey(), raw);
            }
        }
        for (PlanNodeDto child : dto.children()) {
            builder.child(toNode(child));
        }
        return builder.build();
    }

    private static PlanPropertyValue wrapValue(Object value) {
        if (value instanceof String s)      return new StringVal(s);
        if (value instanceof Integer i)     return new IntVal(i);
        if (value instanceof Long l)        return new LongVal(l);
        if (value instanceof Double d)      return new DoubleVal(d);
        if (value instanceof Boolean b)     return new BoolVal(b);
        if (value instanceof PlanNode node) return new NodeVal(fromNode(node));
        if (value instanceof List<?> list) {
            List<PlanPropertyValue> wrapped = new ArrayList<>(list.size());
            for (Object item : list) {
                PlanPropertyValue w = wrapValue(item);
                if (w != null) {
                    wrapped.add(w);
                }
            }
            return new ListVal(wrapped);
        }
        return null; // unknown types are silently skipped (mirrors JmxWriter behaviour)
    }

    private static Object unwrapValue(PlanPropertyValue val) {
        return switch (val) {
            case StringVal(var v)  -> v;
            case IntVal(var v)     -> v;
            case LongVal(var v)    -> v;
            case DoubleVal(var v)  -> v;
            case BoolVal(var v)    -> v;
            case NodeVal(var node) -> toNode(node);
            case ListVal(var items) -> items.stream()
                    .map(PlanNodeSerializer::unwrapValue)
                    .collect(Collectors.toList());
        };
    }

    // =========================================================================
    // DTO types
    // =========================================================================

    /**
     * JSON representation of a {@link PlanNode}.
     */
    public record PlanNodeDto(
            String testClass,
            String testName,
            Map<String, PlanPropertyValue> properties,
            List<PlanNodeDto> children
    ) {}

    /**
     * Sealed interface for typed property values. Each variant carries a short
     * {@code type} discriminator so Jackson can reconstruct the correct Java type
     * without embedding fully-qualified class names in the JSON.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = StringVal.class, name = "s"),
            @JsonSubTypes.Type(value = IntVal.class,    name = "i"),
            @JsonSubTypes.Type(value = LongVal.class,   name = "l"),
            @JsonSubTypes.Type(value = DoubleVal.class,  name = "d"),
            @JsonSubTypes.Type(value = BoolVal.class,   name = "b"),
            @JsonSubTypes.Type(value = NodeVal.class,   name = "node"),
            @JsonSubTypes.Type(value = ListVal.class,   name = "list")
    })
    public sealed interface PlanPropertyValue
            permits StringVal, IntVal, LongVal, DoubleVal, BoolVal, NodeVal, ListVal {}

    /** String property value. */
    public record StringVal(String v) implements PlanPropertyValue {}

    /** Integer property value. */
    public record IntVal(int v) implements PlanPropertyValue {}

    /** Long property value (prevents Jackson from deserializing large numbers as Integer). */
    public record LongVal(long v) implements PlanPropertyValue {}

    /** Double property value. */
    public record DoubleVal(double v) implements PlanPropertyValue {}

    /** Boolean property value. */
    public record BoolVal(boolean v) implements PlanPropertyValue {}

    /** Nested PlanNode property value (elementProp). */
    public record NodeVal(PlanNodeDto node) implements PlanPropertyValue {}

    /** List property value (collectionProp). Items may be of any PlanPropertyValue type. */
    public record ListVal(List<PlanPropertyValue> items) implements PlanPropertyValue {}
}
