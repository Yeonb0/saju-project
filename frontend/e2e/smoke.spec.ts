import { expect, test } from "@playwright/test";

// 세팅 확인용 스모크. 실제 3개 흐름 E2E는 Phase 7에서 추가한다.
test("홈 페이지가 200으로 열린다", async ({ page }) => {
  const response = await page.goto("/");
  expect(response?.status()).toBe(200);
});
