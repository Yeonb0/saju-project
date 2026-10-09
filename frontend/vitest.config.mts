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
    setupFiles: ["./vitest.setup.ts"],
    exclude: ["e2e/**", "node_modules/**"],
    // 순수 로직(.ts)은 node, 화면(.tsx)은 jsdom — jsdom 준비가 실행 시간의 대부분이라 필요한 파일에서만 만든다.
    // 브라우저 저장소를 쓰는 .ts 테스트는 파일 첫 줄의 `// @vitest-environment jsdom` 로 jsdom 을 고른다.
    projects: [
      {
        extends: true,
        test: {
          name: "node",
          environment: "node",
          include: ["src/**/*.{test,spec}.ts"],
        },
      },
      {
        extends: true,
        test: {
          name: "jsdom",
          environment: "jsdom",
          include: ["src/**/*.{test,spec}.tsx"],
        },
      },
    ],
    // 각 파일 첫 테스트가 jsdom 준비 · 워커 경쟁으로 5초를 넘는 간헐 실패가 있었다 (2026-10-04, 10단계 진단).
    // 동시 워커를 절반으로 줄이고 한도를 15초로 둔다.
    // 테스트 안에 실제로 기다리는 곳은 없으므로 한도를 늘려도 멈춘 테스트는 그대로 실패한다.
    maxWorkers: "50%",
    testTimeout: 15_000,
  },
});
