import { defineConfig, devices } from "@playwright/test";

// docs/PHASES.md 3-1: 모바일 뷰포트로만 돌린다. 돈이 걸린 흐름(수능이·선물 구매·선물 수신)에만 E2E.
const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:3000";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? "github" : "list",
  use: {
    baseURL,
    trace: "on-first-retry",
  },
  projects: [
    { name: "Mobile Chrome", use: { ...devices["Pixel 7"] } },
    { name: "Mobile Safari", use: { ...devices["iPhone 15"] } },
  ],
  webServer: {
    command: "pnpm dev",
    url: baseURL,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
