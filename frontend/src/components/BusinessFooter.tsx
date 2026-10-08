import Link from "next/link";
import {
  BUSINESS_INFO,
  type BusinessField,
  type BusinessInfo,
} from "@/lib/business";

// PG 심사 노출 요건 (PG-4): 모든 화면 하단의 사업자 정보 + 약관 3종 링크.
// 와이어 프레임이 없어 읽을 수 있게만 정리한 심사용 최소 스타일이다 (LAYOUT-FIGMA). TODO(PD 토큰 v0): 와이어 임시값
// TODO(PD 문구): 항목 이름 · 링크 이름
const FIELDS: readonly { key: BusinessField; label: string }[] = [
  { key: "companyName", label: "상호" },
  { key: "representative", label: "대표자" },
  { key: "registrationNumber", label: "사업자등록번호" },
  { key: "address", label: "사업장 주소" },
  { key: "phone", label: "유선번호" },
  { key: "email", label: "전자우편주소" },
  { key: "mailOrderNumber", label: "통신판매업 신고번호" },
  { key: "hostingProvider", label: "호스팅 제공자" },
];

// 값이 없으면 항목을 숨기지 않고 자리표시를 보인다 — 미정 상태가 화면에서 바로 드러나게 (TODO(O-01))
const MISSING = "(미정)";

export function BusinessFooter({
  info = BUSINESS_INFO,
}: {
  info?: BusinessInfo;
}) {
  return (
    <footer className="mt-[48px] border-t border-[#d9d9d9] px-[20px] pt-[16px] pb-[24px] text-[12px] leading-[18px] text-neutral-600">
      <dl className="flex flex-col gap-y-[2px]">
        {FIELDS.map(({ key, label }) => (
          // 한 줄 "이름 : 값" — 구분자는 dt 의 ::after 라 화면 글자(dt · dd)는 그대로다
          <div key={key} className="flex gap-x-[4px]">
            <dt className="after:ml-[4px] after:content-[':']">{label}</dt>
            <dd className="min-w-0 break-words">{info[key] ?? MISSING}</dd>
          </div>
        ))}
      </dl>
      {/* 링크 3개가 한 단어처럼 붙어 보이지 않게 최소 간격만 둔다 (꾸밈 아님, PG-FIRST). TODO(PD 토큰 v0): 간격 값 */}
      <nav aria-label="약관" className="mt-[10px] flex flex-wrap gap-x-[12px]">
        <Link href="/terms" className="underline">
          이용약관
        </Link>
        <Link href="/privacy" className="underline">
          개인정보처리방침
        </Link>
        <Link href="/refund" className="underline">
          환불정책
        </Link>
      </nav>
    </footer>
  );
}
