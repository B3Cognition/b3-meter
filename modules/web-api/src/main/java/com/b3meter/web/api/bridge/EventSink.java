package com.jmeternext.web.api.bridge;

import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Abstraction over a real-time event delivery channel.
 *
 * <p>Implemented by {@link SseEmitterSink} for production use (Server-Sent Events),
 * and by in-memory recording stubs in unit tests.
 *
 * <p>All methods must be safe to call from any thread.
 */
public interface EventSink {

    /**
     * Sends a named event with the given payload.
     *
     * @param eventName the event type identifier (e.g. {@code "thread.started"})
     * @param payload   key/value pairs to include in the event body
     * @throws UncheckedIOException if the underlying transport fails (e.g. client disconnected)
     */
    void send(String eventName, Map<String, Object> payload);

    /**
     * Signals that no further events will be sent on this sink.
     */
    void complete();
}
