package com.jmeternext.engine.adapter.schema;

import java.util.List;

/**
 * Describes a JMeter component's configurable properties, extracted from Java
 * BeanInfo metadata by {@link BeanInfoSchemaExtractor}.
 *
 * <p>Both records are deliberately immutable value objects — they carry no
 * behaviour and are safe to share across threads.
 *
 * <p>The JSON schema type strings in {@link PropertySchema#type()} follow the
 * JSON Schema draft-07 vocabulary so that the React frontend can consume them
 * directly without re-mapping.
 */
public final class ComponentSchema {

    private ComponentSchema() {
        // utility class — all public API is through the inner records
    }

    // -------------------------------------------------------------------------
    // ComponentSchema record
    // -------------------------------------------------------------------------

    /**
     * Top-level schema for a single JMeter component class.
     *
     * @param componentName     simple class name, e.g. {@code "ThreadGroup"}
     * @param componentCategory informal grouping, e.g. {@code "thread"}, {@code "sampler"}
     * @param properties        ordered list of property schemas; never {@code null}
     */
    public record Schema(
            String componentName,
            String componentCategory,
            List<PropertySchema> properties
    ) {
        /**
         * Compact constructor — defensive copy of the properties list.
         */
        public Schema {
            if (componentName == null || componentName.isBlank()) {
                throw new IllegalArgumentException("componentName must not be blank");
            }
            if (componentCategory == null) {
                throw new IllegalArgumentException("componentCategory must not be null");
            }
            properties = List.copyOf(properties); // defensive + immutable
        }
    }

    // -------------------------------------------------------------------------
    // PropertySchema record
    // -------------------------------------------------------------------------

    /**
     * Describes a single editable property on a component.
     *
     * <p>The {@code type} field uses JSON Schema vocabulary:
     * <ul>
     *   <li>{@code "string"}  — any text value</li>
     *   <li>{@code "integer"} — whole numbers (mapped from {@code int} / {@code Integer})</li>
     *   <li>{@code "number"}  — floating-point (mapped from {@code double} / {@code float})</li>
     *   <li>{@code "boolean"} — true/false toggle</li>
     *   <li>{@code "enum"}    — one of the values listed in {@code enumValues}</li>
     * </ul>
     *
     * @param name         property key as used in JMX / the property map
     * @param displayName  human-readable label derived from BeanInfo display name
     * @param type         JSON Schema type string (see above)
     * @param defaultValue default value for this property; may be {@code null}
     * @param required     whether the property must be provided
     * @param enumValues   allowed values when {@code type} is {@code "enum"},
     *                     {@code null} otherwise
     */
    public record PropertySchema(
            String name,
            String displayName,
            String type,
            Object defaultValue,
            boolean required,
            List<String> enumValues
    ) {
        /** Compact constructor — validates required fields and defensive-copies enum values. */
        public PropertySchema {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            if (displayName == null) {
                throw new IllegalArgumentException("displayName must not be null");
            }
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("type must not be blank");
            }
            enumValues = (enumValues != null) ? List.copyOf(enumValues) : null;
        }
    }
}
