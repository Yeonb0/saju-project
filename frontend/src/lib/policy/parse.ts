// 정책 원고(마크다운 일부) 파서. 정해진 문법만 받고 그 밖은 전부 Error — 원고 오류가 빌드 실패로 드러나게 한다.
// 미완 원고([ ] 자리표시 · ○) 와 지원하지 않는 문법(링크 · HTML · 인용 · 코드)은 화면에 나가지 않게 거부한다.

export type Inline = { text: string; bold: boolean };
export type ListItem = { inlines: Inline[]; children: Inline[][] };
export type Block =
  | { kind: "paragraph"; inlines: Inline[] }
  | { kind: "subheading"; inlines: Inline[] }
  | { kind: "list"; ordered: boolean; items: ListItem[] }
  | { kind: "table"; header: Inline[][]; rows: Inline[][][] };
export type Section = { id: string; title: Inline[]; blocks: Block[] };
export type PolicyDoc = {
  effectiveDate: string | null;
  intro: Block[];
  sections: Section[];
};

const EFFECTIVE_PREFIX = "시행일:";
const BULLET = /^- (.*)$/;
const NUMBERED = /^\d+\. (.*)$/;
const CHILD = /^( {2}| {4})- (.*)$/;
const TABLE_SEPARATOR = /^\|(\s*-{3,}\s*\|)+$/;

function fail(line: number, message: string): never {
  throw new Error(`정책 원고 ${line}줄: ${message}`);
}

function parseInlines(text: string, line: number): Inline[] {
  const parts = text.split("**");
  if (parts.length % 2 === 0) fail(line, "짝이 맞지 않는 ** 가 있다");
  const out: Inline[] = [];
  parts.forEach((part, index) => {
    if (part === "") return;
    out.push({ text: part, bold: index % 2 === 1 });
  });
  return out;
}

function plainLength(inlines: Inline[]): number {
  return inlines
    .map((i) => i.text)
    .join("")
    .trim().length;
}

function splitRow(raw: string, line: number): string[] {
  const row = raw.trim();
  if (!row.startsWith("|") || !row.endsWith("|") || row.length < 2) {
    fail(line, "표의 줄은 | 로 시작하고 | 로 끝나야 한다");
  }
  return row
    .slice(1, -1)
    .split("|")
    .map((cell) => cell.trim());
}

function checkForbidden(raw: string, line: number): void {
  if (/[[\]○]/.test(raw))
    fail(line, "[ ] · ○ 자리표시나 링크 문법이 남아 있다");
  if (raw.includes("<")) fail(line, "HTML 은 지원하지 않는다");
  if (raw.includes("`")) fail(line, "코드 문법은 지원하지 않는다");
  if (raw.startsWith(">")) fail(line, "인용은 지원하지 않는다");
  if (raw.trim() === "---") fail(line, "구분선은 지원하지 않는다");
  if (raw.includes("\t")) fail(line, "탭은 지원하지 않는다");
}

