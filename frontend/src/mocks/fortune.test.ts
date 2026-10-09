import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/errors";
import type { FortuneSelection } from "@/lib/ports/fortune";
import type { TopUpProduct } from "@/lib/ports/topUp";
import { createFakeFortunePort } from "./fortune";
import { createFakeWallet } from "./wallet";

// 픽스처일 뿐이며 실제 가격 · 규칙과 무관하다. 상품 · 금액은 src/mocks/fortune.ts 의 픽스처다.
const PERSON_ID = "66666666-6666-4666-8666-666666666666";

async function selectionFor(port: ReturnType<typeof createFakeFortunePort>) {
  const [product] = await port.listProducts("SUNEUNG");
  const selection: FortuneSelection = {
    productCode: product.code,
    personId: PERSON_ID,
    counterpartPersonId: null,
  };
  return { product, selection };
}

const codeOf = (error: unknown) =>
  error instanceof ApiError ? error.code : null;

describe("가짜 운세 구매 포트 (MOCK-PORT)", () => {
  it("잔액이 모자라면 견적이 부족분 · 추천 충전을 주고 구매 후 잔액은 비운다", async () => {
    const port = createFakeFortunePort({ wallet: createFakeWallet(1) });
    const { product, selection } = await selectionFor(port);
    const quote = await port.createQuote(selection);
    expect(quote.walletBalance).toBe(1);
    expect(quote.shortage).toBe(product.price.amount - 1);
    expect(quote.recommendedTopUp).not.toBeNull();
    expect(quote.balanceAfter).toBeNull();
  });

  it("잔액이 충분하면 서버가 구매 후 잔액을 준다", async () => {
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    const { product, selection } = await selectionFor(port);
    const quote = await port.createQuote(selection);
    expect(quote.shortage).toBe(0);
    expect(quote.balanceAfter).toBe(100 - product.price.amount);
  });

  it("같은 키 재요청은 같은 결과이고 한 번만 차감한다", async () => {
    const wallet = createFakeWallet(100);
    const port = createFakeFortunePort({ wallet });
    const { product, selection } = await selectionFor(port);
    const { quoteId } = await port.createQuote(selection);
    const first = await port.purchase({ quoteId, selection }, "key-1");
    const again = await port.purchase({ quoteId, selection }, "key-1");
    expect(again).toEqual(first);
    expect(wallet.balance()).toBe(100 - product.price.amount);
    expect(first.balance).toBe(wallet.balance());
  });

  it("같은 키에 다른 본문은 409 IDEMPOTENCY_KEY_REUSED", async () => {
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    const { selection } = await selectionFor(port);
    const a = await port.createQuote(selection);
    const b = await port.createQuote(selection);
    await port.purchase({ quoteId: a.quoteId, selection }, "key-1");
    await expect(
      port.purchase({ quoteId: b.quoteId, selection }, "key-1"),
    ).rejects.toSatisfy((e) => codeOf(e) === "IDEMPOTENCY_KEY_REUSED");
  });

  it("잔액이 모자란 채로 구매하면 409 INSUFFICIENT_BALANCE, 잔액은 그대로", async () => {
    const wallet = createFakeWallet(1);
    const port = createFakeFortunePort({ wallet });
    const { selection } = await selectionFor(port);
    const { quoteId } = await port.createQuote(selection);
    await expect(
      port.purchase({ quoteId, selection }, "key-1"),
    ).rejects.toSatisfy((e) => codeOf(e) === "INSUFFICIENT_BALANCE");
    expect(wallet.balance()).toBe(1);
  });

  it("충전 뒤 같은 견적을 재확인하면 새 잔액으로 다시 계산한다 (같은 quoteId)", async () => {
    const wallet = createFakeWallet(1);
    const port = createFakeFortunePort({ wallet });
    const { selection } = await selectionFor(port);
    const quote = await port.createQuote(selection);
    wallet.credit(100);
    const again = await port.getQuote(quote.quoteId);
    expect(again.quoteId).toBe(quote.quoteId);
    expect(again.walletBalance).toBe(101);
    expect(again.shortage).toBe(0);
  });

  it("유효 시간이 지난 견적은 409 QUOTE_EXPIRED", async () => {
    let now = 0;
    const port = createFakeFortunePort({
      wallet: createFakeWallet(100),
      now: () => now,
    });
    const { selection } = await selectionFor(port);
    const { quoteId } = await port.createQuote(selection);
    now = 31 * 60 * 1000;
    await expect(port.getQuote(quoteId)).rejects.toSatisfy(
      (e) => codeOf(e) === "QUOTE_EXPIRED",
    );
  });

  it("quote_expired: 첫 견적 구매는 409 QUOTE_EXPIRED, 새 견적은 성공", async () => {
    const port = createFakeFortunePort({
      scenario: "quote_expired",
      wallet: createFakeWallet(100),
    });
    const { selection } = await selectionFor(port);
    const first = await port.createQuote(selection);
    await expect(
      port.purchase({ quoteId: first.quoteId, selection }, "key-1"),
    ).rejects.toSatisfy((e) => codeOf(e) === "QUOTE_EXPIRED");
    const next = await port.createQuote(selection);
    const result = await port.purchase(
      { quoteId: next.quoteId, selection },
      "key-2",
    );
    expect(result.status).toBe("FULFILLED");
  });

  it("processing_409: 첫 요청은 409, 같은 키 재요청은 FULFILLED", async () => {
    const port = createFakeFortunePort({
      scenario: "processing_409",
      wallet: createFakeWallet(100),
    });
    const { selection } = await selectionFor(port);
    const { quoteId } = await port.createQuote(selection);
    await expect(
      port.purchase({ quoteId, selection }, "key-1"),
    ).rejects.toSatisfy((e) => codeOf(e) === "IDEMPOTENCY_REQUEST_PROCESSING");
    const result = await port.purchase({ quoteId, selection }, "key-1");
    expect(result.status).toBe("FULFILLED");
  });

  it("generation_failed: FAILED · 차감 되돌림", async () => {
    const wallet = createFakeWallet(100);
    const port = createFakeFortunePort({
      scenario: "generation_failed",
      wallet,
    });
    const { selection } = await selectionFor(port);
    const { quoteId } = await port.createQuote(selection);
    const result = await port.purchase({ quoteId, selection }, "key-1");
    expect(result.status).toBe("FAILED");
    expect(result.refunded).toBe(true);
    expect(wallet.balance()).toBe(100);
  });

  describe("추천 충전 상품 — 가짜 서버가 BE-A 규칙으로 계산 (P-06 · MOCK-PORT)", () => {
    // 픽스처일 뿐이며 실제 가격 · 규칙과 무관하다. 잔액 1 이면 부족분은 12 (상품 가격 13 − 1).
    const topUp = (
      code: string,
      price: number,
      credited: number,
      active = true,
    ): TopUpProduct => ({
      code,
      price: { currency: "KRW", amount: price },
      paidAmount: credited,
      bonusAmount: 0,
      creditedAmount: credited,
      active,
    });

    async function recommendedFor(
      topUpProducts: readonly TopUpProduct[],
      balance = 1,
    ) {
      const port = createFakeFortunePort({
        wallet: createFakeWallet(balance),
        topUpProducts,
      });
      const { selection } = await selectionFor(port);
      return port.createQuote(selection);
    }

    it("a. 부족분을 채우는 활성 상품이 여럿이면 가격이 가장 낮은 것 (배열 첫 번째가 아니어도)", async () => {
      const quote = await recommendedFor([
        topUp("EXPENSIVE", 900, 50),
        topUp("CHEAP", 300, 12),
        topUp("MIDDLE", 500, 20),
      ]);
      expect(quote.shortage).toBe(12);
      expect(quote.recommendedTopUp).toBe("CHEAP");
    });

    it("b. 가격이 같으면 code 사전순 앞", async () => {
      const quote = await recommendedFor([
        topUp("B_SAME", 300, 30),
        topUp("A_SAME", 300, 20),
        topUp("C_SAME", 300, 12),
      ]);
      expect(quote.recommendedTopUp).toBe("A_SAME");
    });

    it("c. 비활성 상품은 충분하고 가장 싸도 추천하지 않는다", async () => {
      const quote = await recommendedFor([
        topUp("INACTIVE_CHEAPEST", 100, 99, false),
        topUp("ACTIVE", 700, 12),
      ]);
      expect(quote.recommendedTopUp).toBe("ACTIVE");
    });

    it("d. 부족분을 채우는 상품이 없으면 부족한데도 추천은 null", async () => {
      const quote = await recommendedFor([
        topUp("TOO_SMALL", 100, 11),
        topUp("INACTIVE_BIG", 200, 99, false),
      ]);
      expect(quote.shortage).toBeGreaterThan(0);
      expect(quote.recommendedTopUp).toBeNull();
    });

    it("e. 잔액이 충분하면 추천은 null 이고 구매 후 잔액은 숫자", async () => {
      const quote = await recommendedFor([topUp("ANY", 100, 99)], 100);
      expect(quote.shortage).toBe(0);
      expect(quote.recommendedTopUp).toBeNull();
      expect(quote.balanceAfter).toBe(87);
    });

    it("f. 없는 quoteId 로 getQuote 하면 404 RESOURCE_NOT_FOUND", async () => {
      const port = createFakeFortunePort();
      const error = await port.getQuote("missing").catch((e: unknown) => e);
      expect(error).toBeInstanceOf(ApiError);
      expect((error as ApiError).status).toBe(404);
      expect(codeOf(error)).toBe("RESOURCE_NOT_FOUND");
    });
  });
});
