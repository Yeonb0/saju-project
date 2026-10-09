import type { HTMLAttributes } from "react";

export function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    // FRONTEND.md 2장 — frame-*.svg 를 border-image 로 입힐 자리. SVG 는 PD 전달 후
    // TODO(PD 토큰 v0): 와이어 임시값 (LAYOUT-FIGMA, 195:208) — 안쪽 여백은 쓰는 쪽이 정한다
    <div
      data-frame="card"
      className={`rounded-[10px] border border-black bg-[rgba(220,220,220,0.6)] ${className ?? ""}`}
      {...props}
    />
  );
}
