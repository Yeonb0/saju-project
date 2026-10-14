// 결과 생성 대기 · 실패 안내 (Phase 4 LoadingScene — TODAY-01 · FORT-05 · FORT-08 · CSAT-02 의 대기 장면).
// 근거: F-06 (구매 직후 동기 생성 — 대기는 구매 요청이 걸린 동안), COMMON 4.8 (최종 실패는 환급 · 재시도 안내), Q-38 답 (nextAction 이 문의면 문의 안내).
// 캐릭터 · 연출은 PG 심사 요청 후 (PG-FIRST) — 지금은 문구 자리만. 문구는 PD 확정 (FUNCTIONAL_SPEC 8장 v0.4 · Q-40).
import type { ReactNode } from "react";
import { Button } from "@/components/Button";
import { LoadingScene } from "@/components/LoadingScene";
import { BUSINESS_INFO } from "@/lib/business";
import type { GenerationFailure } from "@/lib/reading/generation";

// 구매 완료 (서버 FULFILLED + readingId) — 대기 장면과 같은 자리에 「준비가 끝났어요!」 와 「결과 보러 가기」.
// 문구는 FUNCTIONAL_SPEC 8장 v0.4 "구매 상태 화면" 표의 완료 (VIEW_READING) 줄 (PD 확정). 결과 화면으로는 버튼을 누를 때만 간다 (자동 이동 없음).
export function GenerationDone({ onView }: { onView: () => void }) {
  return (
    <div
      data-slot="generation-done"
      className="flex flex-col items-center pt-[367px]"
    >
      {/* TODO(캐릭터): 와이어 프레임 없음 — 대기 장면과 같은 자리 예정 */}
      <p className="block text-center text-[20px] leading-[normal]">
        준비가 끝났어요!
      </p>
      <div className="mt-[24px]">
        <Button variant="popup" onClick={onView}>
          결과 보러 가기
        </Button>
      </div>
    </div>
  );
}

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
    // 배치: LAYOUT-FIGMA 195:121 (FORT-05 · CSAT-02 동일)
    return (
      <div
        data-slot="generation-wait"
        className="flex flex-col items-center pt-[367px]"
      >
        <LoadingScene
          // COMMON-02
          message={
            <>
              <span className="mt-[26px] block text-center text-[20px] leading-[normal]">
                구매를 처리하고 있어요
              </span>
              <span className="block text-center text-[20px] leading-[normal]">
                잠시만 기다려 주세요. 창을 닫지 말아 주세요.
              </span>
            </>
          }
        >
          {/* TODO(캐릭터): 뿌기 분석 포즈 60×70 (195:126) — PG-FIRST 후 · 가운데 정렬(mx-auto) — output 이 문구 폭이라 그 안에서 가운데 */}
          <span
            aria-hidden
            data-slot="character"
            className="mx-auto block h-[70px] w-[60px]"
          />
        </LoadingScene>
      </div>
    );
  }
  if (failure.next === "contact_support") {
    // 다시 시도를 열지 않는 실패 (Q-38 상태표) — COMMON-04. 환급 문장 · "결과를 만들지 못했어요" 는 보이지 않는다
    return (
      <>
        <p role="alert">확인이 필요한 구매예요</p>
        <p>고객센터로 문의해 주시면 빠르게 도와드릴게요.</p>
        {BUSINESS_INFO.email === null ? null : <p>{BUSINESS_INFO.email}</p>}
        {actions}
      </>
    );
  }
  return (
    <>
      <p role="alert">결과를 만들지 못했어요</p>
      {failure.refunded ? (
        // 서버가 환급을 알려 준 경우에만 (COMMON 4.8)
        <p>사용한 등껍질은 돌려드렸어요. 다시 시도해 주세요.</p>
      ) : null}
      {actions}
    </>
  );
}
