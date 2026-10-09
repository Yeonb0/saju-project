import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/errors";
import type { FortuneProduct } from "@/lib/ports/fortune";
import { createFakeFortunePort } from "./fortune";
import { createFakeReadings } from "./reading";

// 픽스처일 뿐이며 실제 섹션 구성 · 규칙과 무관하다. 상품은 src/mocks/fortune.ts 의 픽스처다.
async function firstProduct(type: FortuneProduct["fortuneType"]) {
  const [product] = await createFakeFortunePort().listProducts(type);
  expect(product).toBeDefined();
  return product;
}

describe("가짜 결과 (MOCK-PORT)", () => {
  it("a. 수능 상품이면 수능 섹션 5개를 준다", async () => {
    const readings = createFakeReadings();
    const id = readings.create(await firstProduct("SUNEUNG"));
    const reading = await readings.port.getReading(id);
    expect(reading.sections.map((s) => s.type)).toEqual([
      "TEXT",
      "PERIOD_GUIDANCE",
      "FOOD_RECOMMENDATION",
      "CHECKLIST",
      "UNKNOWN",
    ]);
  });

  it.each([
    "OVERALL",
    "LOVE",
    "WEALTH",
    "COMPATIBILITY",
    "SINSAL",
  ] as const)("b. %s 상품이면 일반 섹션 TEXT · TEXT · UNKNOWN 만 주고 수능 전용 섹션이 없다", async (type) => {
    const readings = createFakeReadings();
    const id = readings.create(await firstProduct(type));
    const reading = await readings.port.getReading(id);
    const types = reading.sections.map((s) => s.type);
    expect(types).toEqual(["TEXT", "TEXT", "UNKNOWN"]);
    for (const only of [
      "PERIOD_GUIDANCE",
      "FOOD_RECOMMENDATION",
      "CHECKLIST",
    ]) {
      expect(types).not.toContain(only);
    }
  });

  it("c. 없는 readingId 는 404 READING_NOT_FOUND", async () => {
    const readings = createFakeReadings();
    try {
      await readings.port.getReading("00000000-0000-4000-8000-000000000000");
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError);
      expect((error as ApiError).status).toBe(404);
      expect((error as ApiError).code).toBe("READING_NOT_FOUND");
      return;
    }
    throw new Error("던지지 않았다");
  });
});
