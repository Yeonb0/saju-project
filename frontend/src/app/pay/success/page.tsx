import { notFound } from "next/navigation";
import { readPaymentReturn } from "@/lib/payment/confirmTopUp";
import { DirectPaymentReturn } from "./DirectPaymentReturn";
import { TopUpSuccess } from "./TopUpSuccess";

// /pay/success — 토스 결제 성공 복귀 (PG-3). PG 복귀 쿼리 paymentKey · orderId · amount 를 그대로 넘긴다.
// 하나라도 없거나 같은 이름이 여러 번 오면 승인 요청을 보내지 않고 not-found (CLAUDE.md 오류 화면).
// searchParams 는 Next 16 에서 Promise 다 (node_modules/next/dist/docs/.../page.md).
export default async function Page(props: PageProps<"/pay/success">) {
  const query = await props.searchParams;
  // 직접결제 복귀는 successUrl 의 flow=direct (DIRECT-PAY). 없으면 충전, 그 밖의 값 · 여러 번은 not-found
  const flow = query.flow;
  if (flow !== undefined && flow !== "direct") notFound();
  const params = new URLSearchParams();
  for (const [name, value] of Object.entries(query)) {
    if (typeof value === "string") params.set(name, value);
  }
  const ret = readPaymentReturn(params);
  if (!ret) notFound();
  if (flow === "direct") return <DirectPaymentReturn ret={ret} />;
  return <TopUpSuccess ret={ret} />;
}
