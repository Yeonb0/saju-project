import Link from "next/link";
import { AppShell } from "@/components/AppShell";
import type { RoutePath } from "@/lib/screens";

// 만들지 않은 화면 — 개발용 ID · Figma 번호를 공개 화면에 내지 않는다 (PG-4, 10/15 화면 점검)
// 렌더링 방식(클라이언트/서버)은 각 화면 구현 때 CLAUDE.md 렌더링 원칙대로 정한다
export function RoutePlaceholder({ path }: { path: RoutePath }) {
  return (
    // 제목과 뒤로 대상은 각 화면 구현 때 정한다. path 는 화면에 쓰지 않고 data-route 에만 둔다
    <AppShell backHref={path === "/" ? undefined : "/"}>
      <div data-route={path}>
        {/* TODO(PD 문구): 만들지 않은 화면 안내 */}
        <p>준비 중이에요</p>
        <Link href="/">홈으로</Link>
      </div>
    </AppShell>
  );
}
