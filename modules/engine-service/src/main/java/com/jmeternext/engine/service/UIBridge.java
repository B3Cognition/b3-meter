package com.jmeternext.engine.service;

/**
 * Callback interface from the test engine to any UI layer.
 *
 * <p>The engine calls these methods to report progress and results. Implementations
 * may update a GUI, push SSE events to a browser client, or fan-out to multiple
 * registered listeners.
 *
 * <p>All methods on this interface must be safe to call from any thread.
 *
 * <p>This interface abstracts all ENGINE→GUI coupling points identified in the
 * GuiPackage audit (audit findings 1–7):
 * <ol>
 *   <li>JMeterThread: thread start/finish count updates</li>
 *   <li>RemoteThreadsListenerImpl: same pattern for remote threads</li>
 *   <li>JMeterUtils.reportErrorToUser / reportInfoToUser</li>
 *   <li>JMeterUtils.refreshUI (no null guard in legacy code)</li>
 *   <li>SSLManager: keystore password dialog</li>
 *   <li>ProxyControl.getTreeModel (no null guard in legacy code)</li>
 *   <li>ResultCollector.showErrorMessage</li>
 * </ol>
 */
public interface UIBridge {

    /**
     * Called when a test run starts.
     *
     * @param context immutable context describing the run that has started
     */
    void onTestStarted(TestRunContext context);

    /**
     * Called periodically during a run with the latest throughput metrics.
     *
     * @param context immutable context for the current run
     * @param samplesPerSecond current throughput in samples per second
     * @param errorPercent     percentage of samples that returned an error
     */
    void onSample(TestRunContext context, double samplesPerSecond, double errorPercent);

    /**
     * Called when a test run completes (successfully or after an error).
     *
     * @param context immutable context for the run that has ended
     */
    void onTestEnded(TestRunContext context);

    // -------------------------------------------------------------------------
    // Audit finding 1 & 2: JMeterThread / RemoteThreadsListenerImpl
    // -------------------------------------------------------------------------

    /**
     * Called when a virtual-user thread starts execution.
     *
     * <p>Replaces {@code GuiPackage.getInstance().getMainFrame().updateCounts()}
     * in {@code JMeterThread} and {@code RemoteThreadsListenerImpl}.
     *
     * @param context          immutable context for the current run
     * @param threadName       name of the thread that started (e.g. {@code "Thread Group 1-1"})
     * @param activeThreadCount total number of active threads after this start
     */
    void onThreadStarted(TestRunContext context, String threadName, int activeThreadCount);

    /**
     * Called when a virtual-user thread finishes execution.
     *
     * <p>Replaces {@code GuiPackage.getInstance().getMainFrame().updateCounts()}
     * in {@code JMeterThread} and {@code RemoteThreadsListenerImpl}.
     *
     * @param context          immutable context for the current run
     * @param threadName       name of the thread that finished
     * @param activeThreadCount total number of active threads after this finish
     */
    void onThreadFinished(TestRunContext context, String threadName, int activeThreadCount);

    // -------------------------------------------------------------------------
    // Audit finding 3: JMeterUtils.reportErrorToUser / reportInfoToUser
    // -------------------------------------------------------------------------

    /**
     * Reports an error-level message to the user.
     *
     * <p>Replaces {@code JMeterUtils.reportErrorToUser()} / {@code JOptionPane.showMessageDialog()}
     * in headless and web contexts.
     *
     * @param message human-readable error description
     * @param title   dialog or notification title
     */
    void reportError(String message, String title);

    /**
     * Reports an informational message to the user.
     *
     * <p>Replaces {@code JMeterUtils.reportInfoToUser()} in headless and web contexts.
     *
     * @param message human-readable informational text
     * @param title   dialog or notification title
     */
    void reportInfo(String message, String title);

    // -------------------------------------------------------------------------
    // Audit finding 4: JMeterUtils.refreshUI — no null guard in legacy code
    // -------------------------------------------------------------------------

    /**
     * Requests a UI refresh (e.g. repaint of tree/table after a config change).
     *
     * <p>Replaces the un-null-guarded {@code JMeterUtils.refreshUI()} call.
     * Headless implementations must be no-ops; GUI implementations may delegate to
     * Swing's {@code SwingUtilities.invokeLater}.
     */
    void refreshUI();

    // -------------------------------------------------------------------------
    // Audit finding 5: SSLManager password dialog
    // -------------------------------------------------------------------------

    /**
     * Prompts the user for a keystore or certificate password.
     *
     * <p>Replaces the Swing password-dialog in {@code SSLManager}.
     * Headless implementations should return the value of the system property
     * {@code javax.net.ssl.keyStorePassword}, or {@code null} if not set.
     * Web implementations must throw {@link UnsupportedOperationException}
     * because authentication credentials are handled through the web UI auth flow.
     *
     * @param message prompt message shown to the user
     * @return the entered password, or {@code null} if the user cancelled / no UI is available
     */
    String promptPassword(String message);

    // -------------------------------------------------------------------------
    // Audit finding 6 & 7: ProxyControl / ResultCollector — sample-level errors
    // -------------------------------------------------------------------------

    /**
     * Called when an individual sample result is received from a sampler.
     *
     * <p>Replaces {@code GuiPackage.showErrorMessage()} calls from
     * {@code ResultCollector} and the unguarded {@code ProxyControl.getTreeModel()}.
     * Implementations may aggregate, forward over SSE, or ignore.
     *
     * @param context      immutable context for the current run
     * @param samplerLabel label of the sampler that produced this result
     * @param elapsed      elapsed time in milliseconds
     * @param success      {@code true} if the sampler reported success
     */
    void onSampleReceived(TestRunContext context, String samplerLabel, long elapsed, boolean success);
}
