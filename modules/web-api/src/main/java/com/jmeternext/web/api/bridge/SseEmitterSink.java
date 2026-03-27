package com.jmeternext.web.api.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Thin wrapper around {@link SseEmitter} that serialises event payloads as JSON.
 *
 * <p>A {@link WebUIBridge} holds one {@code SseEmitterSink} per active test run.
 * When the run ends or the client disconnects, the sink is completed/released.
 *
 * <p>All methods are thread-safe — the underlying {@link SseEmitter#send} is
 * synchronised on the emitter's internal lock.
 */
public final class SseEmitterSink implements EventSink {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SseEmitter emitter;

    /**
     * Creates a new sink wrapping the given {@link SseEmitter}.
     *
     * @param emitter the Spring SSE emitter to write events to; must not be null
     */
    public SseEmitterSink(SseEmitter emitter) {
        if (emitter == null) {
            throw new IllegalArgumentException("emitter must not be null");
        }
        this.emitter = emitter;
    }

    /**
     * Sends a named event with the given payload serialised as a JSON object.
     *
     * @param eventName the SSE {@code event:} field value (e.g. {@code "thread.started"})
     * @param payload   map of key/value pairs to serialise as the event data body
     * @throws UncheckedIOException if the underlying SSE send fails (client disconnected)
     */
    public void send(String eventName, Map<String, Object> payload) {
        try {
            String json = MAPPER.writeValueAsString(payload);
            emitter.send(SseEmitter.event().name(eventName).data(json));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to send SSE event '" + eventName + "'", e);
        }
    }

    /**
     * Marks the emitter as complete; no further events will be sent.
     */
    public void complete() {
        emitter.complete();
    }

    /** Returns the underlying {@link SseEmitter}. */
    public SseEmitter getEmitter() {
        return emitter;
    }
}
