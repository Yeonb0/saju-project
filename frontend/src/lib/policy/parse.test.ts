import { describe, expect, it } from "vitest";
import { parsePolicy } from "./parse";

// 아래 원고는 픽스처일 뿐이며 실제 약관 · 규칙과 무관하다.
const t = (text: string) => ({ text, bold: false });
const b = (text: string) => ({ text, bold: true });

const FIXTURE = `
# 시험 제목

시행일: 2000년 1월 1일

앞말 첫 줄
앞말 둘째 줄

## 첫째 조

본문 **굵게** 글.

### 소제목

- 가
  - 가-1
    - 가-2
- 나

1. 하나
2. 둘

| 칸A | 칸B |
|---|---|
| a1 | **b1** |
| a2 | b2 |

## 둘째 조

내용

## 셋째 조

끝
`;

describe("parsePolicy", () => {
  it("정해진 문법을 정확한 구조로 만든다", () => {
    expect(parsePolicy(FIXTURE)).toEqual({
      effectiveDate: "시행일: 2000년 1월 1일",
      intro: [{ kind: "paragraph", inlines: [t("앞말 첫 줄 앞말 둘째 줄")] }],
      sections: [
        {
          id: "article-1",
          title: [t("첫째 조")],
          blocks: [
            { kind: "paragraph", inlines: [t("본문 "), b("굵게"), t(" 글.")] },
            { kind: "subheading", inlines: [t("소제목")] },
            {
              kind: "list",
              ordered: false,
              items: [
                { inlines: [t("가")], children: [[t("가-1")], [t("가-2")]] },
                { inlines: [t("나")], children: [] },
              ],
            },
            {
              kind: "list",
              ordered: true,
              items: [
                { inlines: [t("하나")], children: [] },
                { inlines: [t("둘")], children: [] },
              ],
            },
            {
              kind: "table",
              header: [[t("칸A")], [t("칸B")]],
              rows: [
                [[t("a1")], [b("b1")]],
                [[t("a2")], [t("b2")]],
              ],
            },
          ],
        },
        {
          id: "article-2",
          title: [t("둘째 조")],
          blocks: [{ kind: "paragraph", inlines: [t("내용")] }],
        },
        {
          id: "article-3",
          title: [t("셋째 조")],
          blocks: [{ kind: "paragraph", inlines: [t("끝")] }],
        },
      ],
    });
  });

  it("시행일이 없으면 null", () => {
    expect(parsePolicy("# 제목\n\n## 조\n\n글").effectiveDate).toBeNull();
  });

  const ERRORS: [string, string, number][] = [
    ["[ ] 자리표시", "# 제목\n\n## 조\n\n[시행일 확정 필요]", 5],
    ["○ 자리표시", "# 제목\n\n## 조\n\n○○ 회사", 5],
    ["링크", "# 제목\n\n## 조\n\n[글](http://x)", 5],
    ["HTML", "# 제목\n\n## 조\n\n<b>글</b>", 5],
    ["인용", "# 제목\n\n## 조\n\n> 인용", 5],
    ["코드 울타리", "# 제목\n\n## 조\n\n```\n코드\n```", 5],
    ["구분선", "# 제목\n\n## 조\n\n---", 5],
    ["#### 제목", "# 제목\n\n## 조\n\n#### 깊음", 5],
    ["두 번째 #", "# 제목\n\n# 또 제목\n\n## 조\n\n글", 3],
    ["시행일 두 번", "# 제목\n시행일: 가\n시행일: 나\n\n## 조\n\n글", 3],
    ["시행일이 ## 뒤", "# 제목\n\n## 조\n\n시행일: 가", 5],
    ["표 칸 수 불일치", "# 제목\n\n## 조\n\n| a | b |\n|---|---|\n| 1 |", 7],
    ["표 구분선 없음", "# 제목\n\n## 조\n\n| a | b |\n| 1 | 2 |", 6],
    ["짝 안 맞는 **", "# 제목\n\n## 조\n\n**굵게", 5],
    ["깊은 들여쓰기", "# 제목\n\n## 조\n\n- 가\n      - 깊음", 6],
    ["들여쓴 숫자 목록", "# 제목\n\n## 조\n\n- 가\n  1. 숫자", 6],
    ["빈 ## 제목", "# 제목\n\n## \n\n글", 3],
    ["제목 줄 없음", "## 조\n\n글", 1],
    ["Section 없음", "# 제목\n\n글만", 3],
    ["빈 문자열", "", 1],
  ];
  it.each(ERRORS)("거부: %s — 메시지에 줄 번호", (_name, md, line) => {
    expect(() => parsePolicy(md)).toThrow(new RegExp(`${line}줄`));
  });
});
