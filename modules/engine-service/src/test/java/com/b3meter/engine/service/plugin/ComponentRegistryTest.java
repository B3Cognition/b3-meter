package com.jmeternext.engine.service.plugin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ComponentRegistry}.
 *
 * <p>Relies on the {@code META-INF/services/com.jmeternext.engine.service.plugin.JMeterComponent}
 * file in test resources, which declares {@link StubHttpSamplerComponent} and
 * {@link StubThreadGroupComponent}.
 */
class ComponentRegistryTest {

    private ComponentRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ComponentRegistry();
    }

    // -------------------------------------------------------------------------
    // Initial state after construction
    // -------------------------------------------------------------------------

    @Test
    void allComponentNamesIsNotNullAfterConstruction() {
        assertNotNull(registry.allComponentNames());
    }

    @Test
    void registryContainsBothTestComponentsAfterConstruction() {
        Collection<String> names = registry.allComponentNames();
        assertTrue(names.contains(StubHttpSamplerComponent.NAME),
                "HTTPSampler must be registered on construction");
        assertTrue(names.contains(StubThreadGroupComponent.NAME),
                "ThreadGroup must be registered on construction");
    }

    @Test
    void registryContainsExactlyTwoComponentsAfterConstruction() {
        assertEquals(2, registry.allComponentNames().size(),
                "Exactly two test components should be registered");
    }

    // -------------------------------------------------------------------------
    // getComponentClass — positive cases
    // -------------------------------------------------------------------------

    @Test
    void getComponentClassReturnsHttpSamplerClass() {
        Optional<Class<?>> result = registry.getComponentClass(StubHttpSamplerComponent.NAME);
        assertTrue(result.isPresent(), "HTTPSampler must be present in registry");
        assertEquals(StubHttpSamplerComponent.class, result.get());
    }

    @Test
    void getComponentClassReturnsThreadGroupClass() {
        Optional<Class<?>> result = registry.getComponentClass(StubThreadGroupComponent.NAME);
        assertTrue(result.isPresent(), "ThreadGroup must be present in registry");
        assertEquals(StubThreadGroupComponent.class, result.get());
    }

    // -------------------------------------------------------------------------
    // getComponentClass — negative cases
    // -------------------------------------------------------------------------

    @Test
    void getComponentClassReturnsEmptyForUnknownName() {
        Optional<Class<?>> result = registry.getComponentClass("NonExistentSampler");
        assertFalse(result.isPresent(),
                "Unknown component name must yield an empty Optional");
    }

    @Test
    void getComponentClassWithNullNameThrows() {
        assertThrows(NullPointerException.class,
                () -> registry.getComponentClass(null));
    }

    // -------------------------------------------------------------------------
    // allComponentNames — immutability
    // -------------------------------------------------------------------------

    @Test
    void allComponentNamesCollectionIsUnmodifiable() {
        Collection<String> names = registry.allComponentNames();
        assertThrows(UnsupportedOperationException.class,
                () -> names.add("should.not.work"),
                "allComponentNames() must return an unmodifiable collection");
    }

    // -------------------------------------------------------------------------
    // refresh — re-scan picks up the same components
    // -------------------------------------------------------------------------

    @Test
    void refreshReturnsRegistryWithSameComponentsOnSubsequentCall() {
        // After a refresh the registry must still expose the same test components
        registry.refresh();
        Collection<String> names = registry.allComponentNames();
        assertTrue(names.contains(StubHttpSamplerComponent.NAME),
                "HTTPSampler must still be present after refresh");
        assertTrue(names.contains(StubThreadGroupComponent.NAME),
                "ThreadGroup must still be present after refresh");
    }

    @Test
    void refreshDoesNotLeaveRegistryEmpty() {
        registry.refresh();
        assertFalse(registry.allComponentNames().isEmpty(),
                "Registry must not be empty after refresh when test providers are on classpath");
    }

    @Test
    void refreshPreservesLookupBehaviour() {
        registry.refresh();
        Optional<Class<?>> result = registry.getComponentClass(StubHttpSamplerComponent.NAME);
        assertTrue(result.isPresent(),
                "getComponentClass must still resolve after refresh");
    }

    @Test
    void multipleRefreshesAreIdempotent() {
        registry.refresh();
        registry.refresh();
        registry.refresh();
        assertEquals(2, registry.allComponentNames().size(),
                "Three consecutive refreshes must leave exactly two components");
    }
}
