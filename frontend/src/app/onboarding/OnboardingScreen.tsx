"use client";

// "use client" 이유: 세션 조회 · 저장(TanStack Query) · 화면 이동(useRouter)은 브라우저에서 한다.
// HOME-02 · A-03. 본인 정보를 저장하면 returnTo 로 돌아간다. 디자인 요소 없음 (PG-FIRST).
// 중복 경고(서버 응답 모양 미정) · 수정(PATCH) 은 만들지 않는다.
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef } from "react";
import { AppShell } from "@/components/AppShell";
import { PersonForm } from "@/components/PersonForm";
import { ApiContractError, classifyApiError } from "@/lib/api/errors";
import { SESSION_QUERY_KEY } from "@/lib/auth/RequireSession";
import { loginHref } from "@/lib/auth/returnTo";
import type { PersonFormOutput } from "@/lib/person/schema";
import { getPersonPort, getSessionPort } from "@/lib/ports";
import type { PersonPort } from "@/lib/ports/person";
import type { SessionPort } from "@/lib/ports/session";

export function OnboardingScreen({
  returnTo,
  personPort,
  sessionPort,
}: {
  // page.tsx 에서 safeReturnTo 를 거친 값만 받는다.
  returnTo: string;
  // 테스트에서 주입한다. 기본값은 포트 선택(src/lib/ports) — 요청할 때 고른다.
  personPort?: PersonPort;
  sessionPort?: SessionPort;
}) {
  const router = useRouter();
  const queryClient = useQueryClient();

  // RequireSession 과 같은 키 · 같은 방식
  const session = useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: () => (sessionPort ?? getSessionPort()).getSession(),
    refetchOnMount: "always",
  });

  const save = useMutation({
    mutationFn: (value: PersonFormOutput) =>
      (personPort ?? getPersonPort()).createSelf(value),
    onSuccess: () => {
      // 옛 세션(hasPrimaryPerson false)이 남아 되돌려 보내지 않게 캐시를 지운다
      queryClient.removeQueries({ queryKey: SESSION_QUERY_KEY });
      router.replace(returnTo);
    },
  });

  // 조회 실패와 응답 계약 위반은 삼키지 않고 오류 화면(error.tsx)으로
  if (session.error) throw session.error;
  if (save.error instanceof ApiContractError) throw save.error;

  // 이 화면에서 새로 받은 값만 믿는다
  const state = session.isFetchedAfterMount ? session.data : undefined;
  const alreadyHasPerson =
    state?.status === "signed_in" && state.hasPrimaryPerson;

  const sent = useRef(false);
  useEffect(() => {
    if (!alreadyHasPerson || sent.current) return;
    sent.current = true;
    router.replace(returnTo);
  }, [alreadyHasPerson, returnTo, router]);

  const kind = save.error ? classifyApiError(save.error) : null;

  return (
    // TODO(PD 문구): 제목
    <AppShell title="정보 입력" backHref="/">
      {state === undefined || alreadyHasPerson ? null : (
        <>
          {/* TODO(PD 문구) */}
          <p
            data-slot="person-intro"
            className="mt-[59px] ml-[37px] min-h-[24px] text-[20px] font-semibold"
          />
          <PersonForm
            submitting={save.isPending}
            // mutate 는 던지지 않는다 — 오류는 save.error 로 화면에 보이고, 제출은 onSettled 에서 끝난다
            onSubmit={(value) =>
              new Promise<void>((resolve) => {
                save.mutate(value, { onSettled: () => resolve() });
              })
            }
          />
          {kind === "login_required" || kind === "csrf_failed" ? (
            // TODO(PD 문구)
            <Link href={loginHref(returnTo)}>다시 로그인해 주세요</Link>
          ) : kind !== null ? (
            // TODO(PD 문구)
            <p role="alert">저장하지 못했습니다</p>
          ) : null}
        </>
      )}
    </AppShell>
  );
}
