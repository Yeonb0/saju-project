"use client";

// "use client" 이유: 세션 · 지갑 조회(TanStack Query)는 브라우저에서 한다.
// HOME-03 홈 (docs/FRONTEND.md 3장, Figma 195:92). 근거: A-02 (홈은 공개 — 들어간 화면이 로그인 가드),
// P-09 · Q-22 (잔액은 서버 값만), MOCK-PORT (포트는 렌더 중이 아니라 요청할 때 고른다), NAME (D-11), LAYOUT-FIGMA (와이어 값).
// 선물하기는 OpenAPI 대기라 지금은 자리표시 화면(/gift/new)으로 간다. 디자인 요소 없음 (PG-FIRST).
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { AppShell } from "@/components/AppShell";
import { SESSION_QUERY_KEY } from "@/lib/auth/RequireSession";
import { FORTUNE_LABELS, type FortuneSlug } from "@/lib/navigation";
import { getSessionPort, getTopUpPort } from "@/lib/ports";
import type { SessionPort } from "@/lib/ports/session";
import type { TopUpPort } from "@/lib/ports/topUp";
import { WALLET_QUERY_KEY } from "@/lib/wallet/query";

const formatNumber = (value: number) => value.toLocaleString("ko-KR");

// TODO(PD 토큰 v0): 와이어 임시값 (LAYOUT-FIGMA)
const CARD_CLASS = "bg-[#d9d9d9]";
const CARD_TITLE_CLASS = "text-[20px] font-semibold";
const TILE_CLASS = "flex items-center justify-center bg-[#b8b8b8] text-[20px]";

function FortuneTile({
  slug,
  className,
}: {
  slug: FortuneSlug;
  className: string;
}) {
  return (
    <Link href={`/fortune/${slug}`} className={`${TILE_CLASS} ${className}`}>
      {FORTUNE_LABELS[slug]}
    </Link>
  );
}

export function HomeScreen({
  sessionPort,
  topUpPort,
}: {
  // 테스트에서 주입한다. 기본값은 포트 선택(src/lib/ports) — 요청할 때 고른다 (MOCK-PORT).
  sessionPort?: SessionPort;
  topUpPort?: TopUpPort;
}) {
  const session = useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: () => (sessionPort ?? getSessionPort()).getSession(),
    // 로그아웃 직후 캐시에 남은 옛 세션으로 잔액을 보이지 않게, 들어올 때마다 새로 받는다
    refetchOnMount: "always",
  });
  const state = session.isFetchedAfterMount ? session.data : undefined;
  const signedIn = state?.status === "signed_in";

  const wallet = useQuery({
    queryKey: WALLET_QUERY_KEY,
    queryFn: () => (topUpPort ?? getTopUpPort()).getWallet(),
    enabled: signedIn,
  });

  // 시끄럽게 실패: 조회 실패는 오류 화면(error.tsx)으로
  if (session.error) throw session.error;
  if (wallet.error) throw wallet.error;

  return (
    // 홈은 자체 위 줄이 있어 AppShell 헤더를 그리지 않는다 (HOME-03)
    <AppShell header={false}>
      <div className="mx-[26px] mb-[31px]">
        {/* 위 줄 (y 34 ~ 69) */}
        <div className="mt-[34px] grid h-[35px] grid-cols-[1fr_auto_1fr] items-center">
          <div className="flex items-center">
            {/* TODO(PD 아이콘): 등껍질 */}
            <div aria-hidden className="h-[35px] w-[33px]" />
            <Link
              href="/wallet"
              className="ml-[3px] flex flex-col text-[13px] font-semibold"
            >
              {/* 로그인 상태일 때만 서버 잔액 (P-09) */}
              <span className="min-h-[16px]">
                {signedIn && wallet.data
                  ? formatNumber(wallet.data.balance)
                  : null}
              </span>
              {/* TODO(PD 문구) */}
              <span>충전하기</span>
            </Link>
          </div>
          {/* NAME (D-11) */}
          <h1 className="text-[30px]">뿌기사주</h1>
          <Link
            href="/me"
            aria-label="마이페이지"
            className="flex h-[30px] w-[30px] items-center justify-center justify-self-end"
          >
            {/* TODO(PD 아이콘): 텍스트 글리프 임시 */}◯
          </Link>
        </div>

        {/* 카드 줄 1 (위 줄 아래 31px) */}
        <div className="mt-[31px] flex gap-x-[13px]">
          <Link
            href="/today"
            className={`h-[159px] w-[211px] pt-[16px] pl-[14px] ${CARD_CLASS} ${CARD_TITLE_CLASS}`}
          >
            {/* TODO(PD 문구) */}
            오늘의 운세
          </Link>
          <Link
            href="/vault"
            className={`h-[159px] w-[125px] pt-[16px] text-center ${CARD_CLASS} ${CARD_TITLE_CLASS}`}
          >
            {/* TODO(PD 문구) */}
            부적 모음
          </Link>
        </div>

        {/* 유료 운세 */}
        <section
          className={`mt-[20px] h-[186px] w-[350px] px-[15px] pt-[14px] ${CARD_CLASS}`}
        >
          {/* TODO(PD 문구) */}
          <h2 className={CARD_TITLE_CLASS}>유료 운세</h2>
          <div className="mt-[13px] flex gap-x-[10px]">
            <FortuneTile slug="overall" className="h-[120px] w-[100px]" />
            <div className="grid grid-cols-[100px_100px] gap-x-[10px] gap-y-[13px]">
              <FortuneTile slug="love" className="h-[52px] w-[100px]" />
              <FortuneTile slug="wealth" className="h-[52px] w-[100px]" />
              <FortuneTile
                slug="compatibility"
                className="h-[52px] w-[100px]"
              />
              <FortuneTile slug="sinsal" className="h-[52px] w-[100px]" />
            </div>
          </div>
        </section>

        {/* 이벤트 운세 */}
        <section
          className={`mt-[20px] h-[217px] w-[350px] px-[15px] pt-[14px] ${CARD_CLASS}`}
        >
          {/* TODO(PD 문구) */}
          <h2 className={CARD_TITLE_CLASS}>이벤트 운세</h2>
          <div className="mt-[13px] flex gap-x-[22px]">
            <Link
              href="/suneung"
              className={`h-[150px] w-[149px] ${TILE_CLASS}`}
            >
              {/* TODO(PD 문구) */}
              수능운
            </Link>
            {/* 링크가 아닌 비활성 타일 */}
            <div
              aria-disabled="true"
              className={`h-[150px] w-[149px] ${TILE_CLASS}`}
            >
              {/* TODO(PD 문구) */}
              준비 중
            </div>
          </div>
        </section>

        {/* 선물하기 — 선물 화면은 OpenAPI 대기라 지금은 자리표시로 간다 */}
        <Link
          href="/gift/new"
          className={`mt-[20px] block h-[121px] w-[350px] px-[15px] pt-[14px] ${CARD_CLASS}`}
        >
          {/* TODO(PD 문구) */}
          <h2 className={CARD_TITLE_CLASS}>선물하기</h2>
        </Link>
      </div>
    </AppShell>
  );
}
