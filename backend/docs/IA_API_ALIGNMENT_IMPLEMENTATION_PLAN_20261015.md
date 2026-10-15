# IA 기준 백엔드 API 정렬 및 BE-B 구현 계획

기준일: 2026-10-15
기준 화면: IA와 `데모데이 와이어프레임.pdf` 89쪽의 `HOME`, `TODAY`, `FORT/MATCH`, `CSAT`, `GIFT`, `RECV`, `PAY`, `TALBOX`, `MY`
범위: 실제 부적 이미지 생성·저장·R2 adapter는 승인 에셋 수령 전까지 제외한다.

## 1. 결론과 담당 경계

현재 로그인 사용자 운세·수능운·부적 보관함·직접 링크 선물 구매 API는 IA의 대부분을 지원한다. 출시 흐름을 막거나 와이어프레임 완성을 위해 보완할 차이는 아래와 같다.

| 우선순위 | IA | 차이 | 담당 |
|---|---|---|---|
| P0 | `RECV-04~07`, `RECV-T-04` | 공개 선물 안내와 수신 정보 접수는 있으나 결과 생성/조회 projection이 없음 | BE-B, A port 연결 |
| P0 | `FORT-07~09` | 사주만 구매한 결과에서 부적을 추가 구매하는 API가 문서에만 있고 controller가 없음 | BE-B + BE-A 지갑 |
| P0 | `보낸 선물` | 현재 목록에는 수신자 표시 이름·option·열람 여부·취소 가능 projection이 부족함 | BE-A gift + BE-B 제공 상태 |
| P1 | `CSAT 판매 종료` | 상품 목록에서 종료 상품이 사라져 종료와 미등록을 구분할 수 없음 | BE-A catalog, FE |
| P1 | `충전·사용 내역` | 사용자 `GET /wallet/transactions`가 문서에만 있고 구현되지 않음 | BE-A |
| P1 | `공유 랜딩` | 오늘의 운세 공유만 구현됐고 일반 운세·부적의 비식별 공유 API가 없음 | BE-B, PD 범위 확정 |
| P1 | `받은 선물` | 내부 계정 연결 port는 있으나 수신자용 연결/목록 HTTP API가 없음 | BE-A + BE-B |

선물 전달은 단계적으로 진행한다. 1차 퀵오픈은 전화번호 없이 구매자가 `/g#token=...` 링크를 직접 전달하고, 2차에 카카오 알림톡을 기본 전달 수단으로 추가한다. 2차 발송 실패 시에도 선물을 자동 취소하지 않고 직접 링크로 복구한다. 공개 token은 API에는 JSON body로만 보낸다.

## 2. 목표 공개 선물 계약

### 구현됨

- `POST /api/v1/public/gifts/access`: 최소 안내, 메시지, option, `INPUT_REQUIRED/PROCESSING/READY`
- `POST /api/v1/public/gifts/recipient-profile`: 암호화 snapshot과 durable fulfillment intent 저장, `202 Accepted`

### 구현 대상

- `POST /api/v1/public/gifts/result`
  - request: raw token만 포함하며 DTO/log에서 마스킹
  - processing: `202`, `state=PROCESSING`, 제한된 polling 힌트
  - ready: `200`, immutable option별 projection
  - unavailable: 변조·미존재·폐기·만료·취소를 같은 `404 RESOURCE_NOT_FOUND`로 처리
  - failed: 생년정보나 provider 원문이 없는 안정적인 실패 분류와 CS 안내
- `POST /api/v1/public/gifts/talismans/{talismanId}/download-url`
  - 계약과 권한 검증 테스트만 준비하고 실제 URL 발급은 이미지/R2 준비 전 비활성

`READING_WITH_TALISMAN`은 교시별 사주, 수능 도시락, 준비물 체크리스트를 만들 수 있는 typed `suneungGuide`와 부적 metadata/status를 반환한다. `TALISMAN_ONLY`는 메시지와 부적 metadata/status만 반환하고 사주·도시락·체크리스트·내부 reading ID를 노출하지 않는다. 현재 `GiftQuoteService`의 `TALISMAN_ONLY` 체크리스트 포함은 먼저 수정한다.

## 3. BE-B 구현 단계

### Phase 0 — 계약·기존 변경 보호

