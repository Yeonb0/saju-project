import { describe, expect, it } from "vitest";
import { DirectPaymentReturn } from "./DirectPaymentReturn";
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

  it("S1. flow=direct 면 DirectPaymentReturn 에 복귀 값을 그대로 넘긴다", async () => {
    const element = await Page(
      props({
        paymentKey: "pk",
        orderId: ORDER_ID,
        amount: "5000",
        flow: "direct",
      }),
    );
    expect(element.type).toBe(DirectPaymentReturn);
    expect(element.props.ret).toEqual({
      paymentKey: "pk",
      orderId: ORDER_ID,
      amount: "5000",
    });
  });

  it("P1. flow 가 없으면(복귀 값 세 개가 정상이어도) not-found — 승인 요청 · 화면 없음 (충전 복귀는 닫았다, DIRECT-PAY)", async () => {
    await expect(
      Page(props({ paymentKey: "pk", orderId: ORDER_ID, amount: "5000" })),
    ).rejects.toThrow();
  });

  it.each([
    ["다른 값", "other"],
    ["여러 번", ["direct", "direct"]],
  ])("S3. flow %s 면 not-found", async (_name, flow) => {
    await expect(
      Page(
        props({ paymentKey: "pk", orderId: ORDER_ID, amount: "5000", flow }),
      ),
    ).rejects.toThrow();
  });
});
