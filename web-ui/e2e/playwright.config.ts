/**
 * T047 — Playwright E2E Configuration
 *
 * Points at localhost:3000 (the Vite dev server / preview server).
 *
 * Usage:
 *   npx playwright test --config=e2e/playwright.config.ts
 *
 * Environment variables:
 *   BASE_URL  — override the default base URL (default: http://localhost:3000)
 *   CI        — when set, disables headed mode and enables retries
 */

import { defineConfig, devices } from '@playwright/test';

const BASE_URL = process.env['BASE_URL'] ?? 'http://localhost:3000';
const IS_CI    = Boolean(process.env['CI']);

export default defineConfig({
  /** Root directory for test files — relative to this config file. */
  testDir: '.',

  /** Glob pattern for test files. */
  testMatch: '**/*.spec.ts',

  /** Maximum time per test. */
  timeout: 30_000,

  /** Retries: 0 locally, 2 in CI. */
  retries: IS_CI ? 2 : 0,

  /** Parallelism: single worker in CI to avoid port conflicts. */
  workers: IS_CI ? 1 : undefined,

  /** Fail the entire run on first failure in CI. */
  forbidOnly: IS_CI,

  /** Reporter: line for local, GitHub Actions annotation in CI. */
  reporter: IS_CI ? 'github' : 'line',

  use: {
    /** Base URL used by page.goto('/') etc. */
    baseURL: BASE_URL,

    /** Collect traces only on first retry. */
    trace: 'on-first-retry',

    /** Capture screenshot only on test failure. */
    screenshot: 'only-on-failure',

    /** Capture video only on test failure. */
    video: 'retain-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },
  ],

  /**
   * Web server to start before the tests run (optional — comment out if you
   * prefer to start the dev server manually).
   *
   * Uncomment when running `npx playwright test` from the web-ui directory:
   *
   * webServer: {
   *   command: 'npm run preview',
   *   url: BASE_URL,
   *   reuseExistingServer: !IS_CI,
   *   timeout: 60_000,
   * },
   */
});
