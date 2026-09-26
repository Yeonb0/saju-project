import type { Metadata } from "next";
import "./globals.css";

// TODO(D-11): 서비스명이 확정되면 title·description을 실제 문구로 바꾼다.
export const metadata: Metadata = {
  title: "사주 서비스 (서비스명 미정)",
  description: "수능 수험생을 위한 사주와 부적",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
