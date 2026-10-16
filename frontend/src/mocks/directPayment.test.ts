import { describe, expect, it } from "vitest";
import { ApiError, ApiNetworkError } from "@/lib/api/errors";
import type { FortuneSelection } from "@/lib/ports/fortune";
import {
  createFakeDirectPaymentPort,
  type FakeDirectPaymentScenario,
} from "./directPayment";
import { createFakeFortunePort, type FakeFortuneScenario } from "./fortune";
import { createFakeWallet } from "./wallet";

// 픽스처일 뿐이며 실제 가격 · 규칙과 무관하다 (DIRECT-PAY, MOCK-PORT). 가짜 운세는 KRW 가격으로 만든다.
const PERSON_ID = "66666666-6666-4666-8666-666666666666";
const TTL_MS = 30 * 60 * 1000;

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

function setup(
  options: {
    scenario?: FakeDirectPaymentScenario;
    fortuneScenario?: FakeFortuneScenario;
    batchLimitKrw?: number;
    now?: () => number;
    priceCurrency?: "KRW" | "TURTLE_SHELL";
    onFulfilled?: () => string;
  } = {},
) {
  const wallet = createFakeWallet(100);
  const fortune = createFakeFortunePort({
    wallet,
    priceCurrency: options.priceCurrency ?? "KRW",
    scenario: options.fortuneScenario,
    now: options.now,
    onFulfilled: options.onFulfilled,
  });
  const port = createFakeDirectPaymentPort({
    fortune,
    scenario: options.scenario,
    batchLimitKrw: options.batchLimitKrw,
  });
  return { wallet, fortune, port };
}

async function quoted(fortune: ReturnType<typeof setup>["fortune"]) {
  const selection = love();
  const quote = await fortune.createQuote(selection);
  return { selection, quote };
}

async function ordered(options: Parameters<typeof setup>[0] = {}) {
  const ctx = setup(options);
  const { selection, quote } = await quoted(ctx.fortune);
  const order = await ctx.port.createOrder({ quoteId: quote.quoteId }, "ok-1");
  const ret = (over: { paymentKey?: string; amount?: string } = {}) => ({
    paymentKey: over.paymentKey ?? "pk-1",
    orderId: order.orderId,
    amount: over.amount ?? String(order.amount.amount),
  });
  return { ...ctx, selection, quote, order, ret };
}

async function errorOf(promise: Promise<unknown>) {
  return promise.then(
    () => null,
    (e: unknown) => e,
  );
}

const codeOf = (error: unknown) =>
  error instanceof ApiError ? error.code : null;

