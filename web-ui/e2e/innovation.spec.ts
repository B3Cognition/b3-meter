import { test, expect } from '@playwright/test';

/**
 * E2E tests for the Innovation UI panels: SLA Discovery, A/B Performance,
 * Chaos Load, and Self Smoke.
 *
 * These tests verify that each panel renders correctly when its tab is clicked,
 * including form fields, buttons, and key UI elements.
 */
test.describe('Innovation Feature Panels', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:3000', { waitUntil: 'networkidle' });
    await page.waitForTimeout(500);
  });

  // =========================================================================
  // SLA Discovery
  // =========================================================================

  test('SLA Discovery panel renders with form fields', async ({ page }) => {
    // Click the SLA tab
    await page.locator('button.tab:has-text("SLA")').click();

    // Verify the panel header
    await expect(page.locator('h3:has-text("Genetic SLA Discovery")')).toBeVisible();

    // Verify form fields
    await expect(page.locator('label:has-text("Target URL")')).toBeVisible();
    await expect(page.locator('label:has-text("Initial VUs")')).toBeVisible();
    await expect(page.locator('label:has-text("Max VUs")')).toBeVisible();

    // Verify start button is present
    await expect(page.locator('button:has-text("Start Discovery")')).toBeVisible();

    await page.screenshot({ path: 'e2e/evidence-sla-panel.png', fullPage: true });
  });

  test('SLA Discovery has correct default values', async ({ page }) => {
    await page.locator('button.tab:has-text("SLA")').click();

    // Check default URL value
    const urlInput = page.locator('input[type="text"]').first();
    await expect(urlInput).toHaveValue('http://localhost:8080/api/health');
  });

  // =========================================================================
  // A/B Performance
  // =========================================================================

  test('A/B Performance panel renders with dual URL inputs', async ({ page }) => {
    // Click the A/B tab
    await page.locator('button.tab:has-text("A/B")').click();

    // Verify the panel header
    await expect(page.locator('h3:has-text("A/B Performance Testing")')).toBeVisible();

    // Verify dual URL fields
    await expect(page.locator('label:has-text("URL A (Baseline)")')).toBeVisible();
    await expect(page.locator('label:has-text("URL B (Candidate)")')).toBeVisible();

    // Verify VU and duration fields
    await expect(page.locator('label:has-text("Virtual Users")')).toBeVisible();
    await expect(page.locator('label:has-text("Duration (s)")')).toBeVisible();

    // Verify start button
    await expect(page.locator('button:has-text("Start A/B Test")')).toBeVisible();

    await page.screenshot({ path: 'e2e/evidence-ab-panel.png', fullPage: true });
  });

  // =========================================================================
  // Chaos Load
  // =========================================================================

  test('Chaos Load panel renders with injection controls', async ({ page }) => {
    // Click the Chaos tab
    await page.locator('button.tab:has-text("Chaos")').click();

    // Verify the panel header
    await expect(page.locator('h3:has-text("Chaos + Load Fusion")')).toBeVisible();

    // Verify chaos injection fieldsets
    await expect(page.locator('text=Inject Latency')).toBeVisible();
    await expect(page.locator('text=Inject Errors')).toBeVisible();

    // Verify save and reset buttons
    await expect(page.locator('button:has-text("Save")')).toBeVisible();
    await expect(page.locator('button:has-text("Reset")')).toBeVisible();

    await page.screenshot({ path: 'e2e/evidence-chaos-panel.png', fullPage: true });
  });

  test('Chaos Load latency injection reveals fields on enable', async ({ page }) => {
    await page.locator('button.tab:has-text("Chaos")').click();

    // Enable latency injection checkbox
    const latencyCheckbox = page.locator('label:has-text("Inject Latency") input[type="checkbox"]');
    await latencyCheckbox.check();

    // Fields should appear
    await expect(page.locator('label:has-text("Min Delay (ms)")')).toBeVisible();
    await expect(page.locator('label:has-text("Max Delay (ms)")')).toBeVisible();
    await expect(page.locator('label:has-text("% of Requests Affected")')).toBeVisible();
  });

  // =========================================================================
  // Self Smoke
  // =========================================================================

  test('Self Smoke panel renders with server controls', async ({ page }) => {
    // Click the Self Smoke tab
    await page.locator('button.tab:has-text("Self Smoke")').click();

    // Verify the panel header
    await expect(page.locator('h3:has-text("Self Smoke")')).toBeVisible();
    await expect(page.locator('text=Regression Test Suite')).toBeVisible();

    // Verify the mock servers section
    await expect(page.locator('text=Mock Servers')).toBeVisible();

    // Verify server control buttons
    await expect(page.locator('button:has-text("Start All")')).toBeVisible();
    await expect(page.locator('button:has-text("Stop All")')).toBeVisible();
    await expect(page.locator('button:has-text("Refresh")')).toBeVisible();

    await page.screenshot({ path: 'e2e/evidence-selfsmoke-panel.png', fullPage: true });
  });

  test('Self Smoke has server status table', async ({ page }) => {
    await page.locator('button.tab:has-text("Self Smoke")').click();

    // Verify table headers
    const table = page.locator('.selfsmoke-table');
    await expect(table.locator('th:has-text("Server")')).toBeVisible();
    await expect(table.locator('th:has-text("Port")')).toBeVisible();
    await expect(table.locator('th:has-text("Protocol")')).toBeVisible();
    await expect(table.locator('th:has-text("Status")')).toBeVisible();
  });

  // =========================================================================
  // Tab switching
  // =========================================================================

  test('tabs switch between panels correctly', async ({ page }) => {
    // Start on Visual tab (default)
    await expect(page.locator('.tree-panel')).toBeVisible();

    // Switch to SLA
    await page.locator('button.tab:has-text("SLA")').click();
    await expect(page.locator('h3:has-text("Genetic SLA Discovery")')).toBeVisible();
    await expect(page.locator('.tree-panel')).not.toBeVisible();

    // Switch to A/B
    await page.locator('button.tab:has-text("A/B")').click();
    await expect(page.locator('h3:has-text("A/B Performance Testing")')).toBeVisible();

    // Switch to Chaos
    await page.locator('button.tab:has-text("Chaos")').click();
    await expect(page.locator('h3:has-text("Chaos + Load Fusion")')).toBeVisible();

    // Switch to Self Smoke
    await page.locator('button.tab:has-text("Self Smoke")').click();
    await expect(page.locator('h3:has-text("Self Smoke")')).toBeVisible();

    // Switch back to Visual
    await page.locator('button.tab:has-text("Visual")').click();
    await expect(page.locator('.tree-panel')).toBeVisible();
  });
});
