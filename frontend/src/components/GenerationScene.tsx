// 결과 생성 대기 · 실패 안내 (Phase 4 LoadingScene — TODAY-01 · FORT-05 · FORT-08 · CSAT-02 의 대기 장면).
// 근거: F-06 (구매 직후 동기 생성 — 대기는 구매 요청이 걸린 동안), COMMON 4.8 (최종 실패는 환급 · 재시도 안내).
// 캐릭터 · 연출은 PG 심사 요청 후 (PG-FIRST) — 지금은 문구 자리만. 문구는 전부 TODO(PD 문구).
import type { ReactNode } from "react";
import { LoadingScene } from "@/components/LoadingScene";
import type { GenerationFailure } from "@/lib/reading/generation";

export function GenerationScene({
  failure,
  actions,
}: {
  // null 이면 생성 대기
  failure: GenerationFailure | null;
  // 실패일 때 앞 화면이 주는 버튼 (다시 시도 · 닫기)
  actions?: ReactNode;
}) {
  if (failure === null) {
    // TODO(PD 문구) · TODO(캐릭터): 뿌기 부적 배달 포즈 (PG-FIRST 후)
    return <LoadingScene message="결과를 만들고 있습니다" />;
  }
  return (
    <>
      {/* TODO(PD 문구) */}
      <p role="alert">결과를 만들지 못했습니다</p>
      {failure.refunded ? (
        // 서버가 환급을 알려 준 경우에만 (COMMON 4.8). TODO(PD 문구)
        <p>사용한 등껍질은 돌려드렸습니다</p>
      ) : null}
      {actions}
    </>
  );
}
