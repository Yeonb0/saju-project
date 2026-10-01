import type { HTMLAttributes } from "react";

export function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    // FRONTEND.md 2장 — frame-*.svg 를 border-image 로 입힐 자리. SVG 는 PD 전달 후
    // TODO(PD 토큰 v0): 색 · 모서리 · 테두리 임시값
    <div
      data-frame="card"
      className={`rounded-lg border border-neutral-900 bg-white p-4 ${className ?? ""}`}
      {...props}
    />
  );
}
