"use client";

// "use client" 이유: 운세 상품 조회(TanStack Query)는 브라우저에서 한다.
// PG-4 /about 서비스 · 상품 소개 (로그인 불필요). 근거: R-01 (심사 상품 = 운세 건별 결제, DIRECT-PAY), P-09 (금액은 서버 상품 값만, 계산 · 하드코딩 없음),
// 정책 v4 환불정책 2 · 3 · 4 · 5조 (src/lib/payment/directNotice.ts 문장 그대로), docs/FRONTEND.md 1-2 (MOCK-PORT).
// 디자인 요소 없음 (PG-FIRST) — 기본 HTML 요소만. 배치는 LAYOUT-FIGMA INFO-01 (302:129). 서비스 소개는 PD 원고 대기 — 자리만 둔다.
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { AppShell } from "@/components/AppShell";
import { BUSINESS_INFO } from "@/lib/business";
import {
  FORTUNE_LABELS,
  FORTUNE_TYPE_OF_SLUG,
  type FortuneSlug,
} from "@/lib/navigation";
import {
  DELIVERY_LINES,
  PAYMENT_LIMIT_LINE,
  REFUND_SUMMARY_LINES,
} from "@/lib/payment/directNotice";
import { getFortunePort } from "@/lib/ports";
import type {
  FortunePort,
  FortuneProduct,
  FortuneType,
} from "@/lib/ports/fortune";

const formatNumber = (value: number) => value.toLocaleString("ko-KR");

type KnownType = Exclude<FortuneType, "UNKNOWN">;

// 상품 표시 순서 (수능운 먼저). 같은 종류 안은 서버 순서
const TYPE_ORDER: readonly KnownType[] = [
  "SUNEUNG",
  "OVERALL",
  "LOVE",
  "WEALTH",
  "COMPATIBILITY",
  "SINSAL",
];

// 운세 이름: 수능운은 따로, 그 밖은 navigation.ts 의 이름 (slug ↔ 종류 대응도 navigation.ts 의 표) — 표를 새로 만들지 않는다. TODO(PD 문구)
function fortuneName(type: FortuneType): string {
  if (type === "SUNEUNG") return "수능운";
  const slug = (
    Object.entries(FORTUNE_TYPE_OF_SLUG) as [FortuneSlug, FortuneType][]
  ).find(([, t]) => t === type)?.[0];
  if (!slug) throw new Error(`운세 종류에 맞는 slug 가 없다: ${type}`);
  return FORTUNE_LABELS[slug];
}

// 옵션 이름 — 정책 v4 2조 표의 말. TODO(PD 문구)
function optionName(product: FortuneProduct): string {
  if (product.fortuneType === "SUNEUNG") return "수능 부적 포함";
  if (product.option === "READING_ONLY") return "사주만";
  if (product.option === "READING_WITH_TALISMAN") return "사주 + 행운 부적";
  throw new Error(`알 수 없는 상품 옵션이다: ${product.option}`);
}

// 같은 「· 」 모양의 목록 (DirectCheckout 안내 목록과 같음)
function DotList({ lines }: { lines: readonly string[] }) {
  return (
    <ul className="mt-[12px] text-[14px] leading-[17px]">
      {lines.map((line) => (
        <li key={line} className="before:content-['·_']">
          {line}
        </li>
      ))}
    </ul>
  );
}

