import type { ReactNode } from "react";

type ChipProps = {
  selected: boolean;
  onClick: () => void;
  children: ReactNode;
  disabled?: boolean;
};

export function Chip({ selected, onClick, children, disabled }: ChipProps) {
  return (
    // FRONTEND.md 2장 — frame-*.svg 를 border-image 로 입힐 자리. SVG 는 PD 전달 후
    // TODO(PD 토큰 v0): 색 · 모서리 · 테두리 임시값 — 값은 와이어 칩 (LAYOUT-FIGMA 347:211)
    <button
      type="button"
      data-frame="chip"
      aria-pressed={selected}
      onClick={onClick}
      disabled={disabled}
      className={`h-[34px] rounded-[8px] border border-black px-[14px] text-[13px] font-bold leading-[normal] text-black disabled:opacity-50 ${selected ? "bg-[#d9d9d9]" : "bg-white"}`}
    >
      {children}
    </button>
  );
}
