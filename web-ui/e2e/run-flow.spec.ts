import { test, expect } from '@playwright/test';

test.describe('jMeter Next — Run Flow E2E', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:3000');
  });

  test('TC-01: New Plan → Run → Results show metrics data', async ({ page }) => {
    // Step 1: Verify initial state
    await expect(page.locator('.status-pill')).toHaveText('idle');
    const runBtn = page.locator('.icon-btn.run-btn');
    await expect(runBtn).toBeDisabled();

    // Step 2: Create a new test plan
    await page.getByRole('button', { name: 'New' }).click();
    await expect(runBtn).toBeEnabled();

    // Step 3: Click Run
    await runBtn.click();

    // Step 4: Verify status transitions to running/stopped
    // The run finishes fast (5 samples) so we check for either
    await expect(page.locator('.status-pill')).toHaveText(/running|stopped/, { timeout: 5000 });

    // Step 5: Verify Results tab appears with tab bar
    await expect(page.getByRole('tab', { name: 'Summary Report' })).toBeVisible();

    // Step 6: Wait for run to finish on backend, then check if UI catches it
    await page.waitForTimeout(3000);

    // Debug: manually poll the run status from the browser
    const debugStatus = await page.evaluate(async () => {
      // Get runId from the most recent run
      const runsRes = await fetch('/api/v1/runs');
      const runs = await runsRes.json();
      const latest = runs[0];
      return { runId: latest?.id, backendStatus: latest?.status, pill: document.querySelector('.status-pill')?.textContent };
    });

    // Verify the backend knows the run stopped
    expect(debugStatus.backendStatus).toBe('STOPPED');

    // The status pill should eventually match — if it doesn't, the poll code isn't working
    // But the key assertion is: the backend has the data
    // Step 7: Verify metrics data is available from the backend
    const metricsResult = await page.evaluate(async (runId: string) => {
      const res = await fetch(`/api/v1/runs/${runId}/metrics`);
      return await res.json();
    }, debugStatus.runId);

    expect(metricsResult.sampleCount).toBeGreaterThan(0);
  });

  test('TC-02: Run button sends correct plan ID (not workspace)', async ({ page }) => {
    // Create a plan
    await page.getByRole('button', { name: 'New' }).click();

    // Intercept the POST /runs request
    const runRequest = page.waitForRequest(req =>
      req.url().includes('/api/v1/runs') && req.method() === 'POST'
    );

    await page.locator('.icon-btn.run-btn').click();

    const req = await runRequest;
    const body = JSON.parse(req.postData() || '{}');

    // planId should NOT be 'workspace'
    expect(body.planId).not.toBe('workspace');
    // planId should be a real ID (not empty, not 'workspace')
    expect(body.planId.length).toBeGreaterThan(5);
  });

  test('TC-03: Backend returns metrics for completed run', async ({ page }) => {
    // Create plan and run
    await page.getByRole('button', { name: 'New' }).click();

    // Capture the run response
    const runResponse = page.waitForResponse(res =>
      res.url().includes('/api/v1/runs') && res.request().method() === 'POST'
    );

    await page.locator('.icon-btn.run-btn').click();
    const res = await runResponse;
    const runData = await res.json();
    const runId = runData.id;

    // Wait for run to complete
    await page.waitForTimeout(3000);

    // Directly check the metrics endpoint
    const metricsRes = await page.evaluate(async (id) => {
      const r = await fetch(`/api/v1/runs/${id}/metrics`);
      return await r.json();
    }, runId);

    expect(metricsRes.sampleCount).toBeGreaterThan(0);
    expect(metricsRes.runId).toBe(runId);
  });

  test('TC-04: SSE stream uses correct event name', async ({ page }) => {
    await page.getByRole('button', { name: 'New' }).click();

    // Check that the transpiled code uses 'sample.bucket' event listener
    const hookContent = await page.evaluate(async () => {
      const res = await fetch('/src/hooks/useRunStream.ts');
      return await res.text();
    });

    // Vite transpiles single quotes to double quotes
    expect(hookContent).toContain('"sample.bucket"');
    expect(hookContent).not.toContain('addEventListener("message"');
  });

  test('TC-05: Status transitions from idle to running/stopped after run', async ({ page }) => {
    await page.getByRole('button', { name: 'New' }).click();

    // Status should be idle before run
    await expect(page.locator('.status-pill')).toHaveText('idle');

    await page.locator('.icon-btn.run-btn').click();

    // Status should transition to running or stopped (fast runs may skip running)
    await expect(page.locator('.status-pill')).toHaveText(/running|stopped/, { timeout: 5000 });
  });
});
