import { defineConfig, devices } from '@playwright/test';

// End-to-end tests: a real browser against the real stack —
// Chromium → Angular dev server (or, in CI, the Cloudflare Worker) → Spring Boot → PostgreSQL.
// They rely on the backend's "demo" profile data (demo@asbl.club / password123, association club-demo).
//
// Locally: start the backend with the demo profile first, then `npm run e2e` (Angular is started for you).
// CI: the workflow sets E2E_BACKEND_COMMAND so Playwright starts the backend too.
const ci = !!process.env['CI'];

export default defineConfig({
  testDir: './e2e',
  workers: 1, // one shared backend and database: run flows one after another
  forbidOnly: ci,
  retries: ci ? 1 : 0,
  reporter: ci ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://localhost:4200',
    locale: 'en-US', // the app picks up the browser language: tests read English
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command:
        process.env['E2E_BACKEND_COMMAND'] ??
        'echo "Start the backend with the demo profile first (see playwright.config.ts)." && exit 1',
      url: 'http://localhost:8080/actuator/health',
      reuseExistingServer: true,
      timeout: 180_000,
    },
    {
      // Default: the Angular dev server (with its dev proxy). CI instead builds the app and serves it through
      // the Cloudflare Worker, like production (E2E_FRONTEND_COMMAND, E2E_THROUGH_WORKER).
      command: process.env['E2E_FRONTEND_COMMAND'] ?? 'npm start',
      url: 'http://localhost:4200',
      reuseExistingServer: !ci,
      timeout: 180_000,
    },
  ],
});
