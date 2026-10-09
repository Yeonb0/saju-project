// PAY-03 · 04 · 05 공통 배치 (LAYOUT-FIGMA 301:166 · 301:180 · 301:190). 헤더 없음.
// 색은 TODO(PD 토큰 v0): 와이어 임시값. 캐릭터는 TODO(캐릭터): 뿌기 — PG 심사 후 (자리만).
// "use client" 없음 — 서버 컴포넌트(/pay/fail)와 클라이언트(TopUpSuccess) 둘 다에서 쓴다.
import type { ReactNode } from "react";
import { AppShell } from "@/components/AppShell";

type Variant = "done" | "waiting" | "failed";

// 클래스는 완전한 리터럴로 둔다 — 조합 · 템플릿으로 이름을 만들면 Tailwind 가 읽지 못한다
const STYLE: Record<
  Variant,
  { character: string; title: string; sub: string; box: string }
> = {
  done: {
    character: "mt-[180px] h-[120px] w-[120px]",
    title: "mt-[30px] text-[30px] leading-[36px] font-bold",
    sub: "mt-[19px] min-h-[20px] text-[17px] leading-[20px]",
    box: "mt-[35px] h-[90px] w-[335px] rounded-[8px] bg-[#f2f2f2] px-[20px] pt-[12px] text-left",
  },
  waiting: {
    character: "mt-[220px] h-[120px] w-[120px]",
    title: "mt-[30px] text-[22px] leading-[26px] font-bold",
    sub: "mt-[14px] min-h-[36px] text-[15px] leading-[18px] text-[#737373]",
    box: "mt-[114px] h-[90px] w-[335px] rounded-[8px] bg-[#f2f2f2] px-[16px] pt-[18px] text-left text-[13px] leading-[16px]",
  },
  failed: {
    character: "mt-[220px] h-[120px] w-[120px]",
    title: "mt-[30px] text-[22px] leading-[26px] font-bold",
    sub: "mt-[14px] min-h-[18px] text-[15px] leading-[18px] text-[#737373]",
    box: "mt-[42px] h-[70px] w-[335px] rounded-[8px] bg-[#f2f2f2] px-[16px] pt-[24px] text-left text-[13px] leading-[16px]",
  },
};

// 새 PD 프레임은 하단 버튼 327×60 — 공용 Button cta(327×69)는 디자인 시스템 적용 때 맞춘다 (와이어 301:175 · 301:177)
export const PAY_ACTION_PRIMARY =
  "flex h-[60px] w-[327px] items-center justify-center border border-black bg-[#d9d9d9] text-[18px] font-bold";
export const PAY_ACTION_SECONDARY =
  "flex h-[60px] w-[327px] items-center justify-center border border-black bg-white text-[18px] font-bold";

export function PayResultLayout({
  variant,
  title,
  sub,
  box,
  actions,
}: {
  variant: Variant;
  title: ReactNode;
  sub?: ReactNode;
  box?: ReactNode;
  actions?: ReactNode;
}) {
  const style = STYLE[variant];
  return (
    <AppShell
      header={false}
      cta={
        actions ? (
          <div className="flex flex-col items-center gap-[13px] pb-[41px]">
            {actions}
          </div>
        ) : undefined
      }
    >
      <div className="flex flex-col items-center text-center">
        {/* TODO(캐릭터): 뿌기 — PG 심사 후 */}
        <div aria-hidden data-slot="character" className={style.character} />
        <div data-slot="pay-result-title" className={style.title}>
          {title}
        </div>
        <div data-slot="pay-result-sub" className={style.sub}>
          {sub}
        </div>
        {box ? (
          <div data-slot="pay-result-box" className={style.box}>
            {box}
          </div>
        ) : null}
      </div>
    </AppShell>
  );
}