export function parsePolicy(md: string): PolicyDoc {
  if (md.trim() === "") fail(1, "원고가 비어 있다");
  const lines = md.split(/\r?\n/);
  for (let k = 0; k < lines.length; k += 1) checkForbidden(lines[k], k + 1);

  let titleSeen = false;
  let effectiveDate: string | null = null;
  const intro: Block[] = [];
  const sections: Section[] = [];
  let target: Block[] = intro;

  const isBlockStart = (raw: string): boolean =>
    raw.startsWith("#") ||
    raw.startsWith("|") ||
    raw.startsWith(EFFECTIVE_PREFIX) ||
    BULLET.test(raw) ||
    NUMBERED.test(raw);

  let i = 0;
  while (i < lines.length) {
    const raw = lines[i];
    const no = i + 1;
    if (raw.trim() === "") {
      i += 1;
      continue;
    }
    if (/^\s/.test(raw)) fail(no, "예상하지 못한 들여쓰기");

    if (raw.startsWith("#")) {
      const m = /^(#+)(?: (.*))?$/.exec(raw);
      if (!m) fail(no, "# 뒤에는 공백이 필요하다");
      const level = m[1].length;
      if (level >= 4) fail(no, "#### 이상의 제목은 지원하지 않는다");
      const titleText = (m[2] ?? "").trim();
      if (titleText === "") fail(no, "제목이 비어 있다");
      if (!titleSeen) {
        if (level !== 1) fail(no, "첫 줄은 # 제목이어야 한다");
        titleSeen = true;
      } else if (level === 1) {
        fail(no, "# 제목은 한 번만 쓸 수 있다");
      } else if (level === 2) {
        const section: Section = {
          id: `article-${sections.length + 1}`,
          title: parseInlines(titleText, no),
          blocks: [],
        };
        if (plainLength(section.title) === 0) fail(no, "제목이 비어 있다");
        sections.push(section);
        target = section.blocks;
      } else {
        target.push({
          kind: "subheading",
          inlines: parseInlines(titleText, no),
        });
      }
      i += 1;
      continue;
    }
    if (!titleSeen) fail(no, "첫 줄은 # 제목이어야 한다");

    if (raw.startsWith(EFFECTIVE_PREFIX)) {
      if (sections.length > 0) fail(no, "시행일은 첫 ## 앞에만 쓸 수 있다");
      if (effectiveDate !== null) fail(no, "시행일은 한 번만 쓸 수 있다");
      if (raw.slice(EFFECTIVE_PREFIX.length).trim() === "")
        fail(no, "시행일이 비어 있다");
      effectiveDate = raw.trim();
      i += 1;
      continue;
    }

    if (raw.startsWith("|")) {
      const header = splitRow(raw, no);
      const sepNo = no + 1;
      const sep = lines[i + 1];
      if (sep === undefined || !TABLE_SEPARATOR.test(sep.trim())) {
        fail(sepNo, "표의 둘째 줄은 |---| 구분선이어야 한다");
      }
      if (splitRow(sep, sepNo).length !== header.length) {
        fail(sepNo, "구분선의 칸 수가 머리행과 다르다");
      }
      const rows: Inline[][][] = [];
      let j = i + 2;
      while (j < lines.length && lines[j].startsWith("|")) {
        const cells = splitRow(lines[j], j + 1);
        if (cells.length !== header.length)
          fail(j + 1, "표의 칸 수가 머리행과 다르다");
        rows.push(cells.map((c) => parseInlines(c, j + 1)));
        j += 1;
      }
      target.push({
        kind: "table",
        header: header.map((c) => parseInlines(c, no)),
        rows,
      });
      i = j;
      continue;
    }

    const bullet = BULLET.exec(raw);
    const numbered = NUMBERED.exec(raw);
    if (bullet || numbered) {
      const ordered = numbered !== null;
      const items: ListItem[] = [];
      let j = i;
      while (j < lines.length) {
        const line = lines[j];
        const lno = j + 1;
        const top = (ordered ? NUMBERED : BULLET).exec(line);
        if (top) {
          if (top[1].trim() === "") fail(lno, "목록 항목이 비어 있다");
          items.push({
            inlines: parseInlines(top[1].trim(), lno),
            children: [],
          });
        } else if (/^\s/.test(line) && line.trim() !== "") {
          const child = CHILD.exec(line);
          if (!child)
            fail(
              lno,
              "목록은 한 단계(2칸 또는 4칸)의 - 항목만 안에 둘 수 있다",
            );
          if (child[2].trim() === "") fail(lno, "목록 항목이 비어 있다");
          items[items.length - 1].children.push(
            parseInlines(child[2].trim(), lno),
          );
        } else {
          break;
        }
        j += 1;
      }
      if (
        j < lines.length &&
        lines[j].trim() !== "" &&
        !isBlockStart(lines[j])
      ) {
        fail(j + 1, "목록 바로 다음 줄은 빈 줄이거나 새 블록이어야 한다");
      }
      target.push({ kind: "list", ordered, items });
      i = j;
      continue;
    }

    const parts: string[] = [raw.trim()];
    let j = i + 1;
    while (
      j < lines.length &&
      lines[j].trim() !== "" &&
      !isBlockStart(lines[j])
    ) {
      if (/^\s/.test(lines[j])) fail(j + 1, "예상하지 못한 들여쓰기");
      parts.push(lines[j].trim());
      j += 1;
    }
    target.push({
      kind: "paragraph",
      inlines: parseInlines(parts.join(" "), no),
    });
    i = j;
  }

  if (!titleSeen) fail(1, "첫 줄은 # 제목이어야 한다");
  if (sections.length === 0) fail(lines.length, "## 항목이 하나도 없다");
  return { effectiveDate, intro, sections };
}
