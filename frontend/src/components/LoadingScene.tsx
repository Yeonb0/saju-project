import type { ReactNode } from "react";

// Phase 4 에서 캐릭터 1~2컷 + 문구로 완성. 대기 시간 · 문구는 여기서 정하지 않는다
export function LoadingScene({
  message,
  children,
}: {
  message?: ReactNode;
  children?: ReactNode; // 캐릭터 자리
}) {
  return (
    // <output> 안에는 인라인(phrasing) 요소만 둔다 — 문구는 span, 캐릭터는 img · svg 로 넣는다
    <output aria-live="polite">
      {children}
      {message ? <span className="block">{message}</span> : null}
    </output>
  );
}
