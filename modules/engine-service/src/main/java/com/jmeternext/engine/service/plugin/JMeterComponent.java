package com.jmeternext.engine.service.plugin;

/**
 * Marker interface that all jmeter-next components implement.
 *
 * <p>This interface serves as the {@link java.util.ServiceLoader} service type for
 * plugin discovery. Any component that should be discovered at runtime must:
 * <ol>
 *   <li>Implement this interface</li>
 *   <li>Declare itself in {@code META-INF/services/com.jmeternext.engine.service.plugin.JMeterComponent}</li>
 * </ol>
 *
 * <p>This interface is framework-free — no Spring, no JMeter-internal imports.
 * Implements Constitution Principle I: Engine-First Decoupling.
 */
public interface JMeterComponent {

    /**
     * Returns the canonical name of this component as used in test plan serialisation
     * (e.g. {@code "HTTPSampler"}, {@code "ThreadGroup"}, {@code "ResponseAssertion"}).
     *
     * <p>Names must be unique within a category. {@link ComponentRegistry} uses this
     * value as the lookup key.
     *
     * @return non-null, non-blank component name
     */
    String getComponentName();

    /**
     * Returns the functional category of this component.
     *
     * <p>Well-known categories: {@code sampler}, {@code controller}, {@code assertion},
     * {@code timer}, {@code listener}, {@code config}, {@code pre-processor},
     * {@code post-processor}.
     *
     * @return non-null, non-blank category string
     */
    String getComponentCategory();
}
