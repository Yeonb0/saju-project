"use client";

// "use client" 이유: 결과 조회(TanStack Query)는 브라우저에서 한다.
// 결과 재열람 화면 — CSAT-03 ~ 06 (/suneung/r/[readingId]) · FORT-06 · 07 (/fortune/r/[readingId]). VIEWER · F-05.
// 없는 결과 · 남의 결과(404 · 403)는 상세 없이 not-found 화면 (결제 · 차감 없이 결과 URL 을 열면 403/404 — Phase 4 완료 기준).
// 로그인 가드는 page.tsx 의 RequireSession. 디자인 요소 없음 (PG-FIRST).
import { useQuery } from "@tanstack/react-query";
import NotFound from "@/app/not-found";
import { AppShell } from "@/components/AppShell";
import { LoadingScene } from "@/components/LoadingScene";
import { ReadingViewer } from "@/components/ReadingViewer";
import { classifyApiError } from "@/lib/api/errors";
import { getReadingPort } from "@/lib/ports";
import type { ReadingPort } from "@/lib/ports/reading";

export function ReadingScreen({
  readingId,
  title,
  port,
}: {
  readingId: string;
  title: string;
  // 테스트에서 주입한다. 기본값은 포트 선택(src/lib/ports) — 요청할 때 고른다.
  port?: ReadingPort;
}) {
  const reading = useQuery({
    queryKey: ["reading", readingId],
    queryFn: () => (port ?? getReadingPort()).getReading(readingId),
    // 결과는 스냅샷이라 다시 받을 일이 없다 (F-05). 404 · 403 을 다시 시도하지 않는다
    retry: false,
    staleTime: Number.POSITIVE_INFINITY,
  });

  if (reading.error) {
    const kind = classifyApiError(reading.error);
    if (kind === "not_found" || kind === "forbidden") return <NotFound />;
    // 그 밖의 실패는 삼키지 않고 오류 화면(error.tsx)으로
    throw reading.error;
  }

  return (
    <AppShell title={title} backHref="/">
      {reading.data ? (
        <ReadingViewer reading={reading.data} />
      ) : (
        // TODO(PD 문구)
        <LoadingScene message="불러오고 있습니다" />
      )}
    </AppShell>
  );
}
