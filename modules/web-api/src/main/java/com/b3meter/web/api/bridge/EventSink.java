/*
 * Copyright 2024-2026 b3meter Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.b3meter.web.api.bridge;

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
