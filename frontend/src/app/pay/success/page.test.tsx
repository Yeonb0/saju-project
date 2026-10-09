import { describe, expect, it } from "vitest";
import Page from "./page";

// 픽스처일 뿐이며 실제 결제 키 · 금액 · 규칙과 무관하다.
const ORDER_ID = "33333333-3333-4333-8333-333333333333";

function props(query: Record<string, string | string[]>) {
  return {
    params: Promise.resolve({}),
    searchParams: Promise.resolve(query),
  } as PageProps<"/pay/success">;
}

describe("/pay/success page", () => {
  it("복귀 값이 하나라도 없으면 승인 없이 not-found", async () => {
    await expect(
      Page(props({ orderId: ORDER_ID, amount: "1" })),
    ).rejects.toThrow();
  });

  it("같은 이름이 여러 번 오면 그 값은 쓰지 않는다 (not-found)", async () => {
    await expect(
      Page(props({ paymentKey: "pk", orderId: ORDER_ID, amount: ["1", "2"] })),
    ).rejects.toThrow();
  });

  it("세 값이 있으면 그대로 TopUpSuccess 에 넘긴다", async () => {
    const element = await Page(
      props({ paymentKey: "pk", orderId: ORDER_ID, amount: "05000" }),
    );
    expect(element.props.ret).toEqual({
      paymentKey: "pk",
      orderId: ORDER_ID,
      amount: "05000",
    });
  });
});
