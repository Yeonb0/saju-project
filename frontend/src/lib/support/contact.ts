// 고객센터 문의 메일 링크. 근거: Q-38 g · Q-47, FUNCTIONAL_SPEC 13장 문의 메일 제목 표.
// 받는 주소는 business.ts 한 곳에서만 가져온다. 본문(body)은 넣지 않는다.
import { BUSINESS_INFO } from "@/lib/business";

export type SupportRef = Readonly<{
  kind: "topUpOrder" | "purchase" | "quote" | "paymentOrder";
  id: string;
}>;

const SUBJECT_PREFIX = "[뿌기사주 문의]";

function subjectOf(ref?: SupportRef): string {
  if (!ref) return SUBJECT_PREFIX;
  switch (ref.kind) {
    case "topUpOrder":
      return `${SUBJECT_PREFIX} 충전 주문번호 ${ref.id}`;
    case "purchase":
      return `${SUBJECT_PREFIX} 운세 구매번호 ${ref.id}`;
    case "quote":
      return `${SUBJECT_PREFIX} 운세 견적번호 ${ref.id}`;
    // TODO(Q-47 · PD 문구: 결제 주문번호 제목) — 직접결제 주문 (DIRECT-PAY)
    case "paymentOrder":
      return `${SUBJECT_PREFIX} 결제 주문번호 ${ref.id}`;
  }
}

// 전자우편주소가 없으면 null — 문의하기 버튼을 그리지 않는다
export function supportMailtoHref(ref?: SupportRef): string | null {
  const email = BUSINESS_INFO.email;
  if (email === null) return null;
  return `mailto:${email}?subject=${encodeURIComponent(subjectOf(ref))}`;
}
