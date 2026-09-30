import { QueryClient } from "@tanstack/react-query";

// TODO(Sentry): 전역 onError 는 두지 않는다 (오류를 조용히 삼키지 않게). Sentry 연결은 별도 체크박스.
export function makeQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: 1, // retry 1 · staleTime 30초 — Phase 1 임시값. API 가 생기면 화면별로 조정.
        staleTime: 30_000,
        refetchOnWindowFocus: false, // 결제창 · 카카오톡을 오가며 앱으로 돌아올 때마다 다시 불러오지 않게.
      },
      mutations: {
        retry: 0, // 결제 · 주문 요청이 자동 재전송되면 중복 결제가 날 수 있다. 재시도는 사용자가 직접.
      },
    },
  });
}
