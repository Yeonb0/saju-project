"use client";

// "use client" 이유: QueryClientProvider 는 클라이언트 컨텍스트다.
import { QueryClientProvider } from "@tanstack/react-query";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";
import { type ReactNode, useState } from "react";
import { makeQueryClient } from "@/lib/queryClient";

export function Providers({ children }: { children: ReactNode }) {
  // useState 초기화 함수로 한 번만 만든다 — 렌더마다 새로 만들면 캐시가 날아간다.
  const [queryClient] = useState(() => makeQueryClient());

  return (
    <QueryClientProvider client={queryClient}>
      {children}
      {/* 프로덕션 빌드에서는 라이브러리가 Devtools 를 빼 준다. */}
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  );
}
