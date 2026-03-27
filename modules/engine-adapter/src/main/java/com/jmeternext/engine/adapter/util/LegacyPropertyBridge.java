package com.jmeternext.engine.adapter.util;

import com.jmeternext.engine.service.util.EngineUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Bridges legacy JMeter property names to the jmeter-next configuration model.
 *
 * <p>Legacy JMeter used {@code jmeter.properties} with its own property-key
 * conventions. This bridge reads that format and maps the known legacy keys to
 * the new {@code com.jmeternext.*} key space, providing backward-compatible
 * property access while the migration progresses.
 *
 * <p>Usage pattern:
 * <pre>{@code
 *   LegacyPropertyBridge bridge = LegacyPropertyBridge.fromFile(Paths.get("jmeter.properties"));
 *   String value = bridge.get("jmeter.save.saveservice.output_format");  // legacy key
 *   String newKey = bridge.newKeyFor("jmeter.save.saveservice.output_format");
 * }</pre>
 *
 * <p>This class is part of the engine-adapter anti-corruption layer (T005).
 * It does not introduce any Swing/AWT or Spring dependencies.
 */
public final class LegacyPropertyBridge {

    /**
     * Known mappings from legacy JMeter property keys to jmeter-next config keys.
     *
     * <p>Keys listed here are the most common properties referenced by legacy code.
     * Unknown keys are passed through unchanged.
     */
    private static final Map<String, String> LEGACY_TO_NEW = buildLegacyMap();

    /** The raw properties loaded from the jmeter.properties file. */
    private final Properties properties;

    private LegacyPropertyBridge(Properties properties) {
        this.properties = properties;
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Creates a bridge backed by a {@code .properties} file on disk.
     *
     * @param path path to the legacy {@code jmeter.properties} file; must not be {@code null}
     * @return a populated bridge instance
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws IOException          if the file cannot be read
     */
    public static LegacyPropertyBridge fromFile(Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        Properties props = EngineUtils.loadProperties(path);
        return new LegacyPropertyBridge(props);
    }

    /**
     * Creates a bridge backed by an existing {@link Properties} instance.
     *
     * <p>Useful for testing and for callers that have already loaded properties
     * via another mechanism.
     *
     * @param properties the properties to wrap; must not be {@code null}
     * @return a bridge wrapping the supplied properties
     * @throws NullPointerException if {@code properties} is {@code null}
     */
    public static LegacyPropertyBridge fromProperties(Properties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        Properties copy = new Properties();
        copy.putAll(properties);
        return new LegacyPropertyBridge(copy);
    }

    // -------------------------------------------------------------------------
    // Property access
    // -------------------------------------------------------------------------

    /**
     * Returns the property value for the given key (legacy or new key accepted).
     *
     * <p>Lookup order:
     * <ol>
     *   <li>Direct key in the loaded properties</li>
     *   <li>If the key is a legacy key, look up via the mapped new key</li>
     *   <li>{@code null} if not found</li>
     * </ol>
     *
     * @param key property key (legacy or new format); must not be {@code null}
     * @return property value, or {@code null} if not found
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public String get(String key) {
        Objects.requireNonNull(key, "key must not be null");
        String direct = properties.getProperty(key);
        if (direct != null) {
            return direct;
        }
        String mappedKey = LEGACY_TO_NEW.get(key);
        if (mappedKey != null) {
            return properties.getProperty(mappedKey);
        }
        return null;
    }

    /**
     * Returns the property value for the given key, or {@code defaultValue} if
     * the property is not found.
     *
     * @param key          property key (legacy or new format); must not be {@code null}
     * @param defaultValue value to return when the key is absent
     * @return property value, or {@code defaultValue}
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public String get(String key, String defaultValue) {
        Objects.requireNonNull(key, "key must not be null");
        String value = get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Returns the new jmeter-next configuration key for the given legacy key,
     * or the key itself if no mapping is defined.
     *
     * @param legacyKey legacy JMeter property key; must not be {@code null}
     * @return the mapped new key, or {@code legacyKey} if unmapped
     * @throws NullPointerException if {@code legacyKey} is {@code null}
     */
    public String newKeyFor(String legacyKey) {
        Objects.requireNonNull(legacyKey, "legacyKey must not be null");
        return LEGACY_TO_NEW.getOrDefault(legacyKey, legacyKey);
    }

    /**
     * Returns a snapshot of all raw properties held by this bridge.
     *
     * <p>The returned object is a copy; mutations do not affect this bridge.
     *
     * @return copy of the underlying properties
     */
    public Properties asProperties() {
        Properties copy = new Properties();
        copy.putAll(properties);
        return copy;
    }

    // -------------------------------------------------------------------------
    // Legacy key mapping table
    // -------------------------------------------------------------------------

    private static Map<String, String> buildLegacyMap() {
        Map<String, String> map = new HashMap<>();

        // Sampler defaults
        map.put("jmeter.save.saveservice.output_format",
                "com.jmeternext.sampler.output.format");
        map.put("jmeter.save.saveservice.response_code",
                "com.jmeternext.sampler.save.response_code");
        map.put("jmeter.save.saveservice.successful",
                "com.jmeternext.sampler.save.success");
        map.put("jmeter.save.saveservice.thread_name",
                "com.jmeternext.sampler.save.thread_name");
        map.put("jmeter.save.saveservice.time",
                "com.jmeternext.sampler.save.elapsed_ms");
        map.put("jmeter.save.saveservice.label",
                "com.jmeternext.sampler.save.label");
        map.put("jmeter.save.saveservice.bytes",
                "com.jmeternext.sampler.save.bytes");
        map.put("jmeter.save.saveservice.latency",
                "com.jmeternext.sampler.save.latency_ms");

        // Thread group
        map.put("jmeter.threadgroup.on_sample_error",
                "com.jmeternext.thread.on_sample_error");

        // HTTP sampler
        map.put("HTTPSampler.connect_timeout",
                "com.jmeternext.http.connect_timeout_ms");
        map.put("HTTPSampler.response_timeout",
                "com.jmeternext.http.response_timeout_ms");

        // Proxy
        map.put("jmeter.proxy.port",
                "com.jmeternext.proxy.port");
        map.put("jmeter.proxy.ssl.cert",
                "com.jmeternext.proxy.ssl.cert_path");

        // Reporting / dashboard
        map.put("jmeter.reportgenerator.report_title",
                "com.jmeternext.report.title");
        map.put("jmeter.reportgenerator.overall_granularity",
                "com.jmeternext.report.granularity_ms");

        return Collections.unmodifiableMap(map);
    }
}
