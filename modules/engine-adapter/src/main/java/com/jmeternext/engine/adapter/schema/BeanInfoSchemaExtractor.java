package com.jmeternext.engine.adapter.schema;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Extracts a {@link ComponentSchema.Schema} from a class's Java BeanInfo metadata.
 *
 * <p>This is the MAVERICK Alternative 3 insight: instead of hand-coding 80+
 * property panels, we derive the UI schema directly from the existing Java bean
 * descriptors that JMeter already ships with every component.
 *
 * <h2>Type mapping</h2>
 * <table>
 *   <tr><th>Java type</th><th>JSON Schema type</th></tr>
 *   <tr><td>String</td><td>string</td></tr>
 *   <tr><td>int / Integer</td><td>integer</td></tr>
 *   <tr><td>long / Long</td><td>integer</td></tr>
 *   <tr><td>double / Double / float / Float</td><td>number</td></tr>
 *   <tr><td>boolean / Boolean</td><td>boolean</td></tr>
 *   <tr><td>Enum subclass</td><td>enum (with values list)</td></tr>
 * </table>
 *
 * <h2>Filtered properties</h2>
 * The following internal JMeter property names are always excluded from the
 * generated schema because they are structural, not user-configurable:
 * {@code guiclass}, {@code testclass}, {@code testname}, {@code enabled},
 * {@code class}, {@code comments}.
 *
 * <h2>Thread-safety</h2>
 * {@code BeanInfoSchemaExtractor} is stateless and therefore inherently
 * thread-safe. A single shared instance may be used across all threads.
 */
public final class BeanInfoSchemaExtractor {

    private static final Logger LOG =
            Logger.getLogger(BeanInfoSchemaExtractor.class.getName());

    /**
     * Internal JMeter property names that are structural and should not appear
     * in the user-facing schema.
     */
    private static final Set<String> EXCLUDED_PROPERTIES = Set.of(
            "guiclass", "testclass", "testname", "enabled", "class", "comments"
    );

