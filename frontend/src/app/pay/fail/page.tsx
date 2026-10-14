import Link from "next/link";
import { readRetryProduct } from "@/lib/payment/retryProduct";
import {
  PAY_ACTION_PRIMARY,
  PAY_ACTION_SECONDARY,
  PayResultLayout,
} from "../PayResultLayout";

// /pay/fail — 토스 결제 실패 · 취소 복귀 (PG-3). 결제가 일어나지 않았으므로 서버 승인 요청을 보내지 않는다.
// PG 가 붙여 보내는 code 는 취소/실패 문구를 가르는 데만 쓰고, code · message 글자는 화면에 쓰지 않는다 — 서버 · PG 원문 노출 금지 (CLAUDE.md 오류 화면, Q-58 은 PD 확인 대기).
// 문구는 PD 확정 (FUNCTIONAL_SPEC 7장 결제 결과 화면 v0.4, Q-33). 결제가 일어나지 않은 화면이라 다시 결제하기는 늘 보인다.
// 고른 상품 유지: failUrl 의 ?product=상품코드 를 /wallet?product=상품코드 로 이어 준다 (TOPUP-RETRY-URL). 상품 코드는 링크 주소에만 쓰고 화면 글자로는 쓰지 않는다.
// 디자인 요소 없음 (PG-FIRST). 배치는 LAYOUT-FIGMA PAY-05 (301:190).
// searchParams 는 Next 16 에서 Promise 다 (node_modules/next/dist/docs/01-app/03-api-reference/03-file-conventions/page.md).
const CANCELED_CODE = "PAY_PROCESS_CANCELED";

export default async function Page(props: PageProps<"/pay/fail">) {
  const { code, product } = await props.searchParams;
  const retryProduct = readRetryProduct(product);
  const retryHref =
    retryProduct === null
      ? "/wallet"
      : `/wallet?product=${encodeURIComponent(retryProduct)}`;
  // 정확히 한 번 온 문자열 code 만 본다 (배열 · 없음은 실패 문구)
  const canceled = code === CANCELED_CODE;
  return (
    <PayResultLayout
      variant="failed"
      title={<p role="alert">결제가 완료되지 않았어요</p>}
      sub={
        <p>
          {canceled
            ? "결제를 취소했어요."
            : "결제 중 문제가 생겼어요. 잠시 후 다시 시도해 주세요."}
        </p>
      }
      box={<p>결제가 완료되지 않아 등껍질은 지급되지 않았어요.</p>}
      actions={
        <>
          <Link href={retryHref} className={PAY_ACTION_PRIMARY}>
            다시 결제하기
          </Link>
          <Link href="/" className={PAY_ACTION_SECONDARY}>
            홈으로
          </Link>
        </>
      }
    />
  );
}
