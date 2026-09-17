const { defineConfig } = require('@playwright/test');

const browserChannel = process.env.E2E_BROWSER_CHANNEL || undefined;

module.exports = defineConfig({
  testDir: './tests',
  timeout: 45_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost',
    browserName: 'chromium',
    channel: browserChannel,
    headless: true,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure'
  },
  reporter: [['list']]
});
