// 구매 선택 복원 (PURCHASE-RESTORE, Q-07 해결, P-06). 잔액 부족 → 충전 → 복귀 때 앞 화면의 선택을 되살린다.
// 규칙 (frontend/CLAUDE.md 브라우저 저장소):
// - sessionStorage 에 인물 ID · 최소 선택값만 둔다. 생년정보 원문 · 이름은 넣지 않는다.
// - 마지막 변경 후 24시간. 읽을 때 만료를 검사하고, 지난 값 · 모양이 다른 값은 지운다.
// - 구매 성공 · 로그아웃 때 지운다 (clearPurchaseSelection).
// - 가격 · 잔액은 저장하지 않는다. 복귀하면 quoteId 로 서버 견적을 다시 받는다 (GET /quotes/{quoteId}).
// - 전역 상태가 아니며 Zustand 를 쓰지 않는다.
// - PURCHASE-KEY-STORE (2026-10-10 결정, 2026-10-12 구현): 구매를 보낸 뒤 결과를 모르는 의도의 Idempotency-Key 와 견적 ID 만 둔다.
//   같은 24시간 · 완료 · 실패 · 로그아웃 때 지운다. 복귀하면 이 키로 상태 조회(Q-38 답 by-quote)부터 하고, 기록이 없을 때만 같은 키로 다시 보낸다 (I-05).
// - v: 4 (질문 선택): productOption · questionKey · relationType 도 함께 둔다 — 복귀 때 같은 질문으로 팝업을 다시 연다. 생년정보 · 이름은 여전히 넣지 않는다.
//   v: 3 · v: 2 는 질문이 없어 되살릴 수 없다 — 읽을 때 지운다 (24시간 만료라 배포 전 값은 하루 안에 사라진다).
// 저장소를 쓸 수 없는 환경(사파리 개인 정보 보호 모드 등)에서는 조용히 저장하지 않는다 — 복원은 편의 기능이다.
import { z } from "zod";
import { safeReturnTo } from "@/lib/auth/returnTo";
import { isQuestionKeyOf } from "@/lib/fortune/questions";
import {
  FORTUNE_TYPES,
  type FortuneSelection,
  PRODUCT_OPTIONS,
  RELATION_TYPES,
} from "@/lib/ports/fortune";

export const PURCHASE_SELECTION_KEY = "purchaseSelection";
export const PURCHASE_SELECTION_TTL_MS = 24 * 60 * 60 * 1000;

export type PurchaseSelection = Readonly<{
  // 돌아갈 앞 화면 (safeReturnTo 를 거친 내부 경로)
  returnPath: string;
  quoteId: string;
  selection: FortuneSelection;
  // 구매를 보낸 뒤 결과를 모르는 의도의 Idempotency-Key. 보낸 적 없으면 null (PURCHASE-KEY-STORE)
  purchaseKey: string | null;
}>;

// 수능운은 질문 값이 모두 null, 그 밖은 옵션 · 질문 키가 있고 (궁합만) 관계 · 상대가 있다. 어긋나면 읽을 때 지운다
function selectionShapeOk(selection: {
  fortuneType: (typeof FORTUNE_TYPES)[number];
  counterpartPersonId: string | null;
  productOption: string | null;
  questionKey: string | null;
  relationType: string | null;
}): boolean {
  if (selection.fortuneType === "SUNEUNG") {
    return (
      selection.productOption === null &&
      selection.questionKey === null &&
      selection.relationType === null &&
      selection.counterpartPersonId === null
    );
  }
  if (
    selection.productOption === null ||
    selection.questionKey === null ||
    !isQuestionKeyOf(selection.fortuneType, selection.questionKey)
  ) {
    return false;
  }
  if (selection.fortuneType === "COMPATIBILITY") {
    return (
      selection.relationType !== null && selection.counterpartPersonId !== null
    );
  }
  return selection.relationType === null;
}

const storedSchema = z.object({
  v: z.literal(4),
  savedAt: z.number().int().nonnegative(),
  returnPath: z.string(),
  quoteId: z.string().min(1),
  selection: z
    .object({
      // UNKNOWN 은 허용하지 않는다
      fortuneType: z.enum(FORTUNE_TYPES),
      productCode: z.string().min(1),
      personId: z.string().min(1),
      counterpartPersonId: z.string().min(1).nullable(),
      productOption: z.enum(PRODUCT_OPTIONS).nullable(),
      questionKey: z.string().min(1).nullable(),
      relationType: z.enum(RELATION_TYPES).nullable(),
    })
    .refine(selectionShapeOk, { message: "질문 선택 모양이 다르다" }),
  purchaseKey: z.string().min(1).nullable(),
});

function storage(): Storage | null {
  try {
    return typeof window === "undefined" ? null : window.sessionStorage;
  } catch {
    return null;
  }
}

export function savePurchaseSelection(
  // purchaseKey 를 생략하면 null
  value: Omit<PurchaseSelection, "purchaseKey"> & {
    purchaseKey?: string | null;
  },
  now: number = Date.now(),
): void {
  const store = storage();
  if (!store) return;
  // 필요한 값만 골라 담는다 — 호출하는 쪽이 다른 값을 섞어 넘겨도 저장되지 않게
  const stored: z.infer<typeof storedSchema> = {
    v: 4,
    savedAt: now,
    returnPath: safeReturnTo(value.returnPath),
    quoteId: value.quoteId,
    selection: {
      fortuneType: value.selection.fortuneType,
      productCode: value.selection.productCode,
      personId: value.selection.personId,
      counterpartPersonId: value.selection.counterpartPersonId,
      productOption: value.selection.productOption,
      questionKey: value.selection.questionKey,
      relationType: value.selection.relationType,
    },
    purchaseKey: value.purchaseKey ?? null,
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
  return {
    returnPath: safeReturnTo(returnPath),
    quoteId,
    selection,
    purchaseKey: result.data.purchaseKey,
  };
}

export function clearPurchaseSelection(): void {
  try {
    storage()?.removeItem(PURCHASE_SELECTION_KEY);
  } catch {
    // 지우지 못해도 24시간 만료 검사가 남은 값을 막는다
  }
}
