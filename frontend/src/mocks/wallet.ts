// 가짜 지갑 (개발 서버 · 미리보기 전용, MOCK-PORT). 가짜 충전과 가짜 운세 구매가 같은 잔액을 본다 —
// 잔액 부족 → 충전 → 복귀 흐름(P-06 · PURCHASE-RESTORE)을 가짜로도 끝까지 눌러 볼 수 있게.
// 상태는 같은 탭 메모리에만 둔다 — 새로고침하면 처음 잔액으로 돌아간다.

// 픽스처일 뿐이며 실제 잔액 · 규칙과 무관하다.
export const FIXTURE_BALANCE = 7;

export type FakeWallet = {
  balance(): number;
  credit(amount: number): void;
  // 잔액이 모자라면 바꾸지 않고 false
  debit(amount: number): boolean;
};

export function createFakeWallet(initial = FIXTURE_BALANCE): FakeWallet {
  let balance = initial;
  return {
    balance: () => balance,
    credit(amount) {
      balance += amount;
    },
    debit(amount) {
      if (balance < amount) return false;
      balance -= amount;
      return true;
    },
  };
}
