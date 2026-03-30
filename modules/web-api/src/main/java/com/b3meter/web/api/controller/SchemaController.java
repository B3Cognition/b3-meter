package com.jmeternext.web.api.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST controller that exposes component JSON schemas derived from Java BeanInfo
 * metadata, enabling automatic form generation in the React frontend.
 *
 * <p>This is the server-side half of the MAVERICK Alternative 3 implementation
 * (T036): instead of hand-coding a UI panel for every JMeter element, the frontend
 * fetches a schema and renders a dynamic form.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET /api/v1/schemas} — list all registered component schemas</li>
 *   <li>{@code GET /api/v1/schemas/{componentName}} — single schema by name</li>
 * </ul>
 *
 * <h2>Caching strategy</h2>
 * Schemas are built once at startup in {@link #init()} and stored in an
 * immutable map. The schemas are derived from static BeanInfo metadata and
 * therefore never change at runtime.
 *
 * <h2>Module boundary</h2>
 * {@code web-api} must not depend on {@code engine-adapter} (enforced by
 * ArchUnit). Accordingly, the schema DTOs are defined as inner records here
 * rather than imported from {@code engine-adapter}'s schema package.
 * The hand-coded registry below mirrors the property shapes that
 * {@code BeanInfoSchemaExtractor} would produce — it is the bridge between
 * the extractor (which runs in {@code engine-adapter}) and the REST layer.
 */
@RestController
@RequestMapping("/api/v1/schemas")
public class SchemaController {

    // -------------------------------------------------------------------------
    // DTO types (inline — engine-adapter boundary must not be crossed here)
    // -------------------------------------------------------------------------

    /**
     * JSON-serialisable description of a single component property.
     *
     * @param name         property key as used in JMX / node properties map
     * @param displayName  human-readable label for the UI
     * @param type         JSON Schema type: "string" | "integer" | "number" |
     *                     "boolean" | "enum"
     * @param defaultValue optional default value; {@code null} when none
     * @param required     whether the field must be present
     * @param enumValues   allowed values for "enum" type; {@code null} otherwise
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PropertySchemaDto(
            String name,
            String displayName,
            String type,
            Object defaultValue,
            boolean required,
            List<String> enumValues
    ) {}

    /**
     * JSON-serialisable component schema returned by the API.
     *
     * @param componentName     simple class name, e.g. "ThreadGroup"
     * @param componentCategory informal category, e.g. "thread", "sampler"
     * @param properties        ordered list of property schemas
     */
    public record ComponentSchemaDto(
            String componentName,
            String componentCategory,
            List<PropertySchemaDto> properties
    ) {}

    // -------------------------------------------------------------------------
    // Schema registry (built at startup)
    // -------------------------------------------------------------------------

    /**
     * Component name → schema, populated once at startup.
     * Declared volatile to prevent partial publication when the reference is
     * replaced; in practice it is written once and never again.
     */
    private volatile Map<String, ComponentSchemaDto> schemaCache;

    /**
     * Builds the schema cache from the known component catalogue.
     *
     * <p>This method is called by Spring once all beans are initialised
     * ({@link PostConstruct}). Adding a new component to {@code buildRegistry()}
     * is the only change required to expose it through the API.
     */
    @PostConstruct
    void init() {
        schemaCache = Collections.unmodifiableMap(buildRegistry());
    }

    // -------------------------------------------------------------------------
    // Endpoints
    // -------------------------------------------------------------------------

    /**
     * Returns all registered component schemas.
     *
     * @return list of schemas (never {@code null}, possibly empty)
     */
    @GetMapping
    public List<ComponentSchemaDto> listAll() {
        return new ArrayList<>(schemaCache.values());
    }

    /**
     * Returns the schema for a single component by its name.
     *
     * @param componentName simple class name, e.g. {@code "ThreadGroup"}
     * @return 200 with schema, or 404 when the component is not registered
     */
    @GetMapping("/{componentName}")
    public ResponseEntity<ComponentSchemaDto> getByName(
            @PathVariable String componentName) {
        return Optional.ofNullable(schemaCache.get(componentName))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // -------------------------------------------------------------------------
    // Registry builder
    // -------------------------------------------------------------------------

    /**
     * Builds the map of all known component schemas.
     *
     * <p>Schemas here reflect the same property shapes that
     * {@code BeanInfoSchemaExtractor} would derive from the JMeter classes at
     * runtime. Keeping them here (rather than calling the extractor directly)
     * preserves the module boundary and avoids a JMeter classpath dependency in
     * {@code web-api}.
     *
     * <p>To add a new component: create a {@link ComponentSchemaDto} with the
     * appropriate properties and add it to the returned map.
     */
    private Map<String, ComponentSchemaDto> buildRegistry() {
        Map<String, ComponentSchemaDto> registry = new ConcurrentHashMap<>();

        // ---- ThreadGroup -------------------------------------------------------
        registry.put("ThreadGroup", new ComponentSchemaDto(
                "ThreadGroup",
                "thread",
                List.of(
                        new PropertySchemaDto(
                                "num_threads", "Number of Threads",
                                "integer", 1, true, null),
                        new PropertySchemaDto(
                                "ramp_time", "Ramp-Up Period (seconds)",
                                "integer", 1, true, null),
                        new PropertySchemaDto(
                                "loops", "Loop Count",
                                "integer", 1, true, null),
                        new PropertySchemaDto(
                                "duration", "Duration (seconds)",
                                "integer", 0, false, null),
                        new PropertySchemaDto(
                                "on_sample_error", "Action After Sampler Error",
                                "enum", "CONTINUE", false,
                                List.of("CONTINUE", "START_NEXT_THREAD_LOOP",
                                        "START_NEXT_ITERATION", "STOP_THREAD",
                                        "STOP_TEST", "STOP_TEST_NOW"))
                )
        ));

        // ---- HTTPSampler -------------------------------------------------------
        registry.put("HTTPSampler", new ComponentSchemaDto(
                "HTTPSampler",
                "sampler",
                List.of(
                        new PropertySchemaDto(
                                "domain", "Server Name or IP",
                                "string", "", true, null),
                        new PropertySchemaDto(
                                "port", "Port Number",
                                "integer", 80, true, null),
                        new PropertySchemaDto(
                                "protocol", "Protocol",
                                "enum", "http", false,
                                List.of("http", "https")),
                        new PropertySchemaDto(
                                "path", "Path",
                                "string", "/", false, null),
                        new PropertySchemaDto(
                                "method", "HTTP Method",
                                "enum", "GET", true,
                                List.of("GET", "POST", "PUT", "PATCH",
                                        "DELETE", "HEAD", "OPTIONS")),
                        new PropertySchemaDto(
                                "connect_timeout", "Connect Timeout (ms)",
                                "integer", 0, false, null),
                        new PropertySchemaDto(
                                "response_timeout", "Response Timeout (ms)",
                                "integer", 0, false, null)
                )
        ));

        // ---- LoopController ---------------------------------------------------
        registry.put("LoopController", new ComponentSchemaDto(
                "LoopController",
                "controller",
                List.of(
                        new PropertySchemaDto(
                                "loops", "Loop Count",
                                "integer", 1, true, null),
                        new PropertySchemaDto(
                                "continue_forever", "Continue Forever",
                                "boolean", false, false, null)
                )
        ));

        // ---- ConstantTimer ----------------------------------------------------
        registry.put("ConstantTimer", new ComponentSchemaDto(
                "ConstantTimer",
                "timer",
                List.of(
                        new PropertySchemaDto(
                                "delay", "Thread Delay (ms)",
                                "integer", 300, true, null)
                )
        ));

        // ---- ResponseAssertion ------------------------------------------------
        registry.put("ResponseAssertion", new ComponentSchemaDto(
                "ResponseAssertion",
                "assertion",
                List.of(
                        new PropertySchemaDto(
                                "test_field", "Field to Test",
                                "enum", "RESPONSE_DATA", false,
                                List.of("RESPONSE_DATA", "RESPONSE_CODE",
                                        "RESPONSE_MESSAGE", "RESPONSE_HEADERS",
                                        "REQUEST_HEADERS", "URL", "DOCUMENT")),
                        new PropertySchemaDto(
                                "assume_success", "Ignore Status",
                                "boolean", false, false, null),
                        new PropertySchemaDto(
                                "custom_message", "Custom Failure Message",
                                "string", null, false, null)
                )
        ));

        return registry;
    }
}
