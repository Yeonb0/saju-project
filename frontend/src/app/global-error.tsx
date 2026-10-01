"use client";

// "use client" 이유: Next 규약 — global-error 는 클라이언트 컴포넌트여야 하고, useEffect 로 Sentry 에 보낸다.
import * as Sentry from "@sentry/nextjs";
import { useEffect } from "react";
import { Button } from "@/components/Button";

// Next 16.3 error.md — retry 는 다시 가져와 다시 그린다 (reset 은 다시 가져오지 않음). app/error.tsx 와 같게
export default function GlobalError({
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
    <html lang="ko">
      <body>
        <h1>오류가 발생했습니다</h1> {/* TODO(PD 문구) */}
        <Button onClick={() => retry()}>다시 시도</Button> {/* TODO(PD 문구) */}
      </body>
    </html>
  );
}
