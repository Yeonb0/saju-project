# A 구매 요청 키 검증

## 이번 구현의 범위

공통 HTTP 경계에서 본인 운세 구매 요청 키를 최초 요청 내용에 영속적으로 연결한다.
B controller/service 및 FE 코드는 변경하지 않는다. 요청 DTO를 파싱한 다음, 인증과
CSRF를 통과하고 DTO 필수값 검증을 만족한 요청에만 적용한다.

대상: POST /api/v1/reading-purchases와 overall/love/wealth/compatibility/sinsal 하위 경로.
견적 POST, 오행분석, 조회, 아직 없는 충전/선물 API에는 적용하지 않는다.
새 구매 endpoint를 추가하면 PurchaseRequestBindingAdvice의 controller/path 목록과 테스트를 함께 갱신한다.

- 같은 사용자 + 같은 키 + 같은 경로/요청: 기존 B 처리로 진행한다.
- 같은 사용자 + 같은 키 + 다른 견적/선택/경로: 409 IDEMPOTENCY_KEY_REUSED, B 구매 호출 전 거절.
- 다른 사용자: 같은 문자열 키를 사용해도 독립적으로 처리한다.
- 공백/512자 초과 키: 400 INVALID_REQUEST. 키 누락은 기존 MVC 필수 헤더 오류다.
- malformed JSON, DTO 필수값 오류, 미인증/CSRF 거절은 binding을 만들지 않는다.
- 업무 검증 실패/503 등 DTO 검증 이후의 실패는 binding을 유지한다.
  같은 구매 의도는 같은 키로 재시도하고, 다른 구매 의도는 새 키를 사용한다.

## 저장과 트랜잭션

새 V202610092230 migration의 purchase_request_bindings는 actor_id, key_hash,
request_hash, created_at만 저장한다. 키/요청 원문/인물 이름/생년정보/결과 응답은 저장하지 않는다.
request_hash는 HTTP 경로와 파싱된 DTO를 JSON으로 변환한 값의 SHA-256이다.
객체 필드 순서는 정렬하고 배열 순서는 보존한다. DTO가 같은 값으로 변환하는
생략/명시적 null 및 JSON 필드 순서 차이는 같은 요청으로 본다.
클라이언트 키의 대소문자/공백은 임의로 정규화하지 않는다.

(actor_id, key_hash) primary key가 동시 요청을 조정한다. 최초 insert는 독립된 짧은
트랜잭션에서 커밋한다. 중복 insert가 실패하면 그 트랜잭션의 롤백이 끝난 후
다른 짧은 읽기 트랜잭션에서 기존 request_hash를 비교한다.
PostgreSQL에서 제약 오류가 난 트랜잭션 안에서 계속 조회하는 방식은 사용하지 않는다.
지갑/구매/Liner 호출 중에는 binding DB 연결이나 잠금을 유지하지 않는다.

## 아직 보장하지 않는 것

이 장치는 전체 IdempotencyStore 구현이 아니라 '원본 HTTP 키의 요청 불일치 차단'이다.
동일 요청을 실행 중 하나만 맡는 lease, 최초 HTTP 응답 재사용, 만료 후 완료 결과 재조회,
purchaseId 상태 조회, 프로세스 중단 재개는 이번 구현에 없다.
같은 키/같은 요청의 동시 처리는 기존 B 구매 점유와 A quote별 원장 중복 방지에 맡긴다.
같은 quote에 다른 키를 보내도 A receipt는 차감을 한 번으로 제한한다.
구매 CREATED/DEBITED/GENERATING 중단 복구는 여전히 A/B 공동 후속 계약이다.
결과 오류만으로 환급 완료를 표시해도 된다는 의미가 아니다.

키의 보존/삭제 기간은 아직 확정하지 않았다. 자동 삭제/TTL을 임의로 넣지 않는다.
DTO 필드/기본값 변경은 기존 request_hash와 재전송 호환성 검토를 포함해야 한다.
새 테이블은 추가형 migration이며 구 버전은 읽지 않는다. 구 버전과 신 버전이 함께 실행되면
구 버전 요청은 이 보호를 우회할 수 있으므로 전체 인스턴스 교체 후에만 적용 보장을 주장한다.
binding은 실패 요청에도 남으므로 가입자별 요청 제한/보존 용량 검증은 운영 활성화 전 후속이다.

## 검증

PurchaseRequestBindingTest: 영속 저장, 원문 미저장, JSON 정규화, 사용자 분리,
키/본문/경로 충돌, 새 서비스 인스턴스, 독립 트랜잭션 동시 요청.
PurchaseRequestBindingApiTest: 모든 공개 구매 경로, 동일 재전송, 선택 변경,
업무 실패 후 재시도, 인증/CSRF/입력 거절, B service 호출 횟수.
HTTP suite는 B service를 mock으로 대체한다. 실제 지갑 차감/외부 생성 E2E는 아니다.
최종 실행 환경과 결과는 docs/OPERATIONS_CHECKLIST.md 9장에 기록한다.
