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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable node in a parsed JMX test-plan tree.
 *
 * <p>Each {@code PlanNode} corresponds to one JMX element (e.g. {@code TestPlan},
 * {@code ThreadGroup}, {@code HTTPSamplerProxy}) together with its typed properties
 * and the ordered list of child nodes that appeared inside the following
 * {@code <hashTree>} block.
 *
 * <p>Properties are stored as {@code Object} values with typed accessor helpers
 * ({@link #getStringProp}, {@link #getIntProp}, {@link #getBoolProp}) that return
 * sensible defaults when the property is absent or of the wrong type.
 *
 * <p>Instances are created via the nested {@link Builder}:
 * <pre>{@code
 * PlanNode node = PlanNode.builder("ThreadGroup", "Users")
 *         .property("ThreadGroup.num_threads", 10)
 *         .property("ThreadGroup.ramp_time", 5)
 *         .build();
 * }</pre>
 *
 * <p>This class has zero dependency on old jMeter classes — it is plain Java.
 */
public final class PlanNode {

    /** JMX {@code testclass} attribute, e.g. {@code "ThreadGroup"}. Never {@code null}. */
    private final String testClass;

    /** JMX {@code testname} attribute (the user-visible label). Never {@code null}. */
    private final String testName;

    /**
     * Typed properties parsed from child {@code <stringProp>}, {@code <intProp>},
     * {@code <boolProp>}, {@code <longProp>}, {@code <doubleProp>},
     * {@code <elementProp>}, and {@code <collectionProp>} elements.
     *
     * <p>Values are one of: {@link String}, {@link Integer}, {@link Long},
     * {@link Double}, {@link Boolean}, {@link PlanNode} (elementProp),
     * or {@code List<Object>} (collectionProp). Never {@code null} as a map,
     * though individual values may be {@code null} for explicitly absent props.
     */
    private final Map<String, Object> properties;

    /**
     * Direct children — the nodes that appeared in the {@code <hashTree>}
     * block immediately after this element. Unmodifiable.
     */
    private final List<PlanNode> children;

    private PlanNode(Builder builder) {
        this.testClass  = builder.testClass;
        this.testName   = builder.testName;
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(builder.properties));
        this.children   = Collections.unmodifiableList(new ArrayList<>(builder.children));
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Creates a new {@link Builder} pre-populated with {@code testClass} and
     * {@code testName}.
     *
     * @param testClass JMX {@code testclass} attribute value; must not be {@code null}
     * @param testName  JMX {@code testname} attribute value; must not be {@code null}
     * @return a fresh builder
     */
    public static Builder builder(String testClass, String testName) {
        return new Builder(testClass, testName);
    }

    // -------------------------------------------------------------------------
    // Core accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the JMX element class, e.g. {@code "ThreadGroup"}.
     *
     * @return non-null testClass string
     */
    public String getTestClass() {
        return testClass;
    }

    /**
     * Returns the user-visible test name, e.g. {@code "Users"}.
     *
     * @return non-null testName string
     */
    public String getTestName() {
        return testName;
    }

    /**
     * Returns an unmodifiable view of all typed properties for this node.
     *
     * @return immutable property map; never {@code null}
     */
    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * Returns the ordered, unmodifiable list of child nodes.
     *
     * @return immutable child list; never {@code null}
     */
    public List<PlanNode> getChildren() {
        return children;
    }

    // -------------------------------------------------------------------------
    // Typed property accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the string value of the property with the given {@code name},
     * or {@code null} if absent or not a {@link String}.
     *
     * @param name property name; must not be {@code null}
     * @return string value, or {@code null}
     */
    public String getStringProp(String name) {
        Object value = properties.get(Objects.requireNonNull(name, "name must not be null"));
        return (value instanceof String) ? (String) value : null;
    }

    /**
     * Returns the string value of the property, or {@code defaultValue} if absent.
     *
     * @param name         property name; must not be {@code null}
     * @param defaultValue fallback value
     * @return string value, or {@code defaultValue}
     */
    public String getStringProp(String name, String defaultValue) {
        String v = getStringProp(name);
        return (v != null) ? v : defaultValue;
    }

    /**
     * Returns the int value of the property with the given {@code name},
     * or {@code 0} if absent or not an {@link Integer}.
     *
     * @param name property name; must not be {@code null}
     * @return int value, or {@code 0}
     */
    public int getIntProp(String name) {
        Object value = properties.get(Objects.requireNonNull(name, "name must not be null"));
        return (value instanceof Integer) ? (Integer) value : 0;
    }

    /**
     * Returns the int value of the property, or {@code defaultValue} if absent.
     *
     * @param name         property name; must not be {@code null}
     * @param defaultValue fallback value
     * @return int value, or {@code defaultValue}
     */
    public int getIntProp(String name, int defaultValue) {
        Object value = properties.get(Objects.requireNonNull(name, "name must not be null"));
        return (value instanceof Integer) ? (Integer) value : defaultValue;
    }

    /**
     * Returns the long value of the property with the given {@code name},
     * or {@code 0L} if absent or not a {@link Long}.
     *
     * @param name property name; must not be {@code null}
     * @return long value, or {@code 0L}
     */
    public long getLongProp(String name) {
        Object value = properties.get(Objects.requireNonNull(name, "name must not be null"));
        if (value instanceof Long)    return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        return 0L;
    }

    /**
     * Returns the boolean value of the property with the given {@code name},
     * or {@code false} if absent or not a {@link Boolean}.
     *
     * @param name property name; must not be {@code null}
     * @return boolean value, or {@code false}
     */
    public boolean getBoolProp(String name) {
        Object value = properties.get(Objects.requireNonNull(name, "name must not be null"));
        return (value instanceof Boolean) && (Boolean) value;
    }

    /**
     * Returns the boolean value of the property, or {@code defaultValue} if absent.
     *
     * @param name         property name; must not be {@code null}
     * @param defaultValue fallback value
     * @return boolean value, or {@code defaultValue}
     */
    public boolean getBoolProp(String name, boolean defaultValue) {
        Object value = properties.get(Objects.requireNonNull(name, "name must not be null"));
        return (value instanceof Boolean) ? (Boolean) value : defaultValue;
    }

    /**
     * Returns the double value of the property with the given {@code name},
     * or {@code 0.0} if absent or not a {@link Double}.
     *
     * @param name property name; must not be {@code null}
     * @return double value, or {@code 0.0}
     */
    public double getDoubleProp(String name) {
        Object value = properties.get(Objects.requireNonNull(name, "name must not be null"));
        return (value instanceof Double) ? (Double) value : 0.0;
    }

    /**
     * Returns the nested {@link PlanNode} for an {@code <elementProp>} property,
     * or {@code null} if absent or not a {@link PlanNode}.
     *
     * @param name property name; must not be {@code null}
     * @return nested node, or {@code null}
     */
    public PlanNode getElementProp(String name) {
        Object value = properties.get(Objects.requireNonNull(name, "name must not be null"));
        return (value instanceof PlanNode) ? (PlanNode) value : null;
    }

    /**
     * Returns the list value for a {@code <collectionProp>} property,
     * or an empty list if absent or not a {@link List}.
     *
     * @param name property name; must not be {@code null}
     * @return unmodifiable list of items; never {@code null}
     */
    @SuppressWarnings("unchecked")
    public List<Object> getCollectionProp(String name) {
        Object value = properties.get(Objects.requireNonNull(name, "name must not be null"));
        return (value instanceof List) ? Collections.unmodifiableList((List<Object>) value)
                                       : Collections.emptyList();
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return "PlanNode{testClass='" + testClass + "', testName='" + testName
                + "', properties=" + properties.size()
                + ", children=" + children.size() + "}";
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /**
     * Mutable builder for {@link PlanNode}.
     */
    public static final class Builder {

        private final String testClass;
        private final String testName;
        private final Map<String, Object> properties = new LinkedHashMap<>();
        private final List<PlanNode> children = new ArrayList<>();

        private Builder(String testClass, String testName) {
            this.testClass = Objects.requireNonNull(testClass, "testClass must not be null");
            this.testName  = Objects.requireNonNull(testName,  "testName must not be null");
        }

        /**
         * Adds a typed property.
         *
         * @param name  property name; must not be {@code null}
         * @param value property value (String, Integer, Long, Double, Boolean, PlanNode, or List)
         * @return this builder
         */
        public Builder property(String name, Object value) {
            properties.put(Objects.requireNonNull(name, "name must not be null"), value);
            return this;
        }

        /**
         * Appends a child node.
         *
         * @param child the child node; must not be {@code null}
         * @return this builder
         */
        public Builder child(PlanNode child) {
            children.add(Objects.requireNonNull(child, "child must not be null"));
            return this;
        }

        /**
         * Builds and returns the immutable {@link PlanNode}.
         *
         * @return new node
         */
        public PlanNode build() {
            return new PlanNode(this);
        }
    }
}
