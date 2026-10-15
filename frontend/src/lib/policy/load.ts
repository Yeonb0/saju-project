// 서버 전용. 원고는 웹 대화 완성본을 DOCS-PLACE 로만 배치한다 — Claude Code 는 내용을 쓰지 않는다.
import { readFileSync } from "node:fs";
import path from "node:path";
import { type PolicyDoc, parsePolicy } from "./parse";

export type PolicyName = "terms" | "privacy" | "refund";

export function loadPolicy(
  name: PolicyName,
  dir: string = path.join(process.cwd(), "src/content/policies"),
): PolicyDoc | null {
  let md: string;
  try {
    md = readFileSync(path.join(dir, `${name}.md`), "utf8");
  } catch (e) {
    if ((e as NodeJS.ErrnoException).code === "ENOENT") return null;
    throw e;
  }
  return parsePolicy(md);
}
