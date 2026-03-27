package com.jmeternext.engine.service.plugin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PluginDiscovery}.
 *
 * <p>Relies on the {@code META-INF/services/com.jmeternext.engine.service.plugin.JMeterComponent}
 * file in test resources, which declares {@link StubHttpSamplerComponent} and
 * {@link StubThreadGroupComponent}.
 */
class PluginDiscoveryTest {

    // -------------------------------------------------------------------------
    // discover(Class)
    // -------------------------------------------------------------------------

    @Test
    void discoverReturnsNonNullList() {
        List<JMeterComponent> components = PluginDiscovery.discover(JMeterComponent.class);
        assertNotNull(components);
    }

    @Test
    void discoverFindsAllTestComponents() {
        List<JMeterComponent> components = PluginDiscovery.discover(JMeterComponent.class);
        assertEquals(2, components.size(),
                "Expected exactly two test components (HTTPSampler + ThreadGroup)");
    }

    @Test
    void discoverContainsHttpSamplerStub() {
        List<JMeterComponent> components = PluginDiscovery.discover(JMeterComponent.class);
        boolean found = components.stream()
                .anyMatch(c -> StubHttpSamplerComponent.NAME.equals(c.getComponentName()));
        assertTrue(found, "HTTPSampler stub must be discoverable via ServiceLoader");
    }

    @Test
    void discoverContainsThreadGroupStub() {
        List<JMeterComponent> components = PluginDiscovery.discover(JMeterComponent.class);
        boolean found = components.stream()
                .anyMatch(c -> StubThreadGroupComponent.NAME.equals(c.getComponentName()));
        assertTrue(found, "ThreadGroup stub must be discoverable via ServiceLoader");
    }

    @Test
    void discoverReturnsUnmodifiableList() {
        List<JMeterComponent> components = PluginDiscovery.discover(JMeterComponent.class);
        assertThrows(UnsupportedOperationException.class,
                () -> components.add(new StubHttpSamplerComponent()),
                "discover() must return an unmodifiable list");
    }

    @Test
    void discoverWithNullServiceTypeThrows() {
        assertThrows(NullPointerException.class,
                () -> PluginDiscovery.discover(null));
    }

    // -------------------------------------------------------------------------
    // discover(Class, ClassLoader)
    // -------------------------------------------------------------------------

    @Test
    void discoverWithExplicitClassLoaderFindsTestComponents() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        List<JMeterComponent> components = PluginDiscovery.discover(JMeterComponent.class, cl);
        assertFalse(components.isEmpty(),
                "Explicit classloader overload must find test components");
    }

    @Test
    void discoverWithNullClassLoaderThrows() {
        assertThrows(NullPointerException.class,
                () -> PluginDiscovery.discover(JMeterComponent.class, null));
    }

    @Test
    void discoverWithNullServiceTypeAndClassLoaderThrows() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        assertThrows(NullPointerException.class,
                () -> PluginDiscovery.discover(null, cl));
    }

    // -------------------------------------------------------------------------
    // discoverTypeNames(Class)
    // -------------------------------------------------------------------------

    @Test
    void discoverTypeNamesReturnsNonNullList() {
        List<String> names = PluginDiscovery.discoverTypeNames(JMeterComponent.class);
        assertNotNull(names);
    }

    @Test
    void discoverTypeNamesContainsBothStubClassNames() {
        List<String> names = PluginDiscovery.discoverTypeNames(JMeterComponent.class);
        assertTrue(names.contains(StubHttpSamplerComponent.class.getName()),
                "Type names must include StubHttpSamplerComponent");
        assertTrue(names.contains(StubThreadGroupComponent.class.getName()),
                "Type names must include StubThreadGroupComponent");
    }

    @Test
    void discoverTypeNamesDoesNotInstantiateComponents() {
        // This test verifies the method completes without throwing — the intent is
        // that providers are NOT instantiated (ServiceLoader.Provider::type is lazy).
        // We can only confirm it returns names, not instances.
        List<String> names = PluginDiscovery.discoverTypeNames(JMeterComponent.class);
        assertEquals(2, names.size());
        names.forEach(n -> assertTrue(n.startsWith("com.jmeternext"),
                "All discovered type names must be in the jmeternext namespace"));
    }

    @Test
    void discoverTypeNamesReturnsUnmodifiableList() {
        List<String> names = PluginDiscovery.discoverTypeNames(JMeterComponent.class);
        assertThrows(UnsupportedOperationException.class,
                () -> names.add("should.not.work"),
                "discoverTypeNames() must return an unmodifiable list");
    }

    @Test
    void discoverTypeNamesWithNullServiceTypeThrows() {
        assertThrows(NullPointerException.class,
                () -> PluginDiscovery.discoverTypeNames(null));
    }
}
