// INFO-02 정책 페이지 (PG-4). 로그인 없이 열린다 — RequireSession 으로 감싸지 않는다.
import { PolicyPage } from "@/components/PolicyPage";

export default function Page() {
  // TODO(PD 문구): 제목
  return <PolicyPage title="환불정책" />;
}
