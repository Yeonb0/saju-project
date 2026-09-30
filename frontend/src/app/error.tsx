"use client";

// "use client" 이유: Next 규약 — error 파일은 클라이언트 컴포넌트여야 하고, useEffect 로 Sentry 에 보낸다.
// PHASES.md Phase 1 (FE) 라우트 오류 화면 — 화면 안 오류가 global-error 까지 올라가
// AppShell(뒤로·메뉴)이 사라지는 것을 막는다. 결제·결과 경로의 '시끄럽게 실패'가 던질 자리.
import * as Sentry from "@sentry/nextjs";
import { useEffect } from "react";
import { AppShell } from "@/components/AppShell";
import { Button } from "@/components/Button";

// Next 16.3 error.md — retry 는 다시 가져와 다시 그린다 (reset 은 다시 가져오지 않음)
export default function RouteError({
  error,
  retry,
}: {
  error: Error & { digest?: string };
  retry: () => void;
}) {
  useEffect(() => {
    Sentry.captureException(error);
  }, [error]);

  return (
    <AppShell backHref="/">
      {/* TODO(PD 문구) */}
      <p>오류가 발생했습니다</p>
      {/* TODO(PD 문구) */}
      <Button onClick={() => retry()}>다시 시도</Button>
    </AppShell>
  );
}
