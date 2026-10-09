// INFO-02 정책 페이지 공통 틀 (LAYOUT-FIGMA 302:155 · PD 메모 302:174 — 세 페이지 같은 틀, 제목만 다름, 로그인 없이 열림, 본문 글자 14 이상).
// 원고는 확정 전 코드에 넣지 않는다 — PD 초안은 docs/policies/ (Q-21 · Q-36). TODO(PD 토큰 v0): 와이어 임시값.
import { AppShell } from "@/components/AppShell";

export function PolicyPage({ title }: { title: string }) {
  return (
    <AppShell title={title} backHref="/">
      <div className="mx-[34px] pb-[31px]">
        {/* TODO(Q-36 · R-07): 시행일 — 확정본에서 */}
        <p
          data-slot="policy-effective-date"
          className="mt-[20px] min-h-[16px] text-[13px] leading-[16px] text-[#737373]"
        />
        {/* TODO(Q-21 · Q-36): 목차 — 확정본의 조 · 항 제목에서 만든다. 와이어의 목차 글자는 초안 원고라 옮기지 않는다 */}
        <nav
          aria-label="목차"
          data-slot="policy-toc"
          className="mt-[16px] min-h-[120px] rounded-[8px] bg-[#f2f2f2] px-[14px] pt-[14px] text-[14px] leading-[17px]"
        />
        {/* TODO(Q-21 · Q-36): 본문 — 확정본 원고 (글자 14 이상, PD 메모 302:174) */}
        <article
          data-slot="policy-body"
          className="mt-[26px] min-h-[238px] text-[14px] leading-[17px]"
        />
      </div>
    </AppShell>
  );
}
