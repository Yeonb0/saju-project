"use client";

// "use client" 이유: 세션 조회(TanStack Query) · 로그인 시작 · 화면 이동(useRouter)은 브라우저에서 한다.
// HOME-01 · PG-2. 근거: A-01 (카카오만), A-02 (로그인 후 returnTo 로 복귀), A-03 (본인 정보 없으면 온보딩).
// 디자인 요소 없음 (PG-FIRST) — 기본 요소의 최소 레이아웃만.
import { useMutation, useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useEffect, useRef } from "react";
import { AppShell } from "@/components/AppShell";
import { SESSION_QUERY_KEY } from "@/lib/auth/RequireSession";
import { onboardingHref } from "@/lib/auth/returnTo";
import { getSessionPort } from "@/lib/ports";
import type { SessionPort } from "@/lib/ports/session";

export function LoginScreen({
  returnTo,
  port,
}: {
  // page.tsx 에서 safeReturnTo 를 거친 값만 받는다.
  returnTo: string;
  // 테스트에서 주입한다. 기본값은 포트 선택(src/lib/ports) — 요청할 때 고른다.
  port?: SessionPort;
}) {
  const router = useRouter();
  const session = () => port ?? getSessionPort();

  // RequireSession 과 같은 키 · 같은 방식
  const current = useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: () => session().getSession(),
    refetchOnMount: "always",
  });

  const login = useMutation({
    mutationFn: () => session().startLogin(returnTo, (url) => router.push(url)),
  });
  // 같은 틱의 두 번째 클릭은 렌더 전이라 isPending 이 아직 false 다 — ref 로 한 번 더 막는다
  const starting = useRef(false);

  // 조회 실패와 로그인 시작 실패는 삼키지 않고 오류 화면(error.tsx)으로
  if (current.error) throw current.error;
  if (login.error) throw login.error;

  // 이 화면에서 새로 받은 값만 믿는다 (캐시에 남은 옛 세션으로 잘못 보내지 않게)
  const state = current.isFetchedAfterMount ? current.data : undefined;
  const target =
    state?.status === "signed_in"
      ? state.hasPrimaryPerson
        ? returnTo
        : onboardingHref(returnTo)
      : null;

  const sent = useRef<string | null>(null);
  useEffect(() => {
    if (target === null || sent.current === target) return;
    sent.current = target;
    router.replace(target);
  }, [target, router]);

  function onStart() {
    if (login.isPending || starting.current) return;
    starting.current = true;
    login.mutate(undefined, {
      onSettled: () => {
        starting.current = false;
      },
    });
  }

  return (
    // header=false: 헤더 없는 화면 (HOME-01 로그인, LAYOUT-FIGMA 195:774)
    <AppShell header={false}>
      <div className="flex flex-col items-center">
        {/* TODO(캐릭터): 뿌기 — PG 심사 후 */}
        <div
          aria-hidden
          data-slot="character"
          className="mt-[202px] h-[70px] w-[60px]"
        />
        {/* NAME (D-11) — 와이어 표기 "사주뿌기" 는 자리표시 (Q-23 j) */}
        <h1 className="mt-[30px] text-[40px]">뿌기사주</h1>
        {/* TODO(PD 문구) */}
        <p
          data-slot="login-intro"
          className="mt-[14px] min-h-[24px] text-[20px]"
        />
        {/* TODO(A-01): 네이버 · 구글 버튼 — BE-B 지원 · 개발 범위 확정 전이라 만들지 않는다 */}
        {/* TODO(BE-B 콜백 동작): OAuth 취소 · 오류 쿼리 처리 — 콜백 동작 확정 전이라 만들지 않는다 */}
        {/* 카카오 로그인 버튼 색 (카카오 디자인 가이드) — TODO(PD 아이콘): 말풍선 심볼 */}
        <button
          type="button"
          onClick={onStart}
          disabled={login.isPending}
          className="mt-[29px] h-[56px] w-[368px] rounded-[10px] bg-[#fee502] text-center text-[20px] text-neutral-900 disabled:opacity-50"
        >
          {/* TODO(PD 문구) */}
          카카오로 시작하기
        </button>
      </div>
    </AppShell>
  );
}
