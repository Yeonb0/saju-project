"use client";

// "use client" 이유: Next 규약 — global-error 는 클라이언트 컴포넌트여야 하고, useEffect 로 Sentry 에 보낸다.
import * as Sentry from "@sentry/nextjs";
import { useEffect } from "react";
import { Button } from "@/components/Button";

export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    Sentry.captureException(error);
  }, [error]);

  return (
    <html lang="ko">
      <body>
        <h1>오류가 발생했습니다</h1> {/* TODO(PD 문구) */}
        <Button onClick={reset}>다시 시도</Button> {/* TODO(PD 문구) */}
      </body>
    </html>
  );
}
