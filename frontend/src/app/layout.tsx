import type { Metadata, Viewport } from "next";
import "./globals.css";

// D-11 확정(2026-09-26): 서비스명 뿌기사주. TODO(PD 문구): description 은 PD 문구 확정 후 교체
export const metadata: Metadata = {
  title: "뿌기사주",
  description: "수능 수험생을 위한 사주와 부적",
};

// safe-area(env())가 iOS 에서 값을 가지려면 viewport-fit=cover 가 필요하다.
export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
