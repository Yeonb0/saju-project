import { fileURLToPath } from "node:url";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

// docs/PHASES.md 3-1: Vitest는 D-day·가격 계산 등 순수 로직 검증용.
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./vitest.setup.ts"],
    include: ["src/**/*.{test,spec}.{ts,tsx}"],
    exclude: ["e2e/**", "node_modules/**"],
    // 각 파일 첫 테스트가 jsdom 준비 · 워커 경쟁으로 5초를 넘는 간헐 실패가 있었다 (2026-10-04, 10단계 진단).
    // 동시 워커를 절반으로 줄이고 한도를 15초로 둔다.
    // 테스트 안에 실제로 기다리는 곳은 없으므로 한도를 늘려도 멈춘 테스트는 그대로 실패한다.
    maxWorkers: "50%",
    testTimeout: 15_000,
  },
});
