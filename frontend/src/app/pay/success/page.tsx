import { notFound } from "next/navigation";
import { readPaymentReturn } from "@/lib/payment/confirmTopUp";
import { DirectPaymentReturn } from "./DirectPaymentReturn";

// /pay/success — 토스 결제 성공 복귀 (PG-3). PG 복귀 쿼리 paymentKey · orderId · amount 를 그대로 넘긴다.
// 하나라도 없거나 같은 이름이 여러 번 오면 승인 요청을 보내지 않고 not-found (CLAUDE.md 오류 화면).
// searchParams 는 Next 16 에서 Promise 다 (node_modules/next/dist/docs/.../page.md).
export default async function Page(props: PageProps<"/pay/success">) {
  const query = await props.searchParams;
  // 충전 복귀는 닫았다 — DIRECT-PAY. flow=direct 가 아니면 not-found (없음 · 다른 값 · 여러 번)
  const flow = query.flow;
  if (flow !== "direct") notFound();
  const params = new URLSearchParams();
  for (const [name, value] of Object.entries(query)) {
    if (typeof value === "string") params.set(name, value);
  }
  const ret = readPaymentReturn(params);
  if (!ret) notFound();
  return <DirectPaymentReturn ret={ret} />;
}
