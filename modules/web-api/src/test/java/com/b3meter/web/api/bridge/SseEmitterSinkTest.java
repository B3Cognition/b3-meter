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

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SseEmitterSink}.
 */
class SseEmitterSinkTest {

    @Test
    void constructorRejectsNullEmitter() {
        assertThrows(IllegalArgumentException.class, () -> new SseEmitterSink(null));
    }

    @Test
    void getEmitterReturnsSameInstance() {
        SseEmitter emitter = new SseEmitter();
        SseEmitterSink sink = new SseEmitterSink(emitter);
        assertSame(emitter, sink.getEmitter());
    }

    @Test
    void completeDoesNotThrow() {
        SseEmitter emitter = new SseEmitter();
        SseEmitterSink sink = new SseEmitterSink(emitter);
        assertDoesNotThrow(sink::complete);
    }

    @Test
    void sendToCompletedEmitterThrowsUncheckedIOException() {
        // Once complete() is called, any subsequent send() raises IllegalStateException
        // which SseEmitter wraps — our sink surfaces it as UncheckedIOException.
        SseEmitter emitter = new SseEmitter();
        SseEmitterSink sink = new SseEmitterSink(emitter);
        sink.complete();
        assertThrows(Exception.class, () -> sink.send("test.event", Map.of("key", "value")));
    }
}
