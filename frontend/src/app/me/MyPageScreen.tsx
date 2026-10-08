"use client";

// "use client" 이유: 세션 · 인물 조회(TanStack Query), 로그아웃 명령과 화면 이동(useRouter)은 브라우저에서 한다.
// MY-01 마이페이지 (docs/FRONTEND.md 3장). 근거: A-02 · A-03 (로그인 가드는 page.tsx 의 RequireSession), PG-2 (로그아웃),
// PURCHASE-RESTORE (로그아웃 때 구매 선택을 지운다 — logout()), MOCK-PORT (포트는 렌더 중이 아니라 요청할 때 고른다),
// LAYOUT-FIGMA (MY-01 195:145 — 배치는 와이어 값). 내 운세 기록 · 충전 내역 · 회원탈퇴는 경로 · API 가 없어 만들지 않는다.
import { useMutation, useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef } from "react";
import { AppShell } from "@/components/AppShell";
import { PersonCard } from "@/components/PersonCard";
import { classifyApiError } from "@/lib/api/errors";
import { logout } from "@/lib/auth/logout";
import { SESSION_QUERY_KEY } from "@/lib/auth/RequireSession";
import { getPersonPort, getSessionPort } from "@/lib/ports";
import type { PersonPort } from "@/lib/ports/person";
import type { SessionPort } from "@/lib/ports/session";

// TODO(PD 토큰 v0): 와이어 임시값 (LAYOUT-FIGMA)
const HEADING_CLASS = "text-[17px] font-medium";
const BOX_CLASS = "h-[81px] w-[351px] bg-[#d9d9d9]";

export function MyPageScreen({
  sessionPort,
  personPort,
}: {
  // 테스트에서 주입한다. 기본값은 포트 선택(src/lib/ports) — 요청할 때 고른다 (MOCK-PORT).
  sessionPort?: SessionPort;
  personPort?: PersonPort;
}) {
  const router = useRouter();

  const session = useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: () => (sessionPort ?? getSessionPort()).getSession(),
  });
  const people = useQuery({
    queryKey: ["people"],
    queryFn: () => (personPort ?? getPersonPort()).list(),
  });

  const leave = useMutation({
    mutationFn: () => logout(sessionPort ?? getSessionPort()),
    onSuccess: () => {
      // 세션 캐시는 무효화하지 않는다 — 무효화하면 이동 전에 RequireSession 이 로그아웃 상태를 보고 /login 으로 보낸다. RequireSession 은 들어올 때마다 새로 받은 세션만 믿는다
      router.replace("/");
    },
    onError: (error) => {
      // 이미 로그아웃 상태면 그대로 처음 화면으로 (message 문자열은 보지 않는다)
      if (classifyApiError(error) === "login_required") router.replace("/");
    },
  });
  // 같은 틱의 두 번째 클릭은 렌더 전이라 isPending 이 아직 false 다 — ref 로 한 번 더 막는다
  const leaving = useRef(false);

  // 시끄럽게 실패: 조회 실패는 오류 화면(error.tsx)으로
  if (session.error) throw session.error;
  if (people.error) throw people.error;

  function onLogout() {
    if (leave.isPending || leaving.current) return;
    leaving.current = true;
    leave.mutate(undefined, {
      onSettled: () => {
        leaving.current = false;
      },
    });
  }

  const nickname =
    session.data?.status === "signed_in" ? session.data.nickname : null;
  const failed =
    leave.error !== null && classifyApiError(leave.error) !== "login_required";

  return (
    // TODO(PD 문구)
    <AppShell title="마이페이지" backHref="/">
      <div className="mx-[25px] pb-[24px]">
        <section>
          {/* TODO(PD 문구) */}
          <h2 className={`mt-[40px] ${HEADING_CLASS}`}>로그인한 계정</h2>
          {/* TODO(PD 문구): 닉네임 없음 */}
          <div
            data-slot="account"
            className={`mt-[7px] flex items-center justify-center text-[13px] font-semibold ${BOX_CLASS}`}
          >
            {nickname}
          </div>
          <div className="mt-[9px] flex w-[351px] justify-end">
            {/* TODO(PD 문구) */}
            <button
              type="button"
              onClick={onLogout}
              disabled={leave.isPending}
              className="h-[34px] w-[93px] border border-black bg-[#d9d9d9] text-[15px] font-semibold disabled:opacity-50"
            >
              로그아웃
            </button>
          </div>
          {failed ? (
            // TODO(PD 문구)
            <p role="alert">로그아웃하지 못했습니다</p>
          ) : null}
        </section>

        <section>
          {/* TODO(PD 문구) */}
          <h2 className={`mt-[24px] ${HEADING_CLASS}`}>내 운세 기록</h2>
          {/* TODO(API): 내 운세 기록 목록 — 포트 · API 없음 (MY-01 ✚ Q-24) */}
          <div aria-hidden className={`mt-[7px] ${BOX_CLASS}`} />
        </section>

        <section>
          {/* TODO(PD 문구) */}
          <h2 className={`mt-[35px] ${HEADING_CLASS}`}>저장된 사람들</h2>
          <ul className="mt-[7px] flex flex-col gap-y-[21px]">
            {people.data?.map((person) => (
              <li key={person.personId}>
                {person.isSelf ? (
                  // TODO(PD 문구)
                  <p className={`mb-[8px] ${HEADING_CLASS}`}>기본 프로필</p>
                ) : null}
                <PersonCard
                  person={person}
                  people={[person]}
                  onSelect={() => {}}
                />
              </li>
            ))}
          </ul>
          {/* TODO(PD 아이콘 · PD 문구) */}
          <Link
            href="/me/people/new"
            aria-label="다른 사람 추가"
            className="mt-[27px] flex h-[53px] w-[351px] items-center justify-center rounded-[10px] border border-dashed border-black text-[20px]"
          >
            +
          </Link>
        </section>

        <nav
          aria-label="안내"
          className="mt-[29px] ml-[10px] flex flex-col text-[17px] font-medium leading-[24px]"
        >
          {/* TODO(Q-24 ✚): 등껍질 충전 · 사용 내역 — 경로 없음 */}
          {/* TODO(A-08 · PD): 회원탈퇴 안내 모달 */}
          {/* TODO(PD 문구) */}
          <Link href="/terms" className="underline">
            약관
          </Link>
          {/* TODO(PD 문구) */}
          <Link href="/refund" className="underline">
            환불정책
          </Link>
          {/* TODO(PD 문구) */}
          <Link href="/privacy" className="underline">
            개인정보처리방침
          </Link>
        </nav>
      </div>
    </AppShell>
  );
}
