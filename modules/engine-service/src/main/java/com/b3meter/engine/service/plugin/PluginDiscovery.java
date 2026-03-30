package com.jmeternext.engine.service.plugin;

import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/**
 * ServiceLoader-based plugin discovery for jmeter-next components.
 *
 * <p>Uses the standard Java {@link ServiceLoader} mechanism. Any JAR on the classpath
 * that contains a {@code META-INF/services} descriptor for the requested service type
 * will have its implementations discovered.
 *
 * <p>This class is framework-free — pure Java, zero Spring or JMeter-internal imports.
 * Implements Constitution Principle I: Engine-First Decoupling.
 *
 * <p>All methods are {@code static}; the class is not instantiable.
 */
public final class PluginDiscovery {

    private PluginDiscovery() {
        // utility class — not instantiable
    }

    /**
     * Discovers all implementations of {@code serviceType} via the thread-context
     * {@link ClassLoader}.
     *
     * <p>Each provider listed in
     * {@code META-INF/services/<serviceType.getName()>} is instantiated and returned.
     * Providers are returned in the order they appear in the service file.
     *
     * @param <T>         the service type
     * @param serviceType the interface or abstract class to discover; must not be {@code null}
     * @return unmodifiable list of discovered instances; never {@code null}
     * @throws NullPointerException if {@code serviceType} is {@code null}
     */
    public static <T> List<T> discover(Class<T> serviceType) {
        Objects.requireNonNull(serviceType, "serviceType must not be null");
        return ServiceLoader.load(serviceType)
                .stream()
                .map(ServiceLoader.Provider::get)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Discovers all implementations of {@code serviceType} from a specific
     * {@link ClassLoader}.
     *
     * <p>Use this overload when loading components from plugin JARs that are not
     * on the application class path (e.g., dynamically loaded extension JARs).
     *
     * @param <T>         the service type
     * @param serviceType the interface or abstract class to discover; must not be {@code null}
     * @param classLoader the class loader to use for discovery; must not be {@code null}
     * @return unmodifiable list of discovered instances; never {@code null}
     * @throws NullPointerException if {@code serviceType} or {@code classLoader} is {@code null}
     */
    public static <T> List<T> discover(Class<T> serviceType, ClassLoader classLoader) {
        Objects.requireNonNull(serviceType,  "serviceType must not be null");
        Objects.requireNonNull(classLoader,  "classLoader must not be null");
        return ServiceLoader.load(serviceType, classLoader)
                .stream()
                .map(ServiceLoader.Provider::get)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Lists the fully-qualified class names of all providers for {@code serviceType}
     * without instantiating them.
     *
     * <p>This is useful for diagnostics and for eager registration of component names
     * before the actual instances are needed.
     *
     * @param <T>         the service type
     * @param serviceType the interface or abstract class to inspect; must not be {@code null}
     * @return unmodifiable list of provider type names; never {@code null}
     * @throws NullPointerException if {@code serviceType} is {@code null}
     */
    public static <T> List<String> discoverTypeNames(Class<T> serviceType) {
        Objects.requireNonNull(serviceType, "serviceType must not be null");
        return ServiceLoader.load(serviceType)
                .stream()
                .map(p -> p.type().getName())
                .collect(Collectors.toUnmodifiableList());
    }
}
