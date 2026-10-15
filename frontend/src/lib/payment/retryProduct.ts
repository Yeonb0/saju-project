// 결제 실패 · 취소 뒤 다시 결제하기가 고른 상품을 유지하는 주소 값 (TOPUP-RETRY-URL, 2026-10-14, FUNCTIONAL_SPEC 7장 PAY-05).
// 토스 failUrl 에만 ?product=상품코드 를 붙이고 /wallet?product=상품코드 로 돌아온다 — 저장소는 쓰지 않는다.
// 모양만 본다. 판매 여부는 충전 화면이 서버 상품 목록으로 본다 (상품 코드 목록을 FE 에 두지 않는다).
const RETRY_PRODUCT_PATTERN = /^[A-Z0-9_]{1,64}$/;

export function readRetryProduct(
  value: string | string[] | undefined,
): string | null {
  if (typeof value !== "string") return null;
  return RETRY_PRODUCT_PATTERN.test(value) ? value : null;
}
