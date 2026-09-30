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
    // TODO(PD 토큰 v0): 색 · 모서리 · 테두리 임시값
    <button
      type="button"
      data-frame="chip"
      aria-pressed={selected}
      onClick={onClick}
      disabled={disabled}
      className={`rounded-full border border-neutral-900 px-3 py-1 disabled:opacity-50 ${selected ? "bg-neutral-900 text-white" : "bg-white text-neutral-900"}`}
    >
      {children}
    </button>
  );
}
