// INFO-02 정책 페이지 공통 틀 (LAYOUT-FIGMA 302:155 · PD 메모 302:174 — 세 페이지 같은 틀, 제목만 다름, 로그인 없이 열림, 본문 글자 14 이상).
// 원고는 src/content/policies/*.md 를 lib/policy 가 읽어 doc 으로 넘긴다. 파일이 없으면 doc 이 null 이고 빈 자리만 둔다 (Q-21 · Q-36 · Q-56).
// TODO(PD 토큰 v0): 와이어 임시값.
// biome-ignore-all lint/suspicious/noArrayIndexKey: 원고는 빌드 때 고정된 목록이라 순서가 바뀌지 않는다 (같은 글자가 반복될 수 있다)
import { AppShell } from "@/components/AppShell";
import type { Block, Inline, PolicyDoc } from "@/lib/policy/parse";

function Inlines({ items }: { items: Inline[] }) {
  return (
    <>
      {items.map((inline, i) =>
        inline.bold ? <strong key={i}>{inline.text}</strong> : inline.text,
      )}
    </>
  );
}

function BlockView({ block }: { block: Block }) {
  switch (block.kind) {
    case "paragraph":
      return (
        <p className="mt-[8px]">
          <Inlines items={block.inlines} />
        </p>
      );
    case "subheading":
      return (
        <h3 className="mt-[16px] text-[14px] font-semibold">
          <Inlines items={block.inlines} />
        </h3>
      );
    case "list": {
      const Tag = block.ordered ? "ol" : "ul";
      return (
        <Tag
          className={`mt-[8px] pl-[20px] ${block.ordered ? "list-decimal" : "list-disc"}`}
        >
          {block.items.map((item, i) => (
            <li key={i}>
              <Inlines items={item.inlines} />
              {item.children.length > 0 && (
                <ul className="mt-[8px] list-disc pl-[20px]">
                  {item.children.map((child, k) => (
                    <li key={k}>
                      <Inlines items={child} />
                    </li>
                  ))}
                </ul>
              )}
            </li>
          ))}
        </Tag>
      );
    }
    case "table":
      return (
        <div className="mt-[8px] overflow-x-auto">
          <table className="w-full border-collapse text-[14px]">
            <thead>
              <tr>
                {block.header.map((cell, i) => (
                  <th
                    key={i}
                    className="border border-[#d9d9d9] p-[6px] text-left align-top"
                  >
                    <Inlines items={cell} />
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {block.rows.map((row, r) => (
                <tr key={r}>
                  {row.map((cell, c) => (
                    <td
                      key={c}
                      className="border border-[#d9d9d9] p-[6px] text-left align-top"
                    >
                      <Inlines items={cell} />
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      );
  }
}

export function PolicyPage({
  title,
  doc,
}: {
  title: string;
  doc: PolicyDoc | null;
}) {
  return (
    <AppShell title={title} backHref="/">
      <div className="mx-[34px] pb-[31px]">
        <p
          data-slot="policy-effective-date"
          className="mt-[20px] min-h-[16px] text-[13px] leading-[16px] text-[#737373]"
        >
          {doc?.effectiveDate}
        </p>
        <nav
          aria-label="목차"
          data-slot="policy-toc"
          className="mt-[16px] min-h-[120px] rounded-[8px] bg-[#f2f2f2] px-[14px] pt-[14px] text-[14px] leading-[17px]"
        >
          {doc && (
            <ol>
              {doc.sections.map((section) => (
                <li key={section.id}>
                  <a href={`#${section.id}`}>
                    <Inlines items={section.title} />
                  </a>
                </li>
              ))}
            </ol>
          )}
        </nav>
        <article
          data-slot="policy-body"
          className="mt-[26px] min-h-[238px] text-[14px] leading-[17px]"
        >
          {doc?.intro.map((block, i) => (
            <BlockView key={i} block={block} />
          ))}
          {doc?.sections.map((section) => (
            <section key={section.id} aria-labelledby={section.id}>
              <h2
                id={section.id}
                className="mt-[24px] text-[15px] font-semibold leading-[20px]"
              >
                <Inlines items={section.title} />
              </h2>
              {section.blocks.map((block, i) => (
                <BlockView key={i} block={block} />
              ))}
            </section>
          ))}
        </article>
      </div>
    </AppShell>
  );
}
