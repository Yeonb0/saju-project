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
    fortuneType: "SUNEUNG",
    productCode: product.code,
    personId: PERSON_ID,
    counterpartPersonId: null,
    productOption: null,
    questionKey: null,
    relationType: null,
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
    const wallet = createFakeWallet(97, 3);
    const port = createFakeFortunePort({
      scenario: "generation_failed",
      wallet,
    });
    const { selection } = await selectionFor(port);
    const { quoteId } = await port.createQuote(selection);
    const result = await port.purchase({ quoteId, selection }, "key-1");
    expect(result.status).toBe("FAILED");
    expect(result.readingId).toBeNull();
    expect(result.compensationStatus).toBe("NOT_REQUIRED");
    expect(result.nextAction).toBe("RETRY_PURCHASE");
    expect([wallet.paid(), wallet.bonus()]).toEqual([97, 3]);
  });

  it("fulfilled: FULFILLED · NOT_REQUIRED · VIEW_READING, readingId 있음", async () => {
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    const { selection } = await selectionFor(port);
    const { quoteId } = await port.createQuote(selection);
    const result = await port.purchase({ quoteId, selection }, "key-1");
    expect(result).toMatchObject({
      status: "FULFILLED",
      compensationStatus: "NOT_REQUIRED",
      nextAction: "VIEW_READING",
    });
    expect(result.readingId).toEqual(expect.any(String));
  });

  it("review_required: FAILED · REVIEW_REQUIRED · CONTACT_SUPPORT, 차감을 되돌리지 않는다", async () => {
    const wallet = createFakeWallet(100);
    const port = createFakeFortunePort({
      scenario: "review_required",
      wallet,
    });
    const { product, selection } = await selectionFor(port);
    const { quoteId } = await port.createQuote(selection);
    const result = await port.purchase({ quoteId, selection }, "key-1");
    expect(result).toMatchObject({
      status: "FAILED",
      compensationStatus: "REVIEW_REQUIRED",
      nextAction: "CONTACT_SUPPORT",
      readingId: null,
    });
    expect(wallet.balance()).toBe(100 - product.price.amount);
  });

  it("refunded: REFUNDED · COMPLETED · RETRY_PURCHASE, 차감을 되돌린다", async () => {
    const wallet = createFakeWallet(100);
    const port = createFakeFortunePort({ scenario: "refunded", wallet });
    const { selection } = await selectionFor(port);
    const { quoteId } = await port.createQuote(selection);
    const result = await port.purchase({ quoteId, selection }, "key-1");
    expect(result).toMatchObject({
      status: "REFUNDED",
      compensationStatus: "COMPLETED",
      nextAction: "RETRY_PURCHASE",
      readingId: null,
    });
    expect(wallet.balance()).toBe(100);
  });

  describe("getPurchaseStatus", () => {
    it("generating: 구매 응답은 GENERATING · WAIT · readingId null, 차감한다", async () => {
      const wallet = createFakeWallet(100);
      const port = createFakeFortunePort({ scenario: "generating", wallet });
      const { product, selection } = await selectionFor(port);
      const { quoteId } = await port.createQuote(selection);
      const result = await port.purchase({ quoteId, selection }, "key-1");
      expect(result).toMatchObject({
        status: "GENERATING",
        compensationStatus: "NOT_REQUIRED",
        nextAction: "WAIT",
        readingId: null,
      });
      expect(wallet.balance()).toBe(100 - product.price.amount);
    });

    it("generating: 조회하면 그때 결과를 만들고 FULFILLED · VIEW_READING, 이후 조회도 같은 값", async () => {
      let created = 0;
      const port = createFakeFortunePort({
        scenario: "generating",
        wallet: createFakeWallet(100),
        onFulfilled: () => `FIXTURE-READING-${++created}`,
      });
      const { selection } = await selectionFor(port);
      const { quoteId } = await port.createQuote(selection);
      const bought = await port.purchase({ quoteId, selection }, "key-1");
      expect(created).toBe(0);

      const first = await port.getPurchaseStatus(quoteId);
      expect(first).toEqual({
        purchaseId: bought.purchaseId,
        readingId: "FIXTURE-READING-1",
        status: "FULFILLED",
        compensationStatus: "NOT_REQUIRED",
        nextAction: "VIEW_READING",
      });
      expect(await port.getPurchaseStatus(quoteId)).toEqual(first);
      expect(created).toBe(1);
      // charged · balance 는 상태에 없다
      expect(first).not.toHaveProperty("charged");
      expect(first).not.toHaveProperty("balance");
    });

    it("구매 기록이 없으면 null", async () => {
      const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
      const { selection } = await selectionFor(port);
      const { quoteId } = await port.createQuote(selection);
      expect(await port.getPurchaseStatus(quoteId)).toBeNull();
      expect(await port.getPurchaseStatus("FIXTURE-UNKNOWN")).toBeNull();
    });

    it("견적마다 자기 구매 상태만 준다", async () => {
      const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
      const { selection } = await selectionFor(port);
      const first = await port.createQuote(selection);
      const bought = await port.purchase(
        { quoteId: first.quoteId, selection },
        "key-1",
      );
      const second = await port.createQuote(selection);
      expect(await port.getPurchaseStatus(second.quoteId)).toBeNull();
      expect((await port.getPurchaseStatus(first.quoteId))?.purchaseId).toBe(
        bought.purchaseId,
      );
    });

    it("processing_409 로 끝난 첫 요청은 기록이 없다 → null, 같은 키 재요청 뒤에는 상태가 있다", async () => {
      const port = createFakeFortunePort({
        scenario: "processing_409",
        wallet: createFakeWallet(100),
      });
      const { selection } = await selectionFor(port);
      const { quoteId } = await port.createQuote(selection);
      await expect(
        port.purchase({ quoteId, selection }, "key-1"),
      ).rejects.toBeInstanceOf(ApiError);
      expect(await port.getPurchaseStatus(quoteId)).toBeNull();
      await port.purchase({ quoteId, selection }, "key-1");
      expect(await port.getPurchaseStatus(quoteId)).toMatchObject({
        status: "FULFILLED",
      });
    });

    it("견적이 만료돼도 조회된다", async () => {
      let now = 0;
      const port = createFakeFortunePort({
        wallet: createFakeWallet(100),
        now: () => now,
      });
      const { selection } = await selectionFor(port);
      const { quoteId } = await port.createQuote(selection);
      await port.purchase({ quoteId, selection }, "key-1");
      now = 31 * 60 * 1000;
      await expect(port.getQuote(quoteId)).rejects.toSatisfy(
        (e) => codeOf(e) === "QUOTE_EXPIRED",
      );
      expect(await port.getPurchaseStatus(quoteId)).toMatchObject({
        status: "FULFILLED",
        nextAction: "VIEW_READING",
      });
    });
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

describe("가짜 운세 구매 포트 — fortuneType 규칙 (ADAPTER-HTTP 흉내)", () => {
  it("j. 상품과 다른 fortuneType 으로 createQuote 하면 422 INVALID_REQUEST", async () => {
    const port = createFakeFortunePort();
    const { selection } = await selectionFor(port);
    const error = await port
      .createQuote({ ...selection, fortuneType: "LOVE" })
      .catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(422);
    expect(codeOf(error)).toBe("INVALID_REQUEST");
  });

  it("k. 견적에 charged 가 있고 price 키는 없다", async () => {
    const port = createFakeFortunePort();
    const { product, selection } = await selectionFor(port);
    const quote = await port.createQuote(selection);
    expect(quote.charged).toEqual(product.price);
    expect("price" in quote).toBe(false);
  });
});

describe("가짜 운세 구매 포트 — 질문 선택 규칙 (GeneralReadingService 흉내)", () => {
  // 픽스처일 뿐이며 실제 상품 · 규칙과 무관하다
  const OTHER_ID = "99999999-9999-4999-8999-999999999999";
  const love = (over: Partial<FortuneSelection> = {}): FortuneSelection => ({
    fortuneType: "LOVE",
    productCode: "FIXTURE_LOVE_READING_ONLY",
    personId: PERSON_ID,
    counterpartPersonId: null,
    productOption: "READING_ONLY",
    questionKey: "CURRENT_RELATIONSHIP",
    relationType: null,
    ...over,
  });
  const compat = (over: Partial<FortuneSelection> = {}): FortuneSelection => ({
    fortuneType: "COMPATIBILITY",
    productCode: "FIXTURE_COMPATIBILITY_READING_ONLY",
    personId: PERSON_ID,
    counterpartPersonId: OTHER_ID,
    productOption: "READING_ONLY",
    questionKey: "OVERALL_MATCH",
    relationType: "LOVER",
    ...over,
  });
  const codeOfQuote = async (selection: FortuneSelection) => {
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    return port.createQuote(selection).then(
      () => null,
      (error: unknown) => codeOf(error),
    );
  };

  it("f. createQuote 오류 code 가 서버 규칙을 따른다", async () => {
    const { selection } = await selectionFor(
      createFakeFortunePort({ wallet: createFakeWallet(100) }),
    );
    // 수능운 + questionKey
    expect(
      await codeOfQuote({ ...selection, questionKey: "OVERALL_FLOW" }),
    ).toBe("INVALID_REQUEST");
    // 일반 운세에서 옵션이 상품의 option 과 다르다
    expect(
      await codeOfQuote(love({ productOption: "READING_WITH_TALISMAN" })),
    ).toBe("INVALID_REQUEST");
    // 모르는 questionKey
    expect(await codeOfQuote(love({ questionKey: "NO_SUCH_KEY" }))).toBe(
      "INVALID_QUESTION_KEY",
    );
    // 궁합 relationType null
    expect(await codeOfQuote(compat({ relationType: null }))).toBe(
      "RELATION_TYPE_REQUIRED",
    );
    // 궁합 상대 null
    expect(await codeOfQuote(compat({ counterpartPersonId: null }))).toBe(
      "COMPATIBILITY_PERSON_REQUIRED",
    );
    // 궁합이 아닌데 relationType
    expect(await codeOfQuote(love({ relationType: "LOVER" }))).toBe(
      "INVALID_REQUEST",
    );
  });

  it("g. 일반 운세 정상 견적은 구매까지 성공한다", async () => {
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    const selection = love();
    const { quoteId } = await port.createQuote(selection);
    const result = await port.purchase({ quoteId, selection }, "key-q-1");
    expect(result.status).toBe("FULFILLED");
    const compatSelection = compat();
    const compatQuote = await port.createQuote(compatSelection);
    const compatResult = await port.purchase(
      { quoteId: compatQuote.quoteId, selection: compatSelection },
      "key-q-2",
    );
    expect(compatResult.status).toBe("FULFILLED");
  });

  it("h. 견적은 질문 A, purchase 는 같은 quoteId 에 질문 B 면 422 INVALID_REQUEST 이고 차감하지 않는다", async () => {
    const wallet = createFakeWallet(100);
    const port = createFakeFortunePort({ wallet });
    const quoted = love({ questionKey: "CURRENT_RELATIONSHIP" });
    const { quoteId } = await port.createQuote(quoted);
    const error = await port
      .purchase(
        { quoteId, selection: love({ questionKey: "MARRIAGE" }) },
        "key-q-3",
      )
      .then(
        () => null,
        (e: unknown) => e,
      );
    expect(codeOf(error)).toBe("INVALID_REQUEST");
    expect(wallet.balance()).toBe(100);
  });
});

describe("가짜 운세 priceCurrency (DIRECT-PAY)", () => {
  it("F1. 기본은 견적 charged.currency TURTLE_SHELL, KRW 를 주면 KRW 이고 금액은 같다", async () => {
    const shell = createFakeFortunePort();
    const krw = createFakeFortunePort({ priceCurrency: "KRW" });
    const a = await shell.createQuote((await selectionFor(shell)).selection);
    const b = await krw.createQuote((await selectionFor(krw)).selection);
    expect(a.charged.currency).toBe("TURTLE_SHELL");
    expect(b.charged.currency).toBe("KRW");
    expect(b.charged.amount).toBe(a.charged.amount);
  });
});
