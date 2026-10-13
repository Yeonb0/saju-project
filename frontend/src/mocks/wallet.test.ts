import { describe, expect, it } from "vitest";
import { createFakeWallet } from "./wallet";

// 픽스처일 뿐이며 실제 잔액 · 차감 규칙과 무관하다.
describe("가짜 지갑", () => {
  it("a. credit 은 유료와 보너스를 따로 더하고 balance 는 둘의 합이다", () => {
    const wallet = createFakeWallet(10, 2);
    wallet.credit(22, 3);
    expect(wallet.paid()).toBe(32);
    expect(wallet.bonus()).toBe(5);
    expect(wallet.balance()).toBe(37);
  });

  it("b. debit 은 보너스를 먼저 빼고 뺀 양을 돌려준다", () => {
    const wallet = createFakeWallet(10, 3);
    expect(wallet.debit(5)).toEqual({ paid: 2, bonus: 3 });
    expect(wallet.paid()).toBe(8);
    expect(wallet.bonus()).toBe(0);
  });

  it("c. 잔액이 모자라면 null 이고 아무것도 바꾸지 않는다", () => {
    const wallet = createFakeWallet(1, 1);
    expect(wallet.debit(3)).toBeNull();
    expect(wallet.paid()).toBe(1);
    expect(wallet.bonus()).toBe(1);
  });
});