    /**
     * Creates a new, stateless extractor instance.
     */
    public BeanInfoSchemaExtractor() {
        // stateless — no initialisation needed
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Extracts a {@link ComponentSchema.Schema} from the supplied class.
     *
     * <p>Uses {@link Introspector#getBeanInfo(Class)} to discover all JavaBean
     * properties (those with at least a getter), maps their types to JSON Schema
     * vocabulary, and skips internal JMeter properties.
     *
     * @param clazz            the component class to introspect; must not be {@code null}
     * @param componentCategory informal grouping string, e.g. {@code "thread"},
     *                          {@code "sampler"}, {@code "listener"}
     * @return an immutable {@link ComponentSchema.Schema} describing the component's
     *         editable properties
     * @throws NullPointerException  if {@code clazz} or {@code componentCategory} is {@code null}
     * @throws SchemaExtractionException if BeanInfo introspection fails
     */
    public ComponentSchema.Schema extract(Class<?> clazz, String componentCategory) {
        Objects.requireNonNull(clazz, "clazz must not be null");
        Objects.requireNonNull(componentCategory, "componentCategory must not be null");

        BeanInfo beanInfo;
        try {
            // Stop at Object — we do not want java.lang.Object's properties
            beanInfo = Introspector.getBeanInfo(clazz, Object.class);
        } catch (IntrospectionException e) {
            throw new SchemaExtractionException(
                    "Failed to introspect " + clazz.getName(), e);
        }

        List<ComponentSchema.PropertySchema> properties = new ArrayList<>();
        for (PropertyDescriptor pd : beanInfo.getPropertyDescriptors()) {
            if (shouldSkip(pd)) {
                continue;
            }
            ComponentSchema.PropertySchema schema = toPropertySchema(pd);
            if (schema != null) {
                properties.add(schema);
            }
        }

        return new ComponentSchema.Schema(
                clazz.getSimpleName(),
                componentCategory,
                properties
        );
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} when the descriptor should be excluded from the schema.
     *
     * <p>A descriptor is skipped if:
     * <ul>
     *   <li>Its name appears in {@link #EXCLUDED_PROPERTIES}.</li>
     *   <li>It has no readable getter (write-only properties are not renderable).</li>
     *   <li>Its property type cannot be mapped to a JSON Schema type.</li>
     * </ul>
     */
    private boolean shouldSkip(PropertyDescriptor pd) {
        if (EXCLUDED_PROPERTIES.contains(pd.getName())) {
            return true;
        }
        // Write-only — no getter, cannot determine current value
        if (pd.getReadMethod() == null) {
            return true;
        }
        // Only include types we know how to render
        return resolveJsonType(pd.getPropertyType()) == null;
    }

    /**
     * Converts a {@link PropertyDescriptor} to a {@link ComponentSchema.PropertySchema}.
     *
     * @return the schema, or {@code null} if the property cannot be mapped
     */
    private ComponentSchema.PropertySchema toPropertySchema(PropertyDescriptor pd) {
        Class<?> propertyType = pd.getPropertyType();
        String jsonType = resolveJsonType(propertyType);
        if (jsonType == null) {
            LOG.log(Level.FINE,
                    "Skipping property {0}: unsupported type {1}",
                    new Object[]{pd.getName(), propertyType});
            return null;
        }

        List<String> enumValues = null;
        if ("enum".equals(jsonType)) {
            enumValues = extractEnumValues(propertyType);
        }

        Object defaultValue = resolveDefaultValue(pd);
        String displayName = (pd.getDisplayName() != null && !pd.getDisplayName().isBlank())
                ? pd.getDisplayName()
                : camelToLabel(pd.getName());

        // A property is "required" when it has a setter and no obvious default
        boolean required = pd.getWriteMethod() != null && defaultValue == null;

        return new ComponentSchema.PropertySchema(
                pd.getName(),
                displayName,
                jsonType,
                defaultValue,
                required,
                enumValues
        );
    }

    /**
     * Maps a Java property type to a JSON Schema type string.
     *
     * @param type the property class; may be a primitive or boxed type
     * @return the JSON Schema type string, or {@code null} if unsupported
     */
    String resolveJsonType(Class<?> type) {
        if (type == null) {
            return null;
        }
        if (type == String.class) {
            return "string";
        }
        if (type == int.class || type == Integer.class
                || type == long.class || type == Long.class) {
            return "integer";
        }
        if (type == double.class || type == Double.class
                || type == float.class || type == Float.class) {
            return "number";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        if (type.isEnum()) {
            return "enum";
        }
        return null; // unsupported — will be filtered out
    }

    /**
     * Extracts the string names of all constants declared by an enum type.
     *
     * @param enumType an {@link Enum} subclass
     * @return list of constant names; never {@code null}
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<String> extractEnumValues(Class<?> enumType) {
        Object[] constants = ((Class<? extends Enum>) enumType).getEnumConstants();
        if (constants == null) {
            return List.of();
        }
        return Arrays.stream(constants)
                .map(Object::toString)
                .toList();
    }

    /**
     * Attempts to determine a sensible default value for the property by calling
     * the getter on a freshly constructed instance (if there is a no-arg constructor).
     *
     * <p>If instantiation or getter invocation fails the exception is swallowed and
     * {@code null} is returned — the caller treats {@code null} as "no known default".
     *
     * @param pd the property descriptor
     * @return the default value, or {@code null}
     */
    private Object resolveDefaultValue(PropertyDescriptor pd) {
        Method getter = pd.getReadMethod();
        if (getter == null) {
            return null;
        }
        Class<?> declaringClass = getter.getDeclaringClass();
        try {
            Object instance = declaringClass.getDeclaredConstructor().newInstance();
            return getter.invoke(instance);
        } catch (Exception e) {
            // Cannot instantiate — no default discoverable
            LOG.log(Level.FINEST,
                    "Could not determine default for {0}: {1}",
                    new Object[]{pd.getName(), e.getMessage()});
            return null;
        }
    }

    /**
     * Converts a camelCase property name to a space-separated label.
     *
     * <p>Example: {@code "numThreads"} → {@code "Num Threads"}.
     */
    private String camelToLabel(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(Character.toUpperCase(name.charAt(0)));
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append(' ');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Exception type
    // -------------------------------------------------------------------------

    /**
     * Thrown when BeanInfo introspection fails at runtime.
     *
     * <p>This is an unchecked exception because introspection failures indicate a
     * programming error (wrong class passed) rather than a recoverable condition.
     */
    public static final class SchemaExtractionException extends RuntimeException {

        @java.io.Serial
        private static final long serialVersionUID = 1L;

        public SchemaExtractionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
