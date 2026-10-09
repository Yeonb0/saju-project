import { AppShell } from "@/components/AppShell";
import { ROUTES, type RoutePath, SCREENS, type ScreenId } from "@/lib/screens";

// Phase 1 자리표시. 렌더링 방식(클라이언트/서버)은 각 화면 구현 때 CLAUDE.md 렌더링 원칙대로 정한다
export function RoutePlaceholder({ path }: { path: RoutePath }) {
  const route: { screens: readonly ScreenId[]; note?: string } = ROUTES[path];

  return (
    // Phase 1 자리표시 — 제목과 뒤로 대상은 각 화면 구현 때 정한다
    <AppShell title={path} backHref={path === "/" ? undefined : "/"}>
      <p>{path}</p>
      {route.screens.length === 0 ? (
        <p>{route.note} — Figma 프레임 없음</p>
      ) : (
        <ul>
          {route.screens.map((id) => (
            <li key={id}>
              {id} {SCREENS[id].name} ({SCREENS[id].nodeId})
            </li>
          ))}
        </ul>
      )}
    </AppShell>
  );
}
