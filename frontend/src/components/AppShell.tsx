"use client";

// "use client" 이유: 사이드 메뉴 열림 상태(useState)를 컴포넌트 로컬로 들고 있기 때문.
import * as Dialog from "@radix-ui/react-dialog";
import Link from "next/link";
import { type ReactNode, useState } from "react";
import { COLUMN_WIDTH } from "@/lib/layout";
import { isMenuGroup, MENU } from "@/lib/navigation";
import type { RoutePath } from "@/lib/screens";
import { BusinessFooter } from "./BusinessFooter";

// header=false: 헤더 없는 화면 (HOME-01 로그인, LAYOUT-FIGMA)
type AppShellProps = {
  header?: boolean;
  title?: string;
  backHref?: RoutePath;
  cta?: ReactNode;
  children: ReactNode;
};

// TODO(PD 토큰 v0): 아래 색은 Tailwind 기본 중립색 임시값
const COLOR_TEXT = "text-neutral-900";
// TODO(PD 토큰 v0): 와이어 임시값 — 그룹 라벨 색 (HOME-04, 195:647)
const COLOR_TEXT_MUTED = "text-[#787878]";
const COLOR_BG = "bg-white";
// TODO(PD 토큰 v0): 사이드 패널 배경 — Figma 와이어 값(52:174). 덮개(흰색 70%)와 구분돼 패널 위치를 확인할 수 있게
const COLOR_PANEL_BG = "bg-[#d9d9d9]";

export function AppShell({
  header = true,
  title,
  backHref,
  cta,
  children,
}: AppShellProps) {
  const [menuOpen, setMenuOpen] = useState(false);

  return (
    <div
      className={`mx-auto flex min-h-dvh w-full flex-col pt-[env(safe-area-inset-top)] ${COLOR_BG} ${COLOR_TEXT} ${cta ? "" : "pb-[env(safe-area-inset-bottom)]"}`}
      style={{ maxWidth: COLUMN_WIDTH }}
    >
      {header ? (
        <>
          {/* LAYOUT-FIGMA (195:203~206): 헤더 64px, 위 여백 29px — 제목 줄이 y 29~64, 세 칸은 그 줄 안에서 세로 가운데 */}
          <header className="grid h-[64px] grid-cols-[1fr_auto_1fr] items-center pt-[29px]">
            <div className="pl-[20px]">
              {backHref ? (
                // history.back 이 아니라 링크로 간다: 카카오톡 링크로 바로 들어오면 돌아갈 기록이 없다.
                // TODO(PD 아이콘): 텍스트 글리프 임시 (상자 34×34 만 와이어에 맞춘다)
                <Link
                  href={backHref}
                  aria-label="뒤로"
                  className="flex h-[34px] w-[34px] items-center justify-center"
                >
                  ‹
                </Link>
              ) : null}
            </div>
            <h1 className="text-[30px] leading-[normal]">{title}</h1>
            <div className="flex justify-end pr-[30px]">
              <Dialog.Root open={menuOpen} onOpenChange={setMenuOpen}>
                <Dialog.Trigger
                  aria-label="메뉴 열기"
                  className="flex h-[24px] w-[24px] items-center justify-center"
                >
                  {/* TODO(PD 아이콘): 텍스트 글리프 임시 */}≡
                </Dialog.Trigger>
                <Dialog.Portal>
                  {/* TODO(PD 토큰 v0): 흰색 70% (Figma 값) */}
                  <Dialog.Overlay className="fixed inset-0 bg-white/70" />
                  {/*
                오른쪽 끝을 브라우저 창이 아니라 앱 기둥 오른쪽 끝에 맞춘다:
                포털은 body 로 나가므로 fixed 의 right 를 (뷰포트 폭 - 기둥 폭) / 2 로 계산한다.
                % 는 fixed 에서 스크롤바를 뺀 뷰포트 폭 기준이다. 좁은 화면에서는 max() 로 0.
              */}
                  <Dialog.Content
                    aria-describedby={undefined}
                    className={`fixed top-0 flex h-[612px] max-h-dvh w-[168px] flex-col items-end rounded-l-[20px] pt-[env(safe-area-inset-top)] pr-[30px] ${COLOR_PANEL_BG} ${COLOR_TEXT}`}
                    style={{
                      right: `max(0px, calc((100% - ${COLUMN_WIDTH}px) / 2))`,
                    }}
                  >
                    <Dialog.Title className="sr-only">메뉴</Dialog.Title>
                    <div className="flex h-14 items-center">
                      <Dialog.Close aria-label="메뉴 닫기">
                        {/* TODO(PD 아이콘): 텍스트 글리프 임시 */}×
                      </Dialog.Close>
                    </div>
                    {/* HOME-04 (LAYOUT-FIGMA): 첫 항목이 패널 위에서 104px(닫기 줄 56px + 48px), 항목 사이 28px, 줄 높이 큰 항목 24px · 하위 항목 20px (195:657 텍스트 상자 높이) */}
                    <nav className="mt-[48px] flex flex-col items-end gap-y-[28px] text-[20px] leading-[24px] font-semibold">
                      {MENU.map((entry) =>
                        isMenuGroup(entry) ? (
                          <div
                            key={entry.label}
                            className="flex flex-col items-end"
                          >
                            <span className={COLOR_TEXT_MUTED}>
                              {entry.label}
                            </span>
                            <div className="mt-[14px] flex flex-col items-end gap-y-[12px] text-[17px] leading-[20px] font-semibold">
                              {entry.items.map((item) => (
                                <Link
                                  key={item.href}
                                  href={item.href}
                                  onClick={() => setMenuOpen(false)}
                                >
                                  {item.label}
                                </Link>
                              ))}
                            </div>
                          </div>
                        ) : (
                          <Link
                            key={entry.href}
                            href={entry.href}
                            onClick={() => setMenuOpen(false)}
                          >
                            {entry.label}
                          </Link>
                        ),
                      )}
                    </nav>
                  </Dialog.Content>
                </Dialog.Portal>
              </Dialog.Root>
            </div>
          </header>
        </>
      ) : null}

      <main className="flex-1">{children}</main>
      {/* PG-4: 모든 화면 하단 사업자 정보 + 약관 링크. cta 는 sticky 라 푸터 위에 겹쳐 따라온다 */}
      <BusinessFooter />

      {cta ? (
        // sticky: 앱 기둥 폭 안에서 화면 아래에 붙고, 본문 위에 겹치지 않아 가려지지 않는다.
        <div
          data-testid="app-cta"
          className={`sticky bottom-0 pb-[env(safe-area-inset-bottom)] ${COLOR_BG}`}
        >
          {cta}
        </div>
      ) : null}
    </div>
  );
}
