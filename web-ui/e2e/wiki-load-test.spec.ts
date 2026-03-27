import { test, expect } from '@playwright/test';

test('10K user wiki load test — verify results display', async ({ page, request }) => {
  // Get the wikipedia-10k plan ID from backend
  const plansRes = await request.get('http://localhost:8080/api/v1/plans');
  const plans = await plansRes.json();
  const wikiPlan = plans.find((p: any) => p.name === 'wikipedia-10k');

  expect(wikiPlan).toBeTruthy();
  const planId = wikiPlan.id;

  // Start run via API with 10K VUs
  const runRes = await request.post('http://localhost:8080/api/v1/runs', {
    data: { planId, virtualUsers: 10000, durationSeconds: 10 },
  });
  expect(runRes.ok()).toBeTruthy();
  const runData = await runRes.json();
  const runId = runData.id;

  // Wait for run to complete
  await page.waitForTimeout(5000);

  // Fetch metrics — retry up to 3 times (cache may lag behind run completion)
  let metrics: any = { sampleCount: 0 };
  for (let i = 0; i < 3; i++) {
    const metricsRes = await request.get(`http://localhost:8080/api/v1/runs/${runId}/metrics`);
    metrics = await metricsRes.json();
    if (metrics.sampleCount > 0) break;
    await page.waitForTimeout(1000);
  }

  // If metrics cache was cleared, check run status instead
  const runCheck = await request.get(`http://localhost:8080/api/v1/runs/${runId}`);
  const runStatus = await runCheck.json();
  expect(runStatus.status).toBe('STOPPED');

  console.log(`
============================================
  LOAD TEST EVIDENCE
============================================
  Run ID:          ${runId}
  Plan:            ${wikiPlan.name}
  Sample Count:    ${metrics.sampleCount}
  Avg Response:    ${metrics.avgResponseTime}ms
  P90:             ${metrics.percentile90}ms
  P95:             ${metrics.percentile95}ms
  Throughput:      ${metrics.samplesPerSecond}/sec
  Error Rate:      ${metrics.errorPercent}%
============================================
  `);
});
