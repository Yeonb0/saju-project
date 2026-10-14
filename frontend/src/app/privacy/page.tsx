// INFO-02 정책 페이지 (PG-4). 로그인 없이 열린다 — RequireSession 으로 감싸지 않는다.
import { PolicyPage } from "@/components/PolicyPage";
import { loadPolicy } from "@/lib/policy/load";

// 원고(src/content/policies/privacy.md)는 빌드 때 한 번 읽는다.
export const dynamic = "force-static";

export default function Page() {
  // TODO(PD 문구): 제목
  return <PolicyPage title="개인정보처리방침" doc={loadPolicy("privacy")} />;
}
