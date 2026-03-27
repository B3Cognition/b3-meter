/**
 * T047 — RunDashboard Page Object
 *
 * Encapsulates all interactions with the run dashboard / run controls UI:
 *   - Click Run / Stop / Stop Now
 *   - Read the current status badge
 *   - Wait for specific status transitions
 *   - Read metrics displayed in the live dashboard
 *
 * Usage:
 *   import { RunDashboardPage } from './RunDashboard.page.js';
 *
 *   test('start and stop a run', async ({ page }) => {
 *     const dashboard = new RunDashboardPage(page);
 *     await dashboard.goto();
 *     await dashboard.clickRun();
 *     await dashboard.waitForStatus('running');
 *     await dashboard.clickStop();
 *     await dashboard.waitForStatus('stopped');
 *     expect(await dashboard.getStatus()).toBe('stopped');
 *   });
 */

import type { Page, Locator } from '@playwright/test';

/** Status values that map to the RunStatus type in runStore.ts. */
export type RunStatus =
  | 'idle'
  | 'starting'
  | 'running'
  | 'stopping'
  | 'stopped'
  | 'error';

/** Human-readable labels rendered by the STATUS_LABEL map in RunControls. */
const STATUS_LABEL_MAP: Record<RunStatus, string> = {
  idle:     'Idle',
  starting: 'Starting…',
  running:  'Running',
  stopping: 'Stopping…',
  stopped:  'Stopped',
  error:    'Error',
};

export class RunDashboardPage {
  readonly page: Page;

  // ---------------------------------------------------------------------------
  // Locators
  // ---------------------------------------------------------------------------

  /** Run button (visible when status is idle/stopped/error). */
  readonly runButton: Locator;

  /** Stop button (visible when status is starting/running/stopping). */
  readonly stopButton: Locator;

  /** Stop Now button (visible when status is starting/running/stopping). */
  readonly stopNowButton: Locator;

  /** Status badge element (data-testid="run-status-badge"). */
  readonly statusBadge: Locator;

  /** Inline error message (role="alert"). */
  readonly errorAlert: Locator;

  /** The metrics / live-dashboard container. */
  readonly metricsDashboard: Locator;

  constructor(page: Page) {
    this.page = page;

    this.runButton        = page.getByRole('button', { name: 'Run' });
    this.stopButton       = page.getByRole('button', { name: 'Stop' });
    this.stopNowButton    = page.getByRole('button', { name: 'Stop Now' });
    this.statusBadge      = page.getByTestId('run-status-badge');
    this.errorAlert       = page.locator('[role="alert"].run-controls__error');
    this.metricsDashboard = page.locator('[data-testid="metrics-dashboard"]');
  }

  // ---------------------------------------------------------------------------
  // Navigation
  // ---------------------------------------------------------------------------

  /**
   * Navigate to the run dashboard route.
   * Adjust path if the app uses a sub-route (e.g. '/dashboard').
   */
  async goto(path = '/'): Promise<void> {
    await this.page.goto(path);
    await this.page.waitForLoadState('networkidle');
  }

  // ---------------------------------------------------------------------------
  // Actions
  // ---------------------------------------------------------------------------

  /**
   * Click the Run button to start a test run.
   * Waits for the button to be enabled before clicking.
   */
  async clickRun(): Promise<void> {
    await this.runButton.waitFor({ state: 'visible' });
    await this.runButton.click();
  }

  /**
   * Click the Stop button for a graceful stop.
   * Waits for the button to be visible before clicking.
   */
  async clickStop(): Promise<void> {
    await this.stopButton.waitFor({ state: 'visible' });
    await this.stopButton.click();
  }

  /**
   * Click the Stop Now button for an immediate stop.
   * Waits for the button to be visible before clicking.
   */
  async clickStopNow(): Promise<void> {
    await this.stopNowButton.waitFor({ state: 'visible' });
    await this.stopNowButton.click();
  }

  // ---------------------------------------------------------------------------
  // Status queries
  // ---------------------------------------------------------------------------

  /**
   * Return the current status string by reading the badge text and reversing
   * the STATUS_LABEL_MAP lookup.
   *
   * Returns 'idle' if the badge text cannot be matched.
   */
  async getStatus(): Promise<RunStatus> {
    const text = (await this.statusBadge.textContent())?.trim() ?? '';
    const entry = Object.entries(STATUS_LABEL_MAP).find(([, label]) => label === text);
    return (entry?.[0] as RunStatus | undefined) ?? 'idle';
  }

  /**
   * Return the raw text content of the status badge (e.g. "Running", "Idle").
   */
  async getStatusText(): Promise<string> {
    return (await this.statusBadge.textContent())?.trim() ?? '';
  }

  /**
   * Wait until the status badge shows the given status.
   *
   * @param status  - The target RunStatus to wait for.
   * @param timeout - Maximum wait in milliseconds (default 15 s).
   */
  async waitForStatus(status: RunStatus, timeout = 15_000): Promise<void> {
    const expectedLabel = STATUS_LABEL_MAP[status];
    await this.statusBadge.waitFor({ state: 'visible', timeout });
    await this.page.waitForFunction(
      ({ selector, label }: { selector: string; label: string }) => {
        const el = document.querySelector(selector);
        return el?.textContent?.trim() === label;
      },
      { selector: '[data-testid="run-status-badge"]', label: expectedLabel },
      { timeout },
    );
  }

  /**
   * Return true if the Run button is currently enabled (plan loaded, not active).
   */
  async isRunEnabled(): Promise<boolean> {
    return this.runButton.isEnabled();
  }

  /**
   * Return true if the Stop button is currently visible.
   */
  async isStopVisible(): Promise<boolean> {
    return this.stopButton.isVisible();
  }

  // ---------------------------------------------------------------------------
  // Error handling
  // ---------------------------------------------------------------------------

  /**
   * Return the text of the inline error alert, or null if no error is shown.
   */
  async getErrorText(): Promise<string | null> {
    const visible = await this.errorAlert.isVisible();
    if (!visible) return null;
    return this.errorAlert.textContent();
  }

  // ---------------------------------------------------------------------------
  // Metrics
  // ---------------------------------------------------------------------------

  /**
   * Return the text content of a metric cell identified by its label.
   *
   * The selector assumes metrics are rendered as `[data-metric="<name>"]`
   * elements — adjust to match the actual DOM structure.
   *
   * @param metricName - The metric identifier (e.g. 'throughput', 'error_rate').
   */
  async getMetricValue(metricName: string): Promise<string | null> {
    const cell = this.page.locator(`[data-metric="${metricName}"]`);
    const visible = await cell.isVisible();
    if (!visible) return null;
    return cell.textContent();
  }

  /**
   * Wait until the metrics dashboard container is visible.
   */
  async waitForDashboard(timeout = 10_000): Promise<void> {
    await this.metricsDashboard.waitFor({ state: 'visible', timeout });
  }
}
