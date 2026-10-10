import { describe, expect, it } from "vitest";
import {
  createIdempotencyKey,
  createIdempotentCommand,
  createKeyedCommand,
  createOrderBoundCommand,
} from "./idempotency";

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
// 픽스처일 뿐이며 실제 주문 · 상품 · 가격과 무관하다.
const ORDER_ID = "3f2b8c1e-6a4d-4e2f-9b7a-1c0d5e8f2a6b";
const PAYLOAD = { productCode: "FIXTURE_PRODUCT" };

describe("createIdempotentCommand", () => {
  it("키는 UUID 형식이다", () => {
    expect(createIdempotentCommand(PAYLOAD).key).toMatch(UUID_PATTERN);
  });

  it("새 구매 의도마다 다른 키가 나온다", () => {
    const first = createIdempotentCommand(PAYLOAD);
    const second = createIdempotentCommand(PAYLOAD);
    expect(first.key).not.toBe(second.key);
  });

  it("본문은 만든 시점에 고정된다 — 원본 객체를 바꿔도 재요청 본문은 같다", () => {
    // 회귀 테스트: 같은 키에 다른 본문이 나가면 409 IDEMPOTENCY_KEY_REUSED (BE-A 결정 28).
    const payload = { productCode: "FIXTURE_PRODUCT" };
    const command = createIdempotentCommand(payload);
    payload.productCode = "CHANGED";
    expect(command.body).toBe(
      JSON.stringify({ productCode: "FIXTURE_PRODUCT" }),
    );
  });

  it("만든 뒤에는 키 · 본문을 바꿀 수 없다", () => {
    expect(Object.isFrozen(createIdempotentCommand(PAYLOAD))).toBe(true);
  });
});

describe("createOrderBoundCommand", () => {
  it("같은 주문 ID 면 새로고침해도 같은 키 · 같은 본문이다", () => {
    // 회귀 테스트: PG 복귀 페이지 새로고침이 새 키로 승인을 다시 보내면 안 된다 (TOPUP-DONE).
    const first = createOrderBoundCommand(ORDER_ID, PAYLOAD);
    const reloaded = createOrderBoundCommand(ORDER_ID, PAYLOAD);
    expect(first.key).toBe(ORDER_ID);
    expect(reloaded.key).toBe(first.key);
    expect(reloaded.body).toBe(first.body);
  });

  it("주문 ID 가 UUID 가 아니면 던진다", () => {
    expect(() => createOrderBoundCommand("not-a-uuid", PAYLOAD)).toThrow();
  });
});

describe("createKeyedCommand", () => {
  it("같은 키 · 같은 입력이면 key · body 가 같고 body 는 payload 와 같다", () => {
    const key = createIdempotencyKey();
    const first = createKeyedCommand(key, PAYLOAD);
    const second = createKeyedCommand(key, PAYLOAD);
    expect(first.key).toBe(key);
    expect(second.key).toBe(first.key);
    expect(second.body).toBe(first.body);
    expect(JSON.parse(first.body)).toEqual(PAYLOAD);
  });

  it("키가 UUID 가 아니면 던진다", () => {
    expect(() => createKeyedCommand("not-a-uuid", PAYLOAD)).toThrow();
  });
});

describe("createIdempotencyKey", () => {
  it("UUID 형식이고 부를 때마다 다르다", () => {
    const first = createIdempotencyKey();
    expect(first).toMatch(UUID_PATTERN);
    expect(createIdempotencyKey()).not.toBe(first);
  });
});
