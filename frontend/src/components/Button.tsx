import type { ButtonHTMLAttributes } from "react";

// variant 는 Figma 확인 전이라 만들지 않는다.
export function Button({
  type = "button", // 폼 안에서 의도치 않은 submit 방지 — Checkout · PersonForm 에서 쓴다
  className,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    // FRONTEND.md 2장 — frame-*.svg 를 border-image 로 입힐 자리. SVG 는 PD 전달 후
    // TODO(PD 토큰 v0): 색 · 모서리 · 테두리 임시값
    <button
      type={type}
      data-frame="button"
      className={`rounded-lg border border-neutral-900 bg-white px-4 py-3 text-neutral-900 disabled:opacity-50 ${className ?? ""}`}
      {...props}
    />
  );
}
