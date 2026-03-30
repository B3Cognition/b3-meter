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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * No-operation (headless) implementation of {@link UIBridge}.
 *
 * <p>Used as a null-safe default when no real UI bridge is wired — for example,
 * in CI pipelines, worker-node containers, and unit-test harnesses. This avoids
 * null checks throughout engine code while guaranteeing no Swing/AWT calls are made.
 *
 * <p>Method contracts:
 * <ul>
 *   <li>{@link #reportError} and {@link #reportInfo} — log to SLF4J at WARN/INFO level</li>
 *   <li>{@link #promptPassword} — returns {@code javax.net.ssl.keyStorePassword} system property,
 *       or {@code null} if not set</li>
 *   <li>All other methods — intentional no-ops</li>
 * </ul>
 */
public final class NoOpUIBridge implements UIBridge {

    private static final Logger LOG = Logger.getLogger(NoOpUIBridge.class.getName());

    /** System property consulted by {@link #promptPassword} when no UI is available. */
    private static final String KEYSTORE_PASSWORD_PROPERTY = "javax.net.ssl.keyStorePassword";

    /** Singleton instance — stateless, safe to share. */
    public static final NoOpUIBridge INSTANCE = new NoOpUIBridge();

    private NoOpUIBridge() {}

    @Override
    public void onTestStarted(TestRunContext context) {
        // intentionally no-op
    }

    @Override
    public void onSample(TestRunContext context, double samplesPerSecond, double errorPercent) {
        // intentionally no-op
    }

    @Override
    public void onTestEnded(TestRunContext context) {
        // intentionally no-op
    }

    /**
     * No-op: thread-count updates have no effect in headless mode.
     */
    @Override
    public void onThreadStarted(TestRunContext context, String threadName, int activeThreadCount) {
        // intentionally no-op
    }

    /**
     * No-op: thread-count updates have no effect in headless mode.
     */
    @Override
    public void onThreadFinished(TestRunContext context, String threadName, int activeThreadCount) {
        // intentionally no-op
    }

    /**
     * Logs the error message at WARNING level instead of showing a Swing dialog.
     */
    @Override
    public void reportError(String message, String title) {
        LOG.log(Level.WARNING, "[{0}] {1}", new Object[]{title, message});
    }

    /**
     * Logs the info message at INFO level instead of showing a Swing dialog.
     */
    @Override
    public void reportInfo(String message, String title) {
        LOG.log(Level.INFO, "[{0}] {1}", new Object[]{title, message});
    }

    /**
     * No-op: UI refresh has no visible effect in headless mode.
     */
    @Override
    public void refreshUI() {
        // intentionally no-op
    }

    /**
     * Returns the keystore password from the {@code javax.net.ssl.keyStorePassword}
     * system property, or {@code null} if the property is not set.
     *
     * <p>This avoids the Swing password dialog that legacy {@code SSLManager} would show.
     */
    @Override
    public String promptPassword(String message) {
        return System.getProperty(KEYSTORE_PASSWORD_PROPERTY);
    }

    /**
     * No-op: sample results are not aggregated or forwarded in headless mode.
     */
    @Override
    public void onSampleReceived(TestRunContext context, String samplerLabel, long elapsed, boolean success) {
        // intentionally no-op
    }
}
