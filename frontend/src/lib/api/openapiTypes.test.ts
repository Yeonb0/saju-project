import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import openapiTS, { astToString } from "openapi-typescript";
import { describe, expect, it } from "vitest";

const SRC_DIR = fileURLToPath(new URL("../../", import.meta.url));
const GENERATED_PATH = path.join(SRC_DIR, "types", "api.d.ts");
const THIS_FILE = fileURLToPath(import.meta.url);
const OPENAPI_URL = new URL(
  "../../../../docs/openapi/api-v1.json",
  import.meta.url,
);

const normalize = (text: string) => text.replace(/\r\n/g, "\n");

// CLI 가 파일 처음에 붙이는 머리 주석 블록(/** … */ 와 뒤의 빈 줄)
const stripHeader = (text: string) =>
  text.replace(/^\/\*\*[\s\S]*?\*\/\n\n/, "");

describe("생성 타입이 OpenAPI 파일과 같다", () => {
  it("src/types/api.d.ts 는 docs/openapi/api-v1.json 에서 생성한 결과와 같다", async () => {
    const ast = await openapiTS(OPENAPI_URL);
    const generated = normalize(astToString(ast));
    const stored = stripHeader(normalize(readFileSync(GENERATED_PATH, "utf8")));
    // OpenAPI 파일이 바뀌었으면 pnpm api:types 를 다시 실행한다 (생성 파일은 손으로 고치지 않는다)
    expect(
      stored,
      "OpenAPI 파일이 바뀌었으면 pnpm api:types 를 다시 실행",
    ).toBe(generated);
  });
});

function sourceFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) return sourceFiles(full);
    return /\.tsx?$/.test(entry.name) ? [full] : [];
  });
}

const posix = (file: string) =>
  path.relative(SRC_DIR, file).split(path.sep).join("/");

// import · export 문(from "…" · import "…" · import("…"))의 모듈 지정자
const SPECIFIER = /(?:\bfrom\s*|\bimport\s*\(?\s*)["']([^"']+)["']/g;

function pointsToGeneratedTypes(file: string, specifier: string) {
  if (specifier === "@/types/api") return true;
  if (!specifier.startsWith(".")) return false;
  const resolved = path.resolve(path.dirname(file), specifier);
  return posix(resolved) === "types/api";
}

describe("생성 타입은 src/lib/api/adapters/ 에서만 쓴다 (MOCK-PORT)", () => {
  it("adapters 밖에서 types/api 를 가져오는 파일이 없다", () => {
    const offenders = sourceFiles(SRC_DIR)
      .filter((file) => file !== THIS_FILE && file !== GENERATED_PATH)
      .filter((file) => !posix(file).startsWith("lib/api/adapters/"))
      .filter((file) => {
        const text = readFileSync(file, "utf8");
        return [...text.matchAll(SPECIFIER)].some((m) =>
          pointsToGeneratedTypes(file, m[1]),
        );
      })
      .map(posix);
    expect(offenders).toEqual([]);
  });
});