describe("가짜 직접결제 포트 (DIRECT-PAY, MOCK-PORT)", () => {
  it("D1. 주문 금액은 견적 charged.amount, KRW, PAYMENT_PENDING, 이름은 견적 productName", async () => {
    const { port, fortune } = setup();
    const { quote } = await quoted(fortune);
    const order = await port.createOrder({ quoteId: quote.quoteId }, "k-1");
    expect(order.amount).toEqual({
      currency: "KRW",
      amount: quote.charged.amount,
    });
    expect(order.status).toBe("PAYMENT_PENDING");
    expect(order.orderName).toBe(quote.productName);
    expect(order.productCode).toBe(quote.productCode);
  });

  it("D2. 같은 키 + 같은 quoteId 는 같은 orderId, 같은 키 + 다른 quoteId 는 409 IDEMPOTENCY_KEY_REUSED", async () => {
    const { port, fortune } = setup();
    const a = await fortune.createQuote(love());
    const b = await fortune.createQuote(love({ questionKey: "MARRIAGE" }));
    const first = await port.createOrder({ quoteId: a.quoteId }, "k-1");
    const again = await port.createOrder({ quoteId: a.quoteId }, "k-1");
    expect(again.orderId).toBe(first.orderId);
    const error = await errorOf(
      port.createOrder({ quoteId: b.quoteId }, "k-1"),
    );
    expect(codeOf(error)).toBe("IDEMPOTENCY_KEY_REUSED");
  });

  it("D3. 모르는 견적 404 RESOURCE_NOT_FOUND, 만료 견적 409 QUOTE_EXPIRED, TURTLE_SHELL 견적 422 PRODUCT_NOT_AVAILABLE", async () => {
    let at = 1_000_000;
    const { port, fortune } = setup({ now: () => at });
    expect(
      codeOf(await errorOf(port.createOrder({ quoteId: "nope" }, "k-1"))),
    ).toBe("RESOURCE_NOT_FOUND");
    const { quote } = await quoted(fortune);
    at += TTL_MS + 1;
    expect(
      codeOf(
        await errorOf(port.createOrder({ quoteId: quote.quoteId }, "k-2")),
      ),
    ).toBe("QUOTE_EXPIRED");

    const shell = setup({ priceCurrency: "TURTLE_SHELL" });
    const sq = await shell.fortune.createQuote(love());
    expect(
      codeOf(
        await errorOf(shell.port.createOrder({ quoteId: sq.quoteId }, "k-3")),
      ),
    ).toBe("PRODUCT_NOT_AVAILABLE");
  });

  it("D4. batchLimitKrw 가 견적 금액 이하면 400 PAYMENT_BATCH_LIMIT_EXCEEDED, 금액보다 1 크면 통과", async () => {
    const probe = setup();
    const { quote } = await quoted(probe.fortune);
    const amount = quote.charged.amount;

    const at = setup({ batchLimitKrw: amount });
    const q1 = await at.fortune.createQuote(love());
    expect(
      codeOf(
        await errorOf(at.port.createOrder({ quoteId: q1.quoteId }, "k-1")),
      ),
    ).toBe("PAYMENT_BATCH_LIMIT_EXCEEDED");

    const above = setup({ batchLimitKrw: amount + 1 });
    const q2 = await above.fortune.createQuote(love());
    const order = await above.port.createOrder({ quoteId: q2.quoteId }, "k-1");
    expect(order.status).toBe("PAYMENT_PENDING");
  });

  it("D5. paid: confirm → PAID, 같은 paymentKey 로 다시 → PAID, 다른 paymentKey → 409 PAYMENT_CONFIRMATION_CONFLICT", async () => {
    const { port, ret } = await ordered({ scenario: "paid" });
    expect((await port.confirm(ret())).status).toBe("PAID");
    expect((await port.confirm(ret())).status).toBe("PAID");
    const error = await errorOf(port.confirm(ret({ paymentKey: "pk-2" })));
    expect(codeOf(error)).toBe("PAYMENT_CONFIRMATION_CONFLICT");
  });

  it("D6. 금액 다름 409 PAYMENT_AMOUNT_MISMATCH 이고 주문은 PAYMENT_PENDING, '12a' 는 422 INVALID_REQUEST, 모르는 주문 404", async () => {
    const { port, order, ret } = await ordered();
    const wrong = String(order.amount.amount + 1);
    expect(codeOf(await errorOf(port.confirm(ret({ amount: wrong }))))).toBe(
      "PAYMENT_AMOUNT_MISMATCH",
    );
    expect((await port.getOrder(order.orderId)).status).toBe("PAYMENT_PENDING");
    expect(codeOf(await errorOf(port.confirm(ret({ amount: "12a" }))))).toBe(
      "INVALID_REQUEST",
    );
    expect(
      codeOf(
        await errorOf(
          port.confirm({ paymentKey: "pk", orderId: "nope", amount: "1" }),
        ),
      ),
    ).toBe("RESOURCE_NOT_FOUND");
  });

  it("D7. pending_then_paid: confirm → PAYMENT_PENDING, 조회 첫 번째 PAYMENT_PENDING, 2번째 PAID", async () => {
    const { port, order, ret } = await ordered({
      scenario: "pending_then_paid",
    });
    expect((await port.confirm(ret())).status).toBe("PAYMENT_PENDING");
    expect((await port.getOrder(order.orderId)).status).toBe("PAYMENT_PENDING");
    expect((await port.getOrder(order.orderId)).status).toBe("PAID");
  });

  it("D8. stuck_pending: 조회 5번 모두 PAYMENT_PENDING", async () => {
    const { port, order, ret } = await ordered({ scenario: "stuck_pending" });
    await port.confirm(ret());
    for (let i = 0; i < 5; i += 1) {
      expect((await port.getOrder(order.orderId)).status).toBe(
        "PAYMENT_PENDING",
      );
    }
  });

  it("D9. confirm_lost: confirm 은 ApiNetworkError 로 reject, 이어서 조회하면 PAID", async () => {
    const { port, order, ret } = await ordered({ scenario: "confirm_lost" });
    const error = await errorOf(port.confirm(ret()));
    expect(error).toBeInstanceOf(ApiNetworkError);
    expect((await port.getOrder(order.orderId)).status).toBe("PAID");
  });

  it("D10. review_required → REVIEW_REQUIRED, rejected → FAILED, 두 경우 모두 생성은 409 PAYMENT_ORDER_NOT_CONFIRMABLE", async () => {
    for (const [scenario, status] of [
      ["review_required", "REVIEW_REQUIRED"],
      ["rejected", "FAILED"],
    ] as const) {
      const ctx = await ordered({ scenario });
      expect((await ctx.port.confirm(ctx.ret())).status).toBe(status);
      const error = await errorOf(
        ctx.port.fulfillReading(
          {
            orderId: ctx.order.orderId,
            quoteId: ctx.quote.quoteId,
            selection: ctx.selection,
          },
          "f-1",
        ),
      );
      expect(codeOf(error)).toBe("PAYMENT_ORDER_NOT_CONFIRMABLE");
    }
  });

  it("D11. 승인 전 fulfillReading → 409 PAYMENT_ORDER_NOT_CONFIRMABLE", async () => {
    const { port, order, quote, selection } = await ordered();
    const error = await errorOf(
      port.fulfillReading(
        { orderId: order.orderId, quoteId: quote.quoteId, selection },
        "f-1",
      ),
    );
    expect(codeOf(error)).toBe("PAYMENT_ORDER_NOT_CONFIRMABLE");
  });

  it("D12. PAID 뒤 fulfillReading → FULFILLED + readingId (onFulfilled 1회), 같은 키 · 같은 입력은 같은 결과이고 여전히 1회", async () => {
    let made = 0;
    const { port, order, quote, selection, ret } = await ordered({
      onFulfilled: () => {
        made += 1;
        return `reading-${made}`;
      },
    });
    await port.confirm(ret());
    const input = {
      orderId: order.orderId,
      quoteId: quote.quoteId,
      selection,
    };
    const first = await port.fulfillReading(input, "f-1");
    expect(first.status).toBe("FULFILLED");
    expect(first.readingId).toBe("reading-1");
    expect(made).toBe(1);
    const again = await port.fulfillReading(input, "f-1");
    expect(again).toEqual(first);
    expect(made).toBe(1);
  });

  it("D13. 같은 키 + 다른 selection 409 IDEMPOTENCY_KEY_REUSED, 다른 키로 다시 409 PAYMENT_CONFIRMATION_CONFLICT", async () => {
    const { port, order, quote, selection, ret } = await ordered();
    await port.confirm(ret());
    const input = {
      orderId: order.orderId,
      quoteId: quote.quoteId,
      selection,
    };
    await port.fulfillReading(input, "f-1");
    expect(
      codeOf(
        await errorOf(
          port.fulfillReading(
            { ...input, selection: love({ questionKey: "MARRIAGE" }) },
            "f-1",
          ),
        ),
      ),
    ).toBe("IDEMPOTENCY_KEY_REUSED");
    expect(codeOf(await errorOf(port.fulfillReading(input, "f-2")))).toBe(
      "PAYMENT_CONFIRMATION_CONFLICT",
    );
  });

  it("D14. quoteId 가 주문 견적과 다르면 422, selection 이 견적과 다르면(questionKey 하나) 422 INVALID_REQUEST", async () => {
    const { port, fortune, order, quote, selection, ret } = await ordered();
    await port.confirm(ret());
    const other = await fortune.createQuote(love({ questionKey: "MARRIAGE" }));
    expect(
      codeOf(
        await errorOf(
          port.fulfillReading(
            { orderId: order.orderId, quoteId: other.quoteId, selection },
            "f-1",
          ),
        ),
      ),
    ).toBe("INVALID_REQUEST");
    expect(
      codeOf(
        await errorOf(
          port.fulfillReading(
            {
              orderId: order.orderId,
              quoteId: quote.quoteId,
              selection: love({ questionKey: "MARRIAGE" }),
            },
            "f-2",
          ),
        ),
      ),
    ).toBe("INVALID_REQUEST");
  });

  it("D15. 결제 뒤 견적이 만료돼도 fulfillReading 은 FULFILLED", async () => {
    let at = 1_000_000;
    const { port, order, quote, selection, ret } = await ordered({
      now: () => at,
    });
    await port.confirm(ret());
    at += TTL_MS + 1;
    const result = await port.fulfillReading(
      { orderId: order.orderId, quoteId: quote.quoteId, selection },
      "f-1",
    );
    expect(result.status).toBe("FULFILLED");
  });

  it("D16. 가짜 운세 generating: GENERATING · readingId null, 이어서 getPurchaseStatus → FULFILLED · VIEW_READING", async () => {
    const { port, fortune, order, quote, selection, ret } = await ordered({
      fortuneScenario: "generating",
    });
    await port.confirm(ret());
    const result = await port.fulfillReading(
      { orderId: order.orderId, quoteId: quote.quoteId, selection },
      "f-1",
    );
    expect(result.status).toBe("GENERATING");
    expect(result.readingId).toBeNull();
    const state = await fortune.getPurchaseStatus(quote.quoteId);
    expect(state?.status).toBe("FULFILLED");
    expect(state?.nextAction).toBe("VIEW_READING");
    expect(state?.readingId).not.toBeNull();
  });

  it("D17. generation_failed: FAILED · readingId null, getPurchaseStatus → FAILED · NOT_REQUIRED · CONTACT_SUPPORT (RETRY_PURCHASE 아님)", async () => {
    const { port, fortune, order, quote, selection, ret } = await ordered({
      fortuneScenario: "generation_failed",
    });
    await port.confirm(ret());
    const result = await port.fulfillReading(
      { orderId: order.orderId, quoteId: quote.quoteId, selection },
      "f-1",
    );
    expect(result.status).toBe("FAILED");
    expect(result.readingId).toBeNull();
    const state = await fortune.getPurchaseStatus(quote.quoteId);
    expect(state?.status).toBe("FAILED");
    expect(state?.compensationStatus).toBe("NOT_REQUIRED");
    expect(state?.nextAction).toBe("CONTACT_SUPPORT");
  });

  it("D18. processing_409: 첫 fulfillReading 은 409 IDEMPOTENCY_REQUEST_PROCESSING, 같은 키 재요청은 FULFILLED", async () => {
    const { port, order, quote, selection, ret } = await ordered({
      fortuneScenario: "processing_409",
    });
    await port.confirm(ret());
    const input = {
      orderId: order.orderId,
      quoteId: quote.quoteId,
      selection,
    };
    expect(codeOf(await errorOf(port.fulfillReading(input, "f-1")))).toBe(
      "IDEMPOTENCY_REQUEST_PROCESSING",
    );
    expect((await port.fulfillReading(input, "f-1")).status).toBe("FULFILLED");
  });

  it("D19. 지갑 불변: 주문 · 승인 · 생성 전후 balance() 가 같다", async () => {
    const { wallet, port, order, quote, selection, ret } = await ordered();
    const before = wallet.balance();
    await port.confirm(ret());
    await port.fulfillReading(
      { orderId: order.orderId, quoteId: quote.quoteId, selection },
      "f-1",
    );
    expect(wallet.balance()).toBe(before);
    expect(before).toBe(100);
  });
});
