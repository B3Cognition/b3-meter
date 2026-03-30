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
package com.b3meter.engine.adapter;

import com.b3meter.engine.service.TestRunContext;
import com.b3meter.engine.service.UIBridge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link NoOpUIBridge}.
 *
 * <p>Covers all methods introduced by the GuiPackage audit (T004) in addition
 * to the original three lifecycle methods from T001.
 */
class NoOpUIBridgeTest {

    private static final TestRunContext CTX = TestRunContext.builder()
            .runId("test-run")
            .planPath("/plans/test.jmx")
            .uiBridge(NoOpUIBridge.INSTANCE)
            .build();

    @AfterEach
    void clearKeystoreProperty() {
        System.clearProperty("javax.net.ssl.keyStorePassword");
    }

    // ------------------------------------------------------------------
    // Structural
    // ------------------------------------------------------------------

    @Test
    void singletonInstanceIsNotNull() {
        assertNotNull(NoOpUIBridge.INSTANCE);
    }

    @Test
    void implementsUIBridgeInterface() {
        assertTrue(NoOpUIBridge.INSTANCE instanceof UIBridge);
    }

    // ------------------------------------------------------------------
    // Original lifecycle methods (regression)
    // ------------------------------------------------------------------

    @Test
    void onTestStartedDoesNotThrow() {
        assertDoesNotThrow(() -> NoOpUIBridge.INSTANCE.onTestStarted(CTX));
    }

    @Test
    void onSampleDoesNotThrow() {
        assertDoesNotThrow(() -> NoOpUIBridge.INSTANCE.onSample(CTX, 100.0, 0.5));
    }

    @Test
    void onTestEndedDoesNotThrow() {
        assertDoesNotThrow(() -> NoOpUIBridge.INSTANCE.onTestEnded(CTX));
    }

    // ------------------------------------------------------------------
    // Audit finding 1 & 2: thread lifecycle
    // ------------------------------------------------------------------

    @Test
    void onThreadStartedDoesNotThrow() {
        assertDoesNotThrow(() -> NoOpUIBridge.INSTANCE.onThreadStarted(CTX, "Thread Group 1-1", 1));
    }

    @Test
    void onThreadStartedDoesNotThrowWithZeroCount() {
        assertDoesNotThrow(() -> NoOpUIBridge.INSTANCE.onThreadStarted(CTX, "Thread Group 1-1", 0));
    }

    @Test
    void onThreadFinishedDoesNotThrow() {
        assertDoesNotThrow(() -> NoOpUIBridge.INSTANCE.onThreadFinished(CTX, "Thread Group 1-1", 0));
    }

    @Test
    void onThreadFinishedDoesNotThrowWithHighCount() {
        assertDoesNotThrow(() -> NoOpUIBridge.INSTANCE.onThreadFinished(CTX, "Thread Group 1-5", 4));
    }

    // ------------------------------------------------------------------
    // Audit finding 3: reportError / reportInfo
    // ------------------------------------------------------------------

    @Test
    void reportErrorDoesNotThrow() {
        assertDoesNotThrow(() -> NoOpUIBridge.INSTANCE.reportError("something went wrong", "Error"));
    }

    @Test
    void reportErrorWithNullMessageDoesNotThrow() {
        // Defensive: engine may pass null in edge cases
        assertDoesNotThrow(() -> NoOpUIBridge.INSTANCE.reportError(null, "Error"));
    }

    @Test
    void reportInfoDoesNotThrow() {
        assertDoesNotThrow(() -> NoOpUIBridge.INSTANCE.reportInfo("test started successfully", "Info"));
    }

    @Test
    void reportInfoWithNullTitleDoesNotThrow() {
        assertDoesNotThrow(() -> NoOpUIBridge.INSTANCE.reportInfo("message", null));
    }

    // ------------------------------------------------------------------
    // Audit finding 4: refreshUI
    // ------------------------------------------------------------------

    @Test
    void refreshUIDoesNotThrow() {
        assertDoesNotThrow(() -> NoOpUIBridge.INSTANCE.refreshUI());
    }

    // ------------------------------------------------------------------
    // Audit finding 5: promptPassword
    // ------------------------------------------------------------------

    @Test
    void promptPasswordReturnsNullWhenPropertyNotSet() {
        System.clearProperty("javax.net.ssl.keyStorePassword");
        assertNull(NoOpUIBridge.INSTANCE.promptPassword("Enter keystore password:"));
    }

    @Test
    void promptPasswordReturnsSystemPropertyWhenSet() {
        System.setProperty("javax.net.ssl.keyStorePassword", "s3cr3t");
        assertEquals("s3cr3t", NoOpUIBridge.INSTANCE.promptPassword("Enter keystore password:"));
    }

    @Test
    void promptPasswordIgnoresMessage() {
        // The message parameter is informational only; no UI is shown
        System.clearProperty("javax.net.ssl.keyStorePassword");
        assertNull(NoOpUIBridge.INSTANCE.promptPassword(null));
    }

    // ------------------------------------------------------------------
    // Audit finding 6 & 7: onSampleReceived
    // ------------------------------------------------------------------

    @Test
    void onSampleReceivedDoesNotThrowOnSuccess() {
        assertDoesNotThrow(() ->
                NoOpUIBridge.INSTANCE.onSampleReceived(CTX, "HTTP Request", 150L, true));
    }

    @Test
    void onSampleReceivedDoesNotThrowOnFailure() {
        assertDoesNotThrow(() ->
                NoOpUIBridge.INSTANCE.onSampleReceived(CTX, "HTTP Request", 5000L, false));
    }

    @Test
    void onSampleReceivedDoesNotThrowWithZeroElapsed() {
        assertDoesNotThrow(() ->
                NoOpUIBridge.INSTANCE.onSampleReceived(CTX, "HTTP Request", 0L, true));
    }
}
