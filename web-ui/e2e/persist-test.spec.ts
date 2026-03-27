import { test, expect } from '@playwright/test';

test('imported plans persist across page reloads', async ({ page }) => {
  // Load page
  await page.goto('http://localhost:3000', { waitUntil: 'networkidle' });
  await page.waitForTimeout(1000);

  // Check if saved plans loaded from backend
  const treeText = await page.locator('.tree-panel').textContent();
  console.log('Tree panel:', treeText?.substring(0, 300));

  // Should see "wikipedia-10k" in the tree (imported via API earlier)
  const hasWikipedia = treeText?.includes('wikipedia-10k');
  console.log('Has wikipedia-10k plan:', hasWikipedia);

  // Verify the plan count in status bar
  const statusBar = await page.locator('.status-bar').textContent();
  console.log('Status bar:', statusBar);

  // Take screenshot
  await page.screenshot({ path: 'e2e/persist-evidence.png', fullPage: true });

  expect(hasWikipedia).toBe(true);
});

test('run wikipedia-10k plan and see results', async ({ page }) => {
  await page.goto('http://localhost:3000', { waitUntil: 'networkidle' });
  await page.waitForTimeout(1000);

  // Click on the wikipedia-10k plan in the tree
  const wikiNode = page.locator('text=wikipedia-10k');
  await expect(wikiNode).toBeVisible({ timeout: 5000 });
  await wikiNode.click();

  // Click Run
  await page.locator('.icon-btn.run-btn').click();

  // Wait for run to complete and show results
  await page.waitForTimeout(8000);

  const statusPill = await page.locator('.status-pill').textContent();
  const hasTable = await page.locator('.summary-report table').count();

  console.log('Status:', statusPill, 'Has table:', hasTable);

  await page.screenshot({ path: 'e2e/wiki-run-evidence.png', fullPage: true });

  expect(statusPill).toBe('stopped');
  expect(hasTable).toBeGreaterThan(0);
});