1. gift 전용 OpenAPI에 `result` schema와 option별 nullable/required 규칙을 먼저 추가한다.
2. 공유 `docs/openapi/api-v1.json`은 FE 타입 갱신과 같은 feature boundary에서 동기화한다.
3. 현재 작업 트리의 recipient profile 파기·key rotation·claim/fence 변경을 보존하고 해당 PostgreSQL 테스트를 기준선으로 실행한다.

완료 조건: 1차 OpenAPI가 token을 path/query에 노출하지 않고 전화번호 없이 동작하며, 향후 알림톡 필드를 호환 가능하게 추가할 수 있고 기존 access/profile 계약과 호환된다.

### Phase 1 — immutable 선물 결과 저장 모델

1. `gift_id` 또는 receipt 기준 결과 snapshot을 한 건만 저장하는 additive Flyway migration을 추가한다.
2. snapshot에는 option, content version, generation version, typed 수능 결과, talisman ID/status만 저장한다.
3. 원본 birth input, user/person ID, raw token, private object key는 결과 projection 저장소에 넣지 않는다.
4. gift/receipt/job/result의 unique key로 중복 worker와 process restart에도 결과가 하나만 생기게 한다.

완료 조건: 동일 gift 동시 저장 한 건, 다른 content 충돌 차단, 기존 migration 데이터 보존을 PostgreSQL에서 확인한다.

### Phase 2 — fulfillment worker

1. 현재 `gift_fulfillment_jobs` claim/lease/fence 저장소 위에 worker orchestration을 연결한다.
2. 짧은 transaction에서 job과 암호화 profile을 claim하고, transaction 밖에서 복호화 → 만세력 facts → Liner/검증된 fallback을 실행한다.
3. 저장 직전에 fence와 현재 gift/receipt 상태를 재확인하고 immutable snapshot을 저장한다.
4. timeout/5xx/schema 오류는 bounded retry하고 소진 시 `REVIEW_REQUIRED`로 전환한다.
5. 실제 부적 이미지는 만들지 않는다. 결정 가능한 오행·동물·문구 metadata와 `PENDING` 상태까지만 연결한다.

완료 조건: 중복 생성 없음, stale worker 완료 거절, 취소된 선물 부활 금지, provider 호출 중 DB lock/transaction 없음.

### Phase 3 — 공개 결과 조회와 최초 제공

1. `POST /public/gifts/result` controller와 최소 projection service를 추가한다.
2. 결과 미완료는 `202 PROCESSING`, 완료는 `200 READY`로 반환한다.
3. 최초 READY 응답을 확정하는 짧은 transaction에서 A `GiftFulfillmentPort.provide`와 content ID를 함께 저장해 취소와 직렬화한다.
4. 재조회는 같은 snapshot과 재열람 만료일을 반환하며 최초 제공 시각을 다시 쓰지 않는다.
5. option별 필드 누출 방지와 타 gift의 talisman ID 접근 차단을 적용한다.

완료 조건: `RECV-04~07`에 필요한 typed fields가 있고 `TALISMAN_ONLY`에서 reading이 누출되지 않으며, 제공/취소 경쟁이 한쪽으로만 수렴한다.

### Phase 4 — 보안·복구·계약 검증

1. 공개 POST의 Origin 정책과 token/IP 기반 bounded rate limit을 적용한다.
2. token, 생년정보, 메시지, result 본문이 access log·애플리케이션 log·오류 응답에 남지 않는지 테스트한다.
3. worker kill/restart, lease 만료, Liner timeout, fallback, DB 저장 실패를 fault injection으로 검증한다.
4. 목적 종료 파기와 처리 중 job 보호, key rotation, 복원 뒤 worker 재개 순서를 PostgreSQL에서 검증한다.
5. backend `check`, `bootJar`, gift OpenAPI export와 코드 리뷰를 수행한다.

실제 staging proxy/Sentry/PostHog 검색, 브라우저 E2E, 부하, 백업 복원, 실제 Liner/R2는 로컬 CI 통과와 별도 증거로 기록한다.

### Phase 5 — 와이어프레임 후속 API

1. 사주만 구매한 소유 reading에 부적을 한 번 추가 구매하는 견적/구매/status API를 구현한다. 중복·동시 요청에서 한 번만 차감하고 기존 talisman을 재사용한다.
2. 일반 운세·부적 공유는 와이어프레임의 공개 카드 필드와 만료·철회 정책을 PD가 확정한 뒤 비식별 snapshot으로 구현한다.
3. 로그인 수신자의 선물 계정 연결과 `받은 선물` 목록을 owner-only cursor API로 제공한다.
4. 보낸 선물 목록에 수신자 표시 이름, option, 최초 제공 여부, 취소 가능 여부를 최소 projection으로 추가한다.

