// 구매 선택 복원 (PURCHASE-RESTORE, Q-07 해결, P-06). 잔액 부족 → 충전 → 복귀 때 앞 화면의 선택을 되살린다.
// 규칙 (frontend/CLAUDE.md 브라우저 저장소):
// - sessionStorage 에 인물 ID · 최소 선택값만 둔다. 생년정보 원문 · 이름은 넣지 않는다.
// - 마지막 변경 후 24시간. 읽을 때 만료를 검사하고, 지난 값 · 모양이 다른 값은 지운다.
// - 구매 성공 · 로그아웃 때 지운다 (clearPurchaseSelection).
// - 가격 · 잔액은 저장하지 않는다. 복귀하면 quoteId 로 서버 견적을 다시 받는다 (GET /quotes/{quoteId}).
// - 전역 상태가 아니며 Zustand 를 쓰지 않는다.
// 저장소를 쓸 수 없는 환경(사파리 개인 정보 보호 모드 등)에서는 조용히 저장하지 않는다 — 복원은 편의 기능이다.
import { z } from "zod";
import { safeReturnTo } from "@/lib/auth/returnTo";
import type { FortuneSelection } from "@/lib/ports/fortune";

export const PURCHASE_SELECTION_KEY = "purchaseSelection";
export const PURCHASE_SELECTION_TTL_MS = 24 * 60 * 60 * 1000;

export type PurchaseSelection = Readonly<{
  // 돌아갈 앞 화면 (safeReturnTo 를 거친 내부 경로)
  returnPath: string;
  quoteId: string;
  selection: FortuneSelection;
}>;

const storedSchema = z.object({
  v: z.literal(1),
  savedAt: z.number().int().nonnegative(),
  returnPath: z.string(),
  quoteId: z.string().min(1),
  selection: z.object({
    productCode: z.string().min(1),
    personId: z.string().min(1),
    counterpartPersonId: z.string().min(1).nullable(),
  }),
});

function storage(): Storage | null {
  try {
    return typeof window === "undefined" ? null : window.sessionStorage;
  } catch {
    return null;
  }
}

export function savePurchaseSelection(
  value: PurchaseSelection,
  now: number = Date.now(),
): void {
  const store = storage();
  if (!store) return;
  // 필요한 값만 골라 담는다 — 호출하는 쪽이 다른 값을 섞어 넘겨도 저장되지 않게
  const stored: z.infer<typeof storedSchema> = {
    v: 1,
    savedAt: now,
    returnPath: safeReturnTo(value.returnPath),
    quoteId: value.quoteId,
    selection: {
      productCode: value.selection.productCode,
      personId: value.selection.personId,
      counterpartPersonId: value.selection.counterpartPersonId,
    },
  };
  try {
    store.setItem(PURCHASE_SELECTION_KEY, JSON.stringify(stored));
  } catch {
    // 저장소가 가득 찼거나 막혔다 — 복원 없이 진행한다
  }
}

export function loadPurchaseSelection(
  now: number = Date.now(),
): PurchaseSelection | null {
  const store = storage();
  if (!store) return null;
  let raw: string | null;
  try {
    raw = store.getItem(PURCHASE_SELECTION_KEY);
  } catch {
    return null;
  }
  if (raw === null) return null;

  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    clearPurchaseSelection();
    return null;
  }
  const result = storedSchema.safeParse(parsed);
  if (
    !result.success ||
    now - result.data.savedAt >= PURCHASE_SELECTION_TTL_MS ||
    result.data.savedAt > now
  ) {
    clearPurchaseSelection();
    return null;
  }
  const { returnPath, quoteId, selection } = result.data;
  return { returnPath: safeReturnTo(returnPath), quoteId, selection };
}

export function clearPurchaseSelection(): void {
  try {
    storage()?.removeItem(PURCHASE_SELECTION_KEY);
  } catch {
    // 지우지 못해도 24시간 만료 검사가 남은 값을 막는다
  }
}
