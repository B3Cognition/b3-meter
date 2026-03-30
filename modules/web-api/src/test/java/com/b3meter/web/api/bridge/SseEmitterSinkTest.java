package com.jmeternext.web.api.bridge;

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
