"use client";

// "use client" 이유: 세션 조회(TanStack Query) · 현재 주소(window.location) · 화면 이동(useRouter)은 브라우저에서 한다.
// PG-2 · 근거: A-02 (로그인 필요한 화면은 로그인 화면으로), A-03 (본인 정보가 없으면 온보딩으로).
// returnTo 는 loginHref · onboardingHref 가 safeReturnTo 를 거쳐 만든다.
// 판단 전에는 아무것도 그리지 않는다 (화면 문구 없음).
import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { type ReactNode, useEffect, useRef } from "react";
import { getSessionPort } from "@/lib/ports";
import type { SessionPort } from "@/lib/ports/session";
import { loginHref, onboardingHref } from "./returnTo";

export const SESSION_QUERY_KEY = ["session"] as const;

type Redirect = "login" | "onboarding";

export function RequireSession({
  children,
  requirePerson = true,
  port,
}: {
  children: ReactNode;
  requirePerson?: boolean;
  // 테스트에서 주입한다. 기본값은 포트 선택(src/lib/ports).
  // 포트는 렌더 중이 아니라 요청할 때 고른다: 진짜 모드 빌드의 정적 렌더가 멈추지 않게.
  port?: SessionPort;
}) {
  const router = useRouter();
  const session = useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: () => (port ?? getSessionPort()).getSession(),
    refetchOnMount: "always",
  });

  // 조회 실패는 삼키지 않고 오류 화면(error.tsx)으로
  if (session.error) throw session.error;

  // 로그인 · 로그아웃 직후 캐시에 남은 옛 세션으로 잘못 보내지 않게, 이 화면에서 새로 받은 값만 믿는다
  const state = session.isFetchedAfterMount ? session.data : undefined;

  let redirect: Redirect | null = null;
  if (state?.status === "signed_out") redirect = "login";
  else if (
    state?.status === "signed_in" &&
    requirePerson &&
    !state.hasPrimaryPerson
  ) {
    redirect = "onboarding";
  }

  // 같은 결정으로 replace 를 두 번 부르지 않는다
  const sent = useRef<Redirect | null>(null);
  useEffect(() => {
    if (redirect === null || sent.current === redirect) return;
    sent.current = redirect;
    // 현재 주소는 렌더 중이 아니라 effect 안에서 읽는다
    const here = window.location.pathname + window.location.search;
    router.replace(
      redirect === "login" ? loginHref(here) : onboardingHref(here),
    );
  }, [redirect, router]);

  if (state === undefined || redirect !== null) return null;
  return <>{children}</>;
}
