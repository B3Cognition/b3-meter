package com.jmeternext.engine.service.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Engine-safe utility methods — pure Java, ZERO Swing/AWT imports.
 *
 * <p>This class is the engine-service equivalent of the subset of legacy
 * {@code JMeterUtils} that does not require a GUI. It replaces the parts of
 * {@code JMeterUtils} that are safe to call from worker-node containers and
 * headless CI pipelines.
 *
 * <p>All methods are {@code static}; the class is not instantiable.
 *
 * <p>Implements Constitution Principle I: Engine-First Decoupling — this class
 * has zero {@code javax.swing.*} or {@code java.awt.*} imports.
 */
public final class EngineUtils {

    /** Version of the jmeter-next engine; override via system property {@code jmeter.version}. */
    private static final String DEFAULT_VERSION = "0.1.0-SNAPSHOT";

    /**
     * System property key that can override {@link #getJMeterVersion()}.
     * Allows the build system to inject the release version at test time.
     */
    private static final String VERSION_PROPERTY = "jmeter.version";

    /** Pattern for {@code ${variableName}} substitution in {@link #replaceVariables}. */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    /**
     * Thread-local locale so that each thread can operate in its own locale
     * without global state contention. Initialised to the JVM default locale
     * for each new thread (matches legacy JMeterUtils.getLocale() behaviour).
     */
    private static final ThreadLocal<Locale> LOCALE =
            ThreadLocal.withInitial(Locale::getDefault);

    private EngineUtils() {
        // utility class — not instantiable
    }

    // -------------------------------------------------------------------------
    // Property access
    // -------------------------------------------------------------------------

    /**
     * Returns the value of the system property with the given key, or {@code null}
     * if the property is not set.
     *
     * @param key property key; must not be {@code null}
     * @return property value, or {@code null}
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public static String getProperty(String key) {
        Objects.requireNonNull(key, "key must not be null");
        return System.getProperty(key);
    }

    /**
     * Returns the value of the system property with the given key, or
     * {@code defaultValue} if the property is not set.
     *
     * @param key          property key; must not be {@code null}
     * @param defaultValue value to return when the property is absent
     * @return property value, or {@code defaultValue}
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public static String getProperty(String key, String defaultValue) {
        Objects.requireNonNull(key, "key must not be null");
        return System.getProperty(key, defaultValue);
    }

    // -------------------------------------------------------------------------
    // Properties file loading
    // -------------------------------------------------------------------------

    /**
     * Loads a {@link Properties} file from the given path.
     *
     * @param path path to the {@code .properties} file; must not be {@code null}
     * @return populated {@link Properties} instance
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws IOException          if the file cannot be read
     */
    public static Properties loadProperties(Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    // -------------------------------------------------------------------------
    // Version
    // -------------------------------------------------------------------------

    /**
     * Returns the jmeter-next engine version.
     *
     * <p>The value is read from the {@code jmeter.version} system property if set;
     * otherwise the build-time default ({@value DEFAULT_VERSION}) is returned.
     *
     * @return non-null, non-blank version string
     */
    public static String getJMeterVersion() {
        return System.getProperty(VERSION_PROPERTY, DEFAULT_VERSION);
    }

    // -------------------------------------------------------------------------
    // Classpath resource loading
    // -------------------------------------------------------------------------

    /**
     * Reads a classpath resource as a UTF-8 string.
     *
     * @param resource resource path relative to the classpath root (e.g. {@code "jmeter.properties"});
     *                 must not be {@code null}
     * @return full contents of the resource as a string
     * @throws NullPointerException if {@code resource} is {@code null}
     * @throws IOException          if the resource does not exist or cannot be read
     */
    public static String getResourceFileAsText(String resource) throws IOException {
        Objects.requireNonNull(resource, "resource must not be null");
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(resource);
        if (stream == null) {
            throw new IOException("Classpath resource not found: " + resource);
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
            return sb.toString();
        }
    }

    // -------------------------------------------------------------------------
    // Variable substitution
    // -------------------------------------------------------------------------

    /**
     * Replaces {@code ${variableName}} placeholders in {@code template} with
     * values from {@code variables}.
     *
     * <p>Placeholders that have no corresponding key in {@code variables} are
     * left unchanged in the output.
     *
     * @param template  the string containing zero or more {@code ${…}} placeholders;
     *                  must not be {@code null}
     * @param variables map of variable names to their replacement values;
     *                  must not be {@code null}
     * @return template with all known placeholders replaced
     * @throws NullPointerException if {@code template} or {@code variables} is {@code null}
     */
    public static String replaceVariables(String template, Map<String, String> variables) {
        Objects.requireNonNull(template, "template must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = variables.get(key);
            if (value != null) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(value));
            } else {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    // -------------------------------------------------------------------------
    // Null-safe string splitting
    // -------------------------------------------------------------------------

    /**
     * Splits {@code value} on {@code separator} and returns the tokens as an
     * unmodifiable list.
     *
     * <p>Returns an empty list when {@code value} is {@code null} or blank,
     * so callers never need to guard against {@code null} return values.
     *
     * @param value     the string to split; {@code null} is treated as empty
     * @param separator the delimiter pattern (passed to {@link String#split});
     *                  must not be {@code null}
     * @return unmodifiable list of tokens; never {@code null}
     * @throws NullPointerException if {@code separator} is {@code null}
     */
    public static List<String> split(String value, String separator) {
        Objects.requireNonNull(separator, "separator must not be null");
        if (value == null || value.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(Arrays.asList(value.split(separator)));
    }

    // -------------------------------------------------------------------------
    // Locale management
    // -------------------------------------------------------------------------

    /**
     * Returns the current thread's locale.
     *
     * <p>Each thread starts with the JVM default locale. Callers may override
     * it with {@link #setLocale(Locale)} without affecting other threads.
     *
     * @return current thread's locale; never {@code null}
     */
    public static Locale getLocale() {
        return LOCALE.get();
    }

    /**
     * Sets the current thread's locale.
     *
     * <p>This change is thread-local: it does not affect other threads.
     *
     * @param locale the locale to use; must not be {@code null}
     * @throws NullPointerException if {@code locale} is {@code null}
     */
    public static void setLocale(Locale locale) {
        Objects.requireNonNull(locale, "locale must not be null");
        LOCALE.set(locale);
    }
}
