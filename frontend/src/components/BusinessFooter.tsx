import Link from "next/link";
import {
  BUSINESS_INFO,
  type BusinessField,
  type BusinessInfo,
} from "@/lib/business";

// PG 심사 노출 요건 (PG-4): 모든 화면 하단의 사업자 정보 + 약관 3종 링크. 스타일 없음 (PG-FIRST)
// TODO(PD 문구): 항목 이름 · 링크 이름
const FIELDS: readonly { key: BusinessField; label: string }[] = [
  { key: "companyName", label: "상호" },
  { key: "representative", label: "대표자" },
  { key: "registrationNumber", label: "사업자등록번호" },
  { key: "address", label: "사업장 주소" },
  { key: "phone", label: "유선번호" },
  { key: "mailOrderNumber", label: "통신판매업 신고번호" },
];

// 값이 없으면 항목을 숨기지 않고 자리표시를 보인다 — 미정 상태가 화면에서 바로 드러나게 (TODO(O-01))
const MISSING = "(미정)";

export function BusinessFooter({
  info = BUSINESS_INFO,
}: {
  info?: BusinessInfo;
}) {
  return (
    <footer>
      <dl>
        {FIELDS.map(({ key, label }) => (
          <div key={key}>
            <dt>{label}</dt>
            <dd>{info[key] ?? MISSING}</dd>
          </div>
        ))}
      </dl>
      <nav aria-label="약관">
        <Link href="/terms">이용약관</Link>
        <Link href="/privacy">개인정보처리방침</Link>
        <Link href="/refund">환불정책</Link>
      </nav>
    </footer>
  );
}
