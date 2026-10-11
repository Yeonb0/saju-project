import Link from "next/link";
import {
  BUSINESS_INFO,
  type BusinessField,
  type BusinessInfo,
} from "@/lib/business";

// PG 심사 노출 요건 (PG-4): 모든 화면 하단의 사업자 정보 + 약관 3종 링크.
// 배치는 LAYOUT-FIGMA COMMON-01 (302:175) · PD 메모 302:179. TODO(PD 토큰 v0): 와이어 임시값
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

// 줄 구성 (와이어 302:175 순서) — FIELDS 8개 키가 빠짐 · 중복 없이 한 번씩 들어간다
const ROWS: readonly (readonly BusinessField[])[] = [
  ["companyName", "representative"],
  ["registrationNumber"],
  ["mailOrderNumber"],
  ["address"],
  ["phone", "email"],
  ["hostingProvider"],
];

const LABEL_OF = Object.fromEntries(
  FIELDS.map(({ key, label }) => [key, label]),
) as Record<BusinessField, string>;

// 값이 없으면 항목을 숨기지 않고 자리표시를 보인다 — 미정 상태가 화면에서 바로 드러나게 (TODO(O-01))
const MISSING = "(미정)";

export function BusinessFooter({
  info = BUSINESS_INFO,
}: {
  info?: BusinessInfo;
}) {
  return (
    <footer className="min-h-[250px] bg-[#f2f2f2] px-[24px] pt-[20px] pb-[24px] text-[12px] leading-[14px]">
      <nav aria-label="약관" className="flex flex-wrap items-center text-black">
        <Link href="/terms">이용약관</Link>
        <span aria-hidden className="mx-[10px]">
          |
        </span>
        {/* PD 메모 302:179 "개인정보처리방침 링크는 굵게" */}
        <Link href="/privacy" className="font-bold">
          개인정보처리방침
        </Link>
        <span aria-hidden className="mx-[10px]">
          |
        </span>
        <Link href="/refund">환불정책</Link>
      </nav>
      <dl className="mt-[16px] flex flex-col gap-y-[2px] text-[#737373]">
        {ROWS.map((row) => (
          <div key={row.join("-")} className="flex flex-wrap gap-x-[12px]">
            {row.map((key) => (
              // 한 줄 "이름 : 값" — 구분자는 dt 의 ::after 라 화면 글자(dt · dd)는 그대로다
              <div key={key} className="flex gap-x-[4px]">
                <dt className="after:ml-[4px] after:content-[':']">
                  {LABEL_OF[key]}
                </dt>
                <dd className="min-w-0 break-words">{info[key] ?? MISSING}</dd>
              </div>
            ))}
          </div>
        ))}
      </dl>
      {/* TODO(PD 문구): 저작권 표기 — 서비스명은 NAME(D-11) */}
      <p className="mt-[2px] text-[#737373]">© 뿌기사주</p>
    </footer>
  );
}
