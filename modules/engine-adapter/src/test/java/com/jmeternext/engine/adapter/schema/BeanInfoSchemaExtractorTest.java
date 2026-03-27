package com.jmeternext.engine.adapter.schema;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BeanInfoSchemaExtractor}.
 *
 * <p>Tests use small inner bean classes so that there is no dependency on actual
 * JMeter classes. Each bean is designed to exercise a specific type-mapping or
 * filtering scenario.
 */
class BeanInfoSchemaExtractorTest {

    private BeanInfoSchemaExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new BeanInfoSchemaExtractor();
    }

    // =========================================================================
    // Test beans
    // =========================================================================

    /** Minimal bean with properties of every supported primitive/boxed type. */
    public static class AllTypesBean {

        private String name = "default-name";
        private int count = 5;
        private long bigCount = 100L;
        private double ratio = 0.5;
        private float rate = 1.0f;
        /** Use "active" rather than "enabled" — "enabled" is an excluded JMeter internal. */
        private boolean active = true;
        private Mode mode = Mode.FAST;

        public String getName() { return name; }
        public void setName(String n) { this.name = n; }

        public int getCount() { return count; }
        public void setCount(int c) { this.count = c; }

        public long getBigCount() { return bigCount; }
        public void setBigCount(long b) { this.bigCount = b; }

        public double getRatio() { return ratio; }
        public void setRatio(double r) { this.ratio = r; }

        public float getRate() { return rate; }
        public void setRate(float r) { this.rate = r; }

        public boolean isActive() { return active; }
        public void setActive(boolean a) { this.active = a; }

        public Mode getMode() { return mode; }
        public void setMode(Mode m) { this.mode = m; }
    }

    public enum Mode { FAST, SLOW, BATCH }

    /** Bean that has the internal JMeter properties that should be filtered out. */
    public static class BeanWithInternalProps {

        private String guiclass = "some.Gui";
        private String testclass = "some.Test";
        private String testname = "My Test";
        private boolean enabled = true;
        private String realProp = "value";

        public String getGuiclass()  { return guiclass; }
        public void setGuiclass(String v) { this.guiclass = v; }

        public String getTestclass() { return testclass; }
        public void setTestclass(String v) { this.testclass = v; }

        public String getTestname()  { return testname; }
        public void setTestname(String v) { this.testname = v; }

        public boolean isEnabled()   { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }

        public String getRealProp()  { return realProp; }
        public void setRealProp(String v) { this.realProp = v; }
    }

    /** Bean with a read-only and an unsupported-type property. */
    public static class BeanWithSpecialProps {

        /** Read-only (computed) — should be included with type "integer". */
        public int getComputedValue() { return 42; }

        /** Unsupported type — should be filtered out. */
        @SuppressWarnings("unused")
        private java.util.Date createdAt;
        public java.util.Date getCreatedAt() { return createdAt; }
        public void setCreatedAt(java.util.Date d) { this.createdAt = d; }
    }

    /** Bean with known defaults discoverable via no-arg constructor. */
    public static class BeanWithDefaults {
        private int threads = 10;
        private String url = "http://localhost";

        public int getThreads() { return threads; }
        public void setThreads(int t) { this.threads = t; }

        public String getUrl() { return url; }
        public void setUrl(String u) { this.url = u; }
    }

    // =========================================================================
    // Type mapping tests
    // =========================================================================

    @Nested
    class TypeMapping {

        @Test
        void stringPropertyMapsToStringType() {
            ComponentSchema.Schema schema = extractor.extract(AllTypesBean.class, "test");
            ComponentSchema.PropertySchema prop = findProperty(schema, "name");
            assertEquals("string", prop.type());
        }

        @Test
        void intPropertyMapsToIntegerType() {
            ComponentSchema.Schema schema = extractor.extract(AllTypesBean.class, "test");
            ComponentSchema.PropertySchema prop = findProperty(schema, "count");
            assertEquals("integer", prop.type());
        }

        @Test
        void longPropertyMapsToIntegerType() {
            ComponentSchema.Schema schema = extractor.extract(AllTypesBean.class, "test");
            ComponentSchema.PropertySchema prop = findProperty(schema, "bigCount");
            assertEquals("integer", prop.type());
        }

        @Test
        void doublePropertyMapsToNumberType() {
            ComponentSchema.Schema schema = extractor.extract(AllTypesBean.class, "test");
            ComponentSchema.PropertySchema prop = findProperty(schema, "ratio");
            assertEquals("number", prop.type());
        }

        @Test
        void floatPropertyMapsToNumberType() {
            ComponentSchema.Schema schema = extractor.extract(AllTypesBean.class, "test");
            ComponentSchema.PropertySchema prop = findProperty(schema, "rate");
            assertEquals("number", prop.type());
        }

        @Test
        void booleanPropertyMapsToBooleanType() {
            ComponentSchema.Schema schema = extractor.extract(AllTypesBean.class, "test");
            ComponentSchema.PropertySchema prop = findProperty(schema, "active");
            assertEquals("boolean", prop.type());
        }

        @Test
        void enumPropertyMapsToEnumType() {
            ComponentSchema.Schema schema = extractor.extract(AllTypesBean.class, "test");
            ComponentSchema.PropertySchema prop = findProperty(schema, "mode");
            assertEquals("enum", prop.type());
        }
    }

    // =========================================================================
    // Enum values tests
    // =========================================================================

    @Nested
    class EnumValues {

        @Test
        void enumPropertyHasAllConstantsAsValues() {
            ComponentSchema.Schema schema = extractor.extract(AllTypesBean.class, "test");
            ComponentSchema.PropertySchema prop = findProperty(schema, "mode");
            assertNotNull(prop.enumValues());
            assertTrue(prop.enumValues().contains("FAST"));
            assertTrue(prop.enumValues().contains("SLOW"));
            assertTrue(prop.enumValues().contains("BATCH"));
        }

        @Test
        void enumValuesListHasCorrectSize() {
            ComponentSchema.Schema schema = extractor.extract(AllTypesBean.class, "test");
            ComponentSchema.PropertySchema prop = findProperty(schema, "mode");
            assertEquals(3, prop.enumValues().size());
        }

        @Test
        void nonEnumPropertyHasNullEnumValues() {
            ComponentSchema.Schema schema = extractor.extract(AllTypesBean.class, "test");
            ComponentSchema.PropertySchema prop = findProperty(schema, "name");
            assertNull(prop.enumValues());
        }
    }

    // =========================================================================
    // Filtering tests
    // =========================================================================

    @Nested
    class Filtering {

        @Test
        void guiclassPropertyIsExcluded() {
            ComponentSchema.Schema schema = extractor.extract(BeanWithInternalProps.class, "test");
            assertPropertyAbsent(schema, "guiclass");
        }

        @Test
        void testclassPropertyIsExcluded() {
            ComponentSchema.Schema schema = extractor.extract(BeanWithInternalProps.class, "test");
            assertPropertyAbsent(schema, "testclass");
        }

        @Test
        void testnamePropertyIsExcluded() {
            ComponentSchema.Schema schema = extractor.extract(BeanWithInternalProps.class, "test");
            assertPropertyAbsent(schema, "testname");
        }

        @Test
        void enabledPropertyIsExcluded() {
            ComponentSchema.Schema schema = extractor.extract(BeanWithInternalProps.class, "test");
            assertPropertyAbsent(schema, "enabled");
        }

        @Test
        void nonInternalPropertyIsIncluded() {
            ComponentSchema.Schema schema = extractor.extract(BeanWithInternalProps.class, "test");
            ComponentSchema.PropertySchema prop = findProperty(schema, "realProp");
            assertEquals("string", prop.type());
        }

        @Test
        void unsupportedTypePropertyIsExcluded() {
            ComponentSchema.Schema schema = extractor.extract(BeanWithSpecialProps.class, "test");
            assertPropertyAbsent(schema, "createdAt");
        }

        @Test
        void readOnlyPropertyWithSupportedTypeIsIncluded() {
            ComponentSchema.Schema schema = extractor.extract(BeanWithSpecialProps.class, "test");
            ComponentSchema.PropertySchema prop = findProperty(schema, "computedValue");
            assertEquals("integer", prop.type());
        }
    }

    // =========================================================================
    // Default values tests
    // =========================================================================

    @Nested
    class DefaultValues {

        @Test
        void integerPropertyDefaultIsResolvedFromConstructor() {
            ComponentSchema.Schema schema = extractor.extract(BeanWithDefaults.class, "test");
            ComponentSchema.PropertySchema prop = findProperty(schema, "threads");
            assertEquals(10, prop.defaultValue());
        }

        @Test
        void stringPropertyDefaultIsResolvedFromConstructor() {
            ComponentSchema.Schema schema = extractor.extract(BeanWithDefaults.class, "test");
            ComponentSchema.PropertySchema prop = findProperty(schema, "url");
            assertEquals("http://localhost", prop.defaultValue());
        }
    }

    // =========================================================================
    // Schema metadata tests
    // =========================================================================

    @Nested
    class SchemaMetadata {

        @Test
        void componentNameIsSimpleClassName() {
            ComponentSchema.Schema schema = extractor.extract(AllTypesBean.class, "sampler");
            assertEquals("AllTypesBean", schema.componentName());
        }

        @Test
        void componentCategoryIsPreserved() {
            ComponentSchema.Schema schema = extractor.extract(AllTypesBean.class, "sampler");
            assertEquals("sampler", schema.componentCategory());
        }

        @Test
        void propertiesListIsNotNull() {
            ComponentSchema.Schema schema = extractor.extract(AllTypesBean.class, "test");
            assertNotNull(schema.properties());
        }

        @Test
        void propertiesListIsImmutable() {
            ComponentSchema.Schema schema = extractor.extract(AllTypesBean.class, "test");
            assertThrows(UnsupportedOperationException.class,
                    () -> schema.properties().clear());
        }
    }

    // =========================================================================
    // Null-guard tests
    // =========================================================================

    @Nested
    class NullGuards {

        @Test
        void extractRejectsNullClass() {
            assertThrows(NullPointerException.class,
                    () -> extractor.extract(null, "test"));
        }

        @Test
        void extractRejectsNullCategory() {
            assertThrows(NullPointerException.class,
                    () -> extractor.extract(AllTypesBean.class, null));
        }
    }

    // =========================================================================
    // resolveJsonType unit tests
    // =========================================================================

    @Nested
    class ResolveJsonType {

        @Test
        void resolveStringReturnsString() {
            assertEquals("string", extractor.resolveJsonType(String.class));
        }

        @Test
        void resolveIntPrimitiveReturnsInteger() {
            assertEquals("integer", extractor.resolveJsonType(int.class));
        }

        @Test
        void resolveIntegerBoxedReturnsInteger() {
            assertEquals("integer", extractor.resolveJsonType(Integer.class));
        }

        @Test
        void resolveLongPrimitiveReturnsInteger() {
            assertEquals("integer", extractor.resolveJsonType(long.class));
        }

        @Test
        void resolveDoublePrimitiveReturnsNumber() {
            assertEquals("number", extractor.resolveJsonType(double.class));
        }

        @Test
        void resolveFloatPrimitiveReturnsNumber() {
            assertEquals("number", extractor.resolveJsonType(float.class));
        }

        @Test
        void resolveBooleanPrimitiveReturnsBoolean() {
            assertEquals("boolean", extractor.resolveJsonType(boolean.class));
        }

        @Test
        void resolveBooleanBoxedReturnsBoolean() {
            assertEquals("boolean", extractor.resolveJsonType(Boolean.class));
        }

        @Test
        void resolveEnumReturnsEnum() {
            assertEquals("enum", extractor.resolveJsonType(Mode.class));
        }

        @Test
        void resolveUnsupportedTypeReturnsNull() {
            assertNull(extractor.resolveJsonType(java.util.Date.class));
        }

        @Test
        void resolveNullTypeReturnsNull() {
            assertNull(extractor.resolveJsonType(null));
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private ComponentSchema.PropertySchema findProperty(ComponentSchema.Schema schema, String name) {
        return schema.properties().stream()
                .filter(p -> p.name().equals(name))
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError("Property '" + name + "' not found in schema for "
                                + schema.componentName() + ". Available: "
                                + schema.properties().stream()
                                        .map(ComponentSchema.PropertySchema::name)
                                        .toList()));
    }

    private void assertPropertyAbsent(ComponentSchema.Schema schema, String name) {
        Optional<ComponentSchema.PropertySchema> found = schema.properties().stream()
                .filter(p -> p.name().equals(name))
                .findFirst();
        assertTrue(found.isEmpty(),
                "Property '" + name + "' should have been filtered out but was present");
    }
}
