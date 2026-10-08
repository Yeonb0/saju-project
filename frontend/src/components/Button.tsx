import type { ButtonHTMLAttributes } from "react";

// variant: cta = 최종 와이어 하단 CTA(195:216), popup = 팝업 안 주 버튼(248:268) (LAYOUT-FIGMA)
type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "default" | "cta" | "popup";
};

// TODO(PD 토큰 v0): 와이어 임시값 — cta 의 폭 · 높이 · 배경 · 글자
const VARIANT_CLASS = {
  default: "rounded-lg border border-neutral-900 bg-white px-4 py-3",
  popup:
    "h-[55px] w-[274px] border border-black bg-[#d9d9d9] text-center text-[20px]",
  cta: "h-[69px] w-[327px] border border-black bg-[#d9d9d9] text-center text-[20px] font-semibold",
} as const;

export function Button({
  type = "button", // 폼 안에서 의도치 않은 submit 방지 — Checkout · PersonForm 에서 쓴다
  variant = "default",
  className,
  ...props
}: ButtonProps) {
  return (
    // FRONTEND.md 2장 — frame-*.svg 를 border-image 로 입힐 자리. SVG 는 PD 전달 후
    // TODO(PD 토큰 v0): 색 · 모서리 · 테두리 임시값
    <button
      type={type}
      data-frame="button"
      className={`${VARIANT_CLASS[variant]} text-neutral-900 disabled:opacity-50 ${className ?? ""}`}
      {...props}
    />
  );
}
