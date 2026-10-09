import Link from "next/link";
import {
  PAY_ACTION_PRIMARY,
  PAY_ACTION_SECONDARY,
  PayResultLayout,
} from "../PayResultLayout";

// /pay/fail — 토스 결제 실패 · 취소 복귀 (PG-3). 결제가 일어나지 않았으므로 서버 승인 요청을 보내지 않는다.
// PG 가 붙여 보내는 code · message 는 화면에 쓰지 않는다 — 서버 · PG 원문 노출 금지 (CLAUDE.md 오류 화면).
// 디자인 요소 없음 (PG-FIRST). 배치는 LAYOUT-FIGMA PAY-05 (301:190).
export default function Page() {
  return (
    <PayResultLayout
      variant="failed"
      // TODO(PD 문구)
      title={<p role="alert">결제가 완료되지 않았습니다</p>}
      // TODO(PD 문구 · Q-33): 취소 · 실패 구분 문구 — PG 가 붙인 code · message 는 화면에 쓰지 않는다
      sub={null}
      // TODO(PD 문구): 미지급 · 미청구 안내
      box={<p data-slot="pay-result-note" />}
      actions={
        <>
          {/* TODO(PD 메모 301:201): 고른 상품 유지 — 저장 방식 미정이라 충전 화면 처음으로 간다. TODO(PD 문구) */}
          <Link href="/wallet" className={PAY_ACTION_PRIMARY}>
            충전으로 돌아가기
          </Link>
          {/* TODO(PD 문구) */}
          <Link href="/" className={PAY_ACTION_SECONDARY}>
            홈으로
          </Link>
        </>
      }
    />
  );
}