완료 조건: 타인 reading/gift/talisman 접근은 동일 404이고, 추가 부적 구매·선물 연결은 중복 차감/중복 ownership을 만들지 않으며, 공유 snapshot에 생년정보·메시지·private object key가 없다.

### Phase 6 — 카카오 알림톡 전환 (1차 출시 후 별도 feature)

1. 수신자 전화번호·발송 동의·정책 version을 목적별 암호화 저장하고 1차 전화번호 없는 주문과 호환한다.
2. 금융/선물 상태와 분리된 delivery 상태, durable outbox, lease/fence, bounded retry, 결과 불명 조회를 연결한다.
3. 대행사 서명 webhook을 event ID로 멱등 처리하고 실제 전송/조회는 DB transaction 밖에서 수행한다.
4. 발송 실패 화면은 같은 직접 링크를 복사할 수 있게 하고 자동 취소·환급하지 않는다.
5. 재발송으로 token을 바꾸면 기존 링크를 폐기하되 원래 90일 만료일을 연장하지 않는다.

이 단계는 대행사·템플릿·발신 프로필·서명 규격·전화번호 보관기간과 사용자 승인 없이는 활성화하지 않는다.

## 4. BE-A·FE·PD 요청

### BE-A

- FORTUNE 상품 목록이 운영상 노출 가능한 수능 상품을 종료 후에도 반환하고 `UPCOMING/AVAILABLE/ENDED`를 제공하도록 catalog 계약을 구현한다. 견적은 `AVAILABLE`만 허용한다.
- 사용자 `GET /api/v1/wallet/transactions`를 본인 소유 cursor page로 구현한다.
- 결과 최초 제공과 취소가 같은 gift work 잠금에서 직렬화되는 기존 port 계약을 BE-B 결과 저장과 함께 검토한다.

### FE

- `/g#token=...`을 읽은 즉시 주소에서 제거하고 body-token access/profile/result 흐름을 사용한다.
- `202 PROCESSING`은 같은 선물 조회로 polling하고 새 제출이나 새 구매를 만들지 않는다.
- 모든 unavailable 404는 이유를 추측하지 않고 IA의 공통 `열 수 없는 링크` 화면으로 보낸다.
- `GIFT-05`는 링크 조회·복사·공유 실패로 표시하고 기존 주문 재조회와 명시적 재발급을 구분한다.
- 2차 알림톡 적용 뒤에는 `GIFT-05`를 발송 실패 + 직접 링크 fallback으로 확장하고 1차 링크 흐름을 제거하지 않는다.

### PD

- 교시/도시락/준비물 field의 화면 제목·빈 상태·실패/지연 문구를 확정한다.
- 실제 부적 이미지·문구 조합표·content version·사용 권리를 전달한다. 전달 전에는 placeholder와 상태만 사용한다.
- 만료·취소·변조를 구분하지 않는 `열 수 없는 링크` 공통 문구를 확정한다.

## 5. 검증 매트릭스

| 영역 | 필수 검증 |
|---|---|
| 권한 | token 변조/다른 gift/talisman ID, 로그인 사용자 소유권과 공개 token 권한 분리 |
| 중복·동시성 | 같은 제출 replay, 동시 worker, 제공/취소 경쟁, stale fence 완료 |
| 실패·복구 | provider timeout/5xx/schema 오류, process interruption, 제한 재시도/검토 전환 |
| 개인정보 | body token만 사용, 로그/오류/OpenAPI 예시에 실제 비밀·생년정보 없음, 목적 종료 파기 |
| 결과 범위 | TALISMAN_ONLY reading 비노출, READING_WITH_TALISMAN typed guide 완전성 |
| 배포 | 신규 Flyway만 사용, 구/신 앱 혼합 호환, 기본 feature flag 비활성, PostgreSQL 검증 |

## 6. 이번 범위 밖

- 실제 부적 bitmap 생성·R2 저장·signed URL 실발급
- 카카오 알림톡/SMS/provider webhook의 **1차 퀵오픈 구현**. 후속 Phase 6 계획에는 포함한다.
- 여러 명 선물 `GIFT-06~08`
- 일반 운세·부적 공개 공유 범위 `G-11`
- 라이브 판매 활성화, 실제 PG 결제, 배포
