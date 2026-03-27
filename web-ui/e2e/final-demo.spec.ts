import { test, expect } from '@playwright/test';

test('DEMO: Wikipedia 10K — persist + run + results evidence', async ({ page }) => {
  // Fresh load — plans should load from DB
  await page.goto('http://localhost:3000', { waitUntil: 'networkidle' });
  await page.waitForTimeout(1000);

  // Verify wikipedia-10k plan persisted
  await expect(page.locator('text=wikipedia-10k')).toBeVisible();

  // Screenshot 1: Plans loaded from DB
  await page.screenshot({ path: 'e2e/evidence-1-plans-loaded.png', fullPage: true });

  // Select and run the wikipedia plan
  await page.locator('text=wikipedia-10k').click();
  await page.locator('.icon-btn.run-btn').click();

  // Wait for completion
  await page.waitForTimeout(10000);

  // Screenshot 2: Results with data
  await page.screenshot({ path: 'e2e/evidence-2-results.png', fullPage: true });

  // Verify results
  const statusPill = await page.locator('.status-pill').textContent();
  const summaryText = await page.locator('[data-testid="tabpanel-summary"]').textContent();

  expect(statusPill).toBe('stopped');
  expect(summaryText).not.toContain('No data yet');

  // Click Charts tab for throughput chart
  await page.getByRole('tab', { name: 'Charts' }).click();
  await page.waitForTimeout(500);
  await page.screenshot({ path: 'e2e/evidence-3-charts.png', fullPage: true });

  // Reload page and verify plan is still there
  await page.reload({ waitUntil: 'networkidle' });
  await page.waitForTimeout(1000);
  await expect(page.locator('text=wikipedia-10k')).toBeVisible();

  // Screenshot 4: Plan survives reload
  await page.screenshot({ path: 'e2e/evidence-4-after-reload.png', fullPage: true });

  console.log(`
============================================
  EVIDENCE CAPTURED
============================================
  1. evidence-1-plans-loaded.png  — DB-persisted plans auto-load
  2. evidence-2-results.png       — Summary Report with metrics
  3. evidence-3-charts.png        — Charts/throughput view
  4. evidence-4-after-reload.png  — Plans survive page reload
============================================
  `);
});
