package com.jmeternext.engine.service.plugin;

/**
 * Test-only stub simulating an HTTP Sampler component for ServiceLoader discovery tests.
 *
 * <p>Declared in
 * {@code META-INF/services/com.jmeternext.engine.service.plugin.JMeterComponent}
 * so it is discovered by {@link PluginDiscovery} during testing.
 */
public final class StubHttpSamplerComponent implements JMeterComponent {

    /** Component name used as the lookup key in {@link ComponentRegistry}. */
    public static final String NAME     = "HTTPSampler";

    /** Category string returned by this stub. */
    public static final String CATEGORY = "sampler";

    @Override
    public String getComponentName() {
        return NAME;
    }

    @Override
    public String getComponentCategory() {
        return CATEGORY;
    }
}