export function AboutScreen({
  fortunePort,
}: {
  // 테스트에서 주입한다. 기본값은 포트 선택(src/lib/ports) — 진짜 구현이 없으면 던져 오류 화면으로 간다.
  // 포트는 렌더 중이 아니라 요청할 때 고른다: 빌드의 정적 렌더에서 진짜 모드 오류로 빌드가 멈추지 않게.
  fortunePort?: FortunePort;
}) {
  // 종류 6개를 한 번의 조회로 받는다 (Promise.all) — 하나라도 실패하면 오류 화면으로
  const products = useQuery({
    queryKey: ["aboutFortuneProducts"],
    queryFn: async () => {
      const port = fortunePort ?? getFortunePort();
      const lists = await Promise.all(
        TYPE_ORDER.map((type) => port.listProducts(type)),
      );
      return lists.flat();
    },
  });

  // 시끄럽게 실패: 조회 실패와 계약 위반은 오류 화면(error.tsx)으로
  if (products.error) throw products.error;

  // 비활성 상품은 어떤 경우에도 보이지 않는다. 종류 순서 → 서버 순서 (정렬은 안정 정렬)
  const active = products.data
    ?.filter((p) => p.active)
    .sort(
      (a, b) =>
        TYPE_ORDER.indexOf(a.fortuneType as KnownType) -
        TYPE_ORDER.indexOf(b.fortuneType as KnownType),
    );
  // 직접결제 상품 가격은 원화다 — 조용히 건너뛰지 않는다 (DIRECT-PAY)
  if (active?.some((p) => p.price.currency !== "KRW")) {
    throw new Error("직접결제 상품 가격이 KRW 가 아니다 (DIRECT-PAY)");
  }

  return (
    // 서비스명이 화면 제목(AppShell 의 h1) — NAME (D-11). h1 은 하나만 둔다
    <AppShell title="뿌기사주">
      <div className="mx-[34px] pb-[28px]">
        {/* TODO(캐릭터): 뿌기 — PG 심사 후 */}
        <div
          aria-hidden
          data-slot="character"
          className="mx-auto mt-[26px] h-[100px] w-[100px]"
        />

        <section className="mt-[20px]">
          {/* TODO(PD 문구) */}
          <h2 className="text-[18px] leading-[22px] font-bold">서비스 소개</h2>
          {/* TODO(PD 문구 · R-01): 서비스 소개 */}
          <p
            data-slot="intro"
            className="mt-[12px] min-h-[102px] text-[14px] leading-[17px]"
          />
        </section>

        <section className="mt-[28px]">
          {/* TODO(PD 문구) */}
          <h2 className="text-[18px] leading-[22px] font-bold">상품과 가격</h2>
          {/* 서버 값만 표시한다 (P-09). TODO(PD 토큰 v0): 와이어 임시값 (302:129) */}
          <div
            data-slot="products"
            className="mt-[12px] min-h-[150px] rounded-[8px] bg-[#f2f2f2] px-[14px] pt-[16px] text-[13px] leading-[16px]"
          >
            {active && active.length > 0 ? (
              <ul>
                {active.map((product) => (
                  <li key={product.code}>
                    {/* TODO(PD 문구): 항목 이름 · 단위 */}
                    {fortuneName(product.fortuneType)} · {optionName(product)} ·{" "}
                    {formatNumber(product.price.amount)}원
                  </li>
                ))}
              </ul>
            ) : active ? (
              // TODO(PD 문구)
              <p>판매 중인 상품이 없습니다</p>
            ) : null}
          </div>
          {/* 2조 */}
          <p className="mt-[12px] text-[14px] leading-[17px]">
            {PAYMENT_LIMIT_LINE}
          </p>
        </section>

        <section className="mt-[26px]">
          {/* TODO(PD 문구) */}
          <h2 className="text-[18px] leading-[22px] font-bold">
            제공 방법 · 기간
          </h2>
          {/* 3조 */}
          <DotList lines={DELIVERY_LINES} />
        </section>

        <section className="mt-[28px]">
          {/* TODO(PD 문구) */}
          <h2 className="text-[18px] leading-[22px] font-bold">환불</h2>
          {/* 4 · 5조 */}
          <DotList lines={REFUND_SUMMARY_LINES} />
          <p className="text-[14px] leading-[17px]">
            {/* TODO(PD 문구) */}
            <Link href="/refund" className="underline">
              환불정책
            </Link>
          </p>
        </section>

        <section className="mt-[28px]">
          {/* TODO(PD 문구) */}
          <h2 className="text-[18px] leading-[22px] font-bold">고객센터</h2>
          {/* 사업자 값은 src/lib/business.ts 에서만 (R-07). 값이 없으면 그 줄을 그리지 않는다. TODO(PD 문구): 항목 이름 · 운영 시간 */}
          <div
            data-slot="support"
            className="mt-[12px] text-[14px] leading-[17px]"
          >
            {BUSINESS_INFO.email === null ? null : (
              <p>이메일: {BUSINESS_INFO.email}</p>
            )}
            {BUSINESS_INFO.phone === null ? null : (
              <p>전화: {BUSINESS_INFO.phone}</p>
            )}
          </div>
        </section>
      </div>
    </AppShell>
  );
}
