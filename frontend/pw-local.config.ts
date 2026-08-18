import { defineConfig, devices } from '@playwright/test';
export default defineConfig({
  testDir: './e2e',
  workers: 1,
  timeout: 90_000,
  reporter: 'list',
  use: { baseURL: 'http://localhost:4173', trace: 'off', navigationTimeout: 60_000 },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
