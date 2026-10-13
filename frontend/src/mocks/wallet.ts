// 가짜 지갑 (개발 서버 · 미리보기 전용, MOCK-PORT). 가짜 충전과 가짜 운세 구매가 같은 잔액을 본다 —
// 잔액 부족 → 충전 → 복귀 흐름(P-06 · PURCHASE-RESTORE)을 가짜로도 끝까지 눌러 볼 수 있게.
// 상태는 같은 탭 메모리에만 둔다 — 새로고침하면 처음 잔액으로 돌아간다.

// 픽스처일 뿐이며 실제 잔액 · 규칙과 무관하다.
export const FIXTURE_BALANCE = 7;

export type FakeWallet = {
  // 유료 + 보너스 (가짜 서버 안의 계산 — 화면은 합산하지 않는다)
  balance(): number;
  paid(): number;
  bonus(): number;
  credit(paid: number, bonus?: number): void;
  // 모자라면 바꾸지 않고 null, 있으면 뺀 양을 돌려준다 (되돌릴 때 그대로 credit)
  // 실제 차감 순서는 서버가 만료 빠른 순, 가짜는 만료일이 없어 보너스 먼저
  debit(amount: number): { paid: number; bonus: number } | null;
};

export function createFakeWallet(
  initialPaid = FIXTURE_BALANCE,
  initialBonus = 0,
): FakeWallet {
  let paid = initialPaid;
  let bonus = initialBonus;
  return {
    balance: () => paid + bonus,
    paid: () => paid,
    bonus: () => bonus,
    credit(paidAmount, bonusAmount = 0) {
      paid += paidAmount;
      bonus += bonusAmount;
    },
    debit(amount) {
      if (paid + bonus < amount) return null;
      const fromBonus = Math.min(bonus, amount);
      const fromPaid = amount - fromBonus;
      bonus -= fromBonus;
      paid -= fromPaid;
      return { paid: fromPaid, bonus: fromBonus };
    },
  };
}
