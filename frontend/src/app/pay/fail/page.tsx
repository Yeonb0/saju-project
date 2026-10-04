import Link from "next/link";
import { AppShell } from "@/components/AppShell";

// /pay/fail — 토스 결제 실패 · 취소 복귀 (PG-3). 결제가 일어나지 않았으므로 서버 승인 요청을 보내지 않는다.
// PG 가 붙여 보내는 code · message 는 화면에 쓰지 않는다 — 서버 · PG 원문 노출 금지 (CLAUDE.md 오류 화면).
// 디자인 요소 없음 (PG-FIRST).
export default function Page() {
  return (
    // TODO(PD 문구): 제목
    <AppShell title="충전" backHref="/wallet">
      {/* TODO(PD 문구) */}
      <p role="alert">결제가 완료되지 않았습니다</p>
      {/* TODO(PD 문구) */}
      <Link href="/wallet">충전으로 돌아가기</Link>
    </AppShell>
  );
}
