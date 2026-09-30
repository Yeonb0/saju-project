// PHASES.md Phase 1 (FE) 404 화면 — Next 기본 영어 404 대체. Phase 5 '토큰 1글자 바꾼 URL은 404' 도 이 화면.
import Link from "next/link";
import { AppShell } from "@/components/AppShell";

export default function NotFound() {
  return (
    <AppShell backHref="/">
      {/* TODO(PD 문구) */}
      <p>페이지를 찾을 수 없습니다</p>
      {/* TODO(PD 문구) */}
      <Link href="/">홈으로</Link>
    </AppShell>
  );
}
