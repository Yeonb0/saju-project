import { RequireSession } from "@/lib/auth/RequireSession";
import { readRetryProduct } from "@/lib/payment/retryProduct";
import { WalletScreen } from "./WalletScreen";

// PAY-01 등껍질 충전 (PG-3). 화면 내용은 WalletScreen (클라이언트)
// PG-2 · PG-3 · A-02 (로그인 필요) · A-03 (본인 정보 없으면 온보딩) — 가드로 감싼다
// ?product=상품코드 는 결제 실패 · 취소 뒤 다시 결제하기의 상품 유지 (TOPUP-RETRY-URL). 모양만 보고 넘긴다 — 판매 여부는 WalletScreen 이 서버 목록으로 본다.
// searchParams 는 Next 16 에서 Promise 다 (node_modules/next/dist/docs/01-app/03-api-reference/03-file-conventions/page.md).
export default async function Page(props: PageProps<"/wallet">) {
  const { product } = await props.searchParams;
  return (
    <RequireSession>
      <WalletScreen retryProductCode={readRetryProduct(product)} />
    </RequireSession>
  );
}
