# BE-B FE 연결 차단 해소 구현 계획

> 작성일: 2026-10-11
> 담당: BE-B
> 대상: FE 요청서 `뿌기사주 팀 요청서 (FE, 10/11)`의 BE-B 항목
> 생성 모델: **Liner API** (`LinerApiProvider`) — OpenAI API는 사용하지 않는다.

## 구현 진행 현황

| 단계 | 상태 | 비고 |
| --- | --- | --- |
| Phase 0 인물·관계 계약 | 완료 | `docs/API_SPEC.md`에 people DTO, 오류, 관계 매핑 반영 |
| Phase 1 인물/`ReadingSubjectPort` | 1차 완료 | CRUD, 암호화 저장, 소유권, soft delete, 제한·통합 테스트 완료 |
| Phase 1 동시성 보강 | 인증 단계와 함께 수행 | `users` 생성 후 owner row lock/FK로 SELF 중복·OTHER 10명 경쟁 조건 보강 |
| Phase 2 궁합 계약 | 코드 검증 완료·문서 반영 | 기존 궁합 서비스의 상대/동일인/관계 필수 검증을 유지 |
| Phase 3 수능 typed response | 완료 | 수능 전용 Liner JSON Schema, validator, fallback, 공개 응답 추가 |
| Phase 4 이후 | 예정 | 오늘의 운세부터 진행 |

## 1. 목적과 우선순위

현재 FE는 실제 API adapter를 일부 만들었지만, 세션·인물 구현체가 없어 브라우저에서 실제 요청을 완료할 수 없다. 이 계획의 목표는 다음 두 단계로 나눠 차단 요인을 제거하는 것이다.

1. **로그인 전(오늘 낮~오후):** 인증을 제외하고도 독립 구현·테스트 가능한 인물, 운세 계약, 수능 결과 구조, 오늘의 운세의 기반을 완료한다.
2. **로그인 작업(오늘 저녁):** 카카오 로그인, 서버 세션, `GET /session`, CSRF를 한 묶음으로 구현하고 스테이징에서 FE가 실제 E2E를 시작할 수 있게 한다.

우선순위는 아래와 같다.

| 우선순위 | 작업 | FE에서 막히는 흐름 | 완료 정의 |
| --- | --- | --- | --- |
| P0 | `person` 모듈 및 `ReadingSubjectPort` | 견적·운세 구매가 503 | 실제 저장 인물로 계산·견적·구매 서비스가 동작 |
| P0 | 세션·카카오 로그인·CSRF | 모든 실제 API가 401/403 | 로그인 후 세션 쿠키와 CSRF 헤더로 변경 요청 성공 |
| P1 | 궁합 `relationType` 계약 반영 | 궁합 견적 adapter | enum·예시·검증이 API_SPEC/OpenAPI/코드에서 일치 |
| P1 | 수능 결과 typed response | CSAT-04 결과 화면 | 교시·음식·준비물을 문자열 파싱 없이 표시 가능 |
| P1 | 오늘의 운세 계약·구현 | 오늘의 운세 adapter | `GET /daily-fortunes/today`가 안정적인 응답 반환 |
| P1 | 구매 상태 계약(Q-38) | 처리 중/실패/환급 화면 | BE-A와 상태·소유권·오류 code를 문서로 확정 |

## 2. 현재 상태 확인

### 이미 있는 기반

- `fortune/reading`에는 수능운 및 일반 운세 견적·구매·조회 서비스가 있다.
- 생성은 `fortune/generation/infrastructure/LinerApiProvider`를 통해 Liner API structured output으로 수행한다. Liner 장애 시 deterministic fallback이 있다.
- `ReadingSubjectPort` 인터페이스는 이미 있으며, 소유자와 인물 ID를 받아 `BirthInput`과 표시 이름을 돌려주도록 정의되어 있다.
- 일반 운세와 수능운 서비스는 이 포트를 실제 구매·견적 시점에 호출한다.
- `RelationType`과 일반 운세 API의 궁합 요청 필드는 이미 일부 존재한다.
- `SecurityConfig`는 보호 대상 API와 401/403 envelope를 갖고 있으나 실제 로그인 경로·세션 인증·CSRF 토큰 발급은 없다.

### 아직 없어 FE를 막는 것

- `person` 패키지, migration, repository, controller, `ReadingSubjectPort` 구현체가 없다.
- `auth`, `member` 구현이 없고, `/api/v1/**` 호출을 인증시킬 수 없다.
- `GET /session`이 없고 FE가 보낼 `csrfToken`도 발급되지 않는다.
- 수능 결과는 현재 `sections: [{ key, content }]` 중심이다. 교시별 안내, 음식, 준비물 체크 항목을 화면이 안전하게 렌더링할 typed field가 없다.
- API_SPEC에는 기존 `POST /daily-fortunes` 정의가 남아 있으나, FE 요청의 목표 계약은 body 없는 `GET /daily-fortunes/today`다. 충돌을 해소해야 한다.

## 3. 작업 원칙

1. **계약 선반영:** endpoint, enum, 필수 여부, 오류 응답은 구현 전 API_SPEC와 OpenAPI에 추가한다. FE는 문서에 없는 필드를 코드에 의존하지 않는다.
2. **로그인 전에도 서비스 테스트:** 사람/운세 서비스는 `userId`를 명시적으로 받는 구조를 유지하고, controller 테스트에서는 테스트용 `Authentication`을 주입한다. 저녁의 세션 구현이 낮 작업을 막지 않게 한다.
3. **인물 소유권은 DB 조회 조건으로 강제:** `personId`만으로 읽지 않고 항상 `(owner_user_id, person_id, deleted_at is null)` 조건으로 읽는다.
4. **Liner 원문을 UI 계약으로 노출하지 않음:** Liner의 섹션/문장 형식 변경이 FE를 깨지 않도록, API 응답은 BE-B가 소유한 typed DTO로 매핑한다.
5. **상태·금전 계약은 BE-A 합의 전 임의 확정하지 않음:** Q-38 중 지갑, receipt, 보상, 환급의 최종 상태값은 BE-A의 확인 뒤 문서화한다.
6. **이전 migration은 수정하지 않음:** 신규 테이블·열·제약은 timestamp 기반 후속 Flyway migration으로만 추가한다.

## 4. 실행 순서 및 산출물

## Phase 0 — 계약 동결 및 작업 브랜치 정리

### 목표

FE가 backend 완료를 기다리지 않고 타입/adapter를 만들 수 있도록, 구현 대상의 첫 계약을 문서와 OpenAPI에 표시한다.

### 변경 대상

- `docs/API_SPEC.md`
- springdoc annotation 또는 OpenAPI 생성 설정
- `docs/COMMON_RESPONSE_AND_ERROR_CODES.md` (새 오류 code가 생길 때만)

### 계약에 포함할 내용

1. `people` CRUD 요청/응답 DTO와 권한·삭제 규칙
2. 궁합 `relationType` 값 및 화면 매핑
3. 수능 결과 typed response 초안
4. 오늘의 운세 path, 인증 여부, 대상 인물 지정 방식, 오류 응답
5. 구매 상태 Q-38의 미결 항목과 BE-A/PD 승인 필요 표시

### 완료 조건

- FE가 endpoint, request/response example, required/optional 필드를 읽고 adapter type을 만들 수 있다.
- 아직 확정되지 않은 값은 `TBD`로 감추지 않고 담당자·결정 기한을 적는다.

## Phase 1 — 인물 모듈과 `ReadingSubjectPort` 구현 (P0)

### 1. 도메인 및 저장 모델

새 `person` 패키지를 아래 경계로 만든다.

```text
person/
  api/                 # PeopleController, 요청/응답 DTO
  application/         # PersonService / PersonUseCase
  domain/              # Person, PersonInput, Relation 등
  port/                # PersonRepository
  infrastructure/      # JdbcPersonRepository, ReadingSubjectAdapter
```

#### 최소 저장 필드

정확한 column 명은 ERD와 기존 birth input 형식에 맞추되, 아래 정보는 보존해야 한다.

| 범주 | 필드 |
| --- | --- |
| 식별·소유 | `id`, `owner_user_id`, `is_primary`, `deleted_at`, 생성/수정 시각 |
| 표시 | `display_name`, 타인 관계값 |
| 출생 입력 | 생년월일, 양/음력, 윤달 여부, 출생시각 또는 시간 모름, 성별 |
| 타인 동의 | `third_party_authorization_confirmed`, 확인 시각 |

#### 데이터 규칙

- 본인은 사용자당 한 명만 허용한다. 이미 본인이 있으면 명시적인 오류를 반환한다.
- 타인은 활성 상태 기준 최대 10명이다. 초과하면 `409 PERSON_LIMIT_EXCEEDED`.
- 타인은 `relation` 및 `thirdPartyAuthorizationConfirmed=true`가 필수다.
- 출생 입력은 현재 `BirthInput`의 검증 규칙(양/음력, 윤달, 시간 모름, 유효 날짜)을 재사용한다.
- soft delete한 인물은 목록과 새 견적/구매 대상에서 제외하지만, 과거 `reading_results`의 스냅샷과 조회는 유지한다.
- PATCH로 계산 입력이 바뀌어도 과거 결과의 출생 정보/계산 스냅샷은 바꾸지 않는다.

### 2. API

| API | 목적 | 주요 검증 |
| --- | --- | --- |
| `GET /api/v1/people` | 본인과 활성 타인 목록 | 현재 사용자 소유만 반환 |
| `POST /api/v1/people` | 본인/타인 생성 | 본인 중복, 타인 동의/관계, 최대 10명 |
| `GET /api/v1/people/{personId}` | 상세 조회 | 소유자만, 타인은 404로 은닉 |
| `PATCH /api/v1/people/{personId}` | 인물 수정 | 소유권·입력 재검증 |
| `DELETE /api/v1/people/{personId}` | 인물 비활성화 | 소유권 검사, 과거 결과 유지 |

현재 로그인 전 단계에서는 controller가 기존 `Authentication`에서 사용자 UUID를 얻는 형태로 작성하고, 테스트에서는 인증 principal을 주입한다. 실제 production session principal은 Phase 6에서 같은 UUID를 제공해야 한다.

### 3. 운세 모듈 연결

`ReadingSubjectPort` 구현체는 인물 repository를 직접 노출하지 않고 아래 책임만 제공한다.

```java
OwnedSubject getOwnedSubject(UUID ownerUserId, UUID personId)
```

- 활성 상태의 소유 인물만 조회한다.
- 결과를 `personId`, `displayName`, 완전한 `BirthInput`으로 조립한다.
- 찾지 못하면 `null`을 반환해 기존 운세 서비스가 `RESOURCE_NOT_FOUND`를 처리하게 한다.
- 운세 모듈이 `person` table이나 repository에 직접 의존하지 않도록 한다.

### 4. 검증 및 테스트

- migration을 빈 DB와 기존 schema upgrade 양쪽에서 적용한다.
- 본인 생성/중복, 타인 동의 누락, 타인 10명 초과, 타 사용자 personId 접근, soft delete를 테스트한다.
- `ReadingSubjectPort`가 타 사용자·삭제된 인물에 대해 결과를 주지 않는지 테스트한다.
- `SuneungReadingService` 및 `GeneralReadingService` 통합 테스트에서 Mockito bean을 실제 adapter로 교체한 경로를 추가한다.

### 완료 정의

실제 DB에 생성된 인물로 일반 운세·수능운의 견적 발행까지 성공하며, 포트 부재로 인한 `READING_FULFILLMENT_UNAVAILABLE`가 더 이상 발생하지 않는다.

## Phase 2 — 궁합 `relationType` 계약 완성 (P1)

### 확정된 매핑

| 화면 선택 | API 값 |
| --- | --- |
| 엄마 | `FAMILY` |
| 아빠 | `FAMILY` |
| 애인 | `LOVER` |
| 직접 입력 | `OTHER` |

전체 서버 enum은 `FAMILY`, `FRIEND`, `LOVER`, `CRUSH`, `WORK_SCHOOL`, `OTHER`다.

### 구현 항목

- API_SPEC/OpenAPI의 궁합 견적·구매 요청에 enum 및 example을 넣는다.
- 궁합 견적·구매에서 `counterpartPersonId`, `relationType`, `questionKey`를 모두 필수로 검증한다.
- `personId == counterpartPersonId`는 `INVALID_REQUEST`로 거절한다.
- 두 인물이 모두 현재 사용자가 소유한 활성 인물인지 `ReadingSubjectPort` 경유로 확인한다.
- FE가 화면에서 `relationType`을 직접 선택하는지, 저장된 상대 인물의 relation을 기본값으로 가져오는지는 PD/FE 결정으로 남긴다. 어느 방식을 택해도 API에는 최종 enum 값을 보낸다.

### 완료 정의

API_SPEC, OpenAPI generated schema, controller validation, 일반 운세 통합 테스트가 동일한 여섯 값을 사용한다.

## Phase 3 — 수능 결과 typed response 설계 및 구현 (P1)

### 문제

현재 `GeneratedSection(key, content, sourceFactKeys)`는 생성 검증에는 적합하지만, FE가 `EXAM_PERIODS` 문자열을 파싱해 교시별 카드·음식·준비물 체크리스트를 만들도록 하면 Liner 문장 변경에 취약해진다.

### 권장 공개 API 모델

기존 `sections`는 호환성을 위해 유지하고, 수능 결과에 아래 `suneungGuide`를 **추가**한다. 기존 FE를 깨지 않는 additive 변경이다.

```json
{
  "id": "uuid",
  "fortuneType": "SUNEUNG",
  "sections": [{ "key": "SUMMARY", "content": "..." }],
  "suneungGuide": {
    "summary": "...",
    "examDayGuide": "...",
    "examPeriods": [
      { "period": "1교시", "guidance": "..." }
    ],
    "focusTips": ["..."],
    "foodRecommendations": [
      { "item": "...", "reason": "..." }
    ],
    "preparationChecklist": [
      { "label": "수험표 확인", "category": "DOCUMENT" }
    ],
    "anxietyManagement": ["..."],
    "missingElementAdvice": "..."
  }
}
```

`checked` 같은 UI의 사용자 상태는 서버 결과가 아니라 FE local/session state로 소유한다. 서버는 체크 항목의 안정적인 `label`과 선택적 `category`만 제공한다.

### Liner API 변경

1. 수능 전용 Liner JSON schema를 정의한다.
2. `LinerApiProvider`가 수능 요청일 때 그 schema를 요청한다.
3. 응답 validator는 교시 배열, 음식 배열, 준비물 배열의 필수성·중복·빈 문자열을 검증한다.
4. Liner 실패 시 `DeterministicReadingFallback`도 정확히 같은 구조를 생성한다.
5. 저장용 모델을 확장하거나, 기존 `sections_json`에 typed guide를 함께 저장할 수 있는 버전 호환 모델을 정의한다.
6. API mapper가 저장된 typed guide를 `suneungGuide`로 변환한다.

### 호환성 및 롤백

- 먼저 response에 필드를 추가하고, 기존 `sections` 제거는 FE 전환 뒤 별도 breaking-change 버전에서 한다.
- 과거 저장 결과에 typed guide가 없으면 기존 sections로부터 제한적으로 변환하거나, `suneungGuide: null`을 반환할지 결정한다. 권장안은 안정적 변환이 불가능하면 `null`을 허용하고 FE가 legacy renderer를 사용하게 하는 것이다.
- 새 schema가 Liner에서 검증되지 않거나 응답이 불완전하면 partial 결과를 저장하지 않고 deterministic fallback으로 완결된 guide를 만든다.

### 테스트

- Liner request body가 수능 전용 JSON schema를 보내는지 테스트한다.
- 정상 Liner 응답, 누락된 교시/체크리스트, 중복 key, fallback 각각을 검증한다.
- 구매 API 및 조회 API가 동일한 `suneungGuide`를 반환하는지 테스트한다.
- 기존 sections-only 결과 조회의 호환성 테스트를 추가한다.

### PD 확인 사항

PD에는 아래만 확인 요청한다. 데이터 구조 자체는 이 확인을 기다리지 않고 구현한다.

- `category`의 화면 표기명과 아이콘/순서
- `foodRecommendations`가 음식명만 필요한지, 주의 사항도 필요한지
- 체크리스트의 화면 문구

## Phase 4 — 오늘의 운세 (P1)

### 계약 충돌 해소

기존 API_SPEC의 `POST /daily-fortunes`와 FE 요청 `GET /daily-fortunes/today`가 충돌한다. FE 요청에 맞춰 아래를 canonical endpoint로 한다.

```text
GET /api/v1/daily-fortunes/today?personId={uuid}
```

- request body는 없다.
- `personId`는 query parameter로 둔다. 저장 인물이 여러 명인 사용자도 명시적으로 선택할 수 있다.
- 인증이 필요하고, 대상이 현재 사용자의 활성 인물인지 확인한다.
- 인물 미지정 허용 여부는 제품 결정이 필요하다. 권장안은 **필수**로 두어 본인/타인 혼동을 막는 것이다.

### 최소 응답 계약

```json
{
  "date": "2026-10-11",
  "personId": "uuid",
  "scores": {
    "overall": 72,
    "love": 65,
    "wealth": 74,
    "studyWork": 80,
    "condition": 58
  },
  "disclaimer": "오락·참고용 정보입니다."
}
```

- 모든 지수는 0~100 정수로 검증한다.
- 본문 장문 생성은 하지 않는다. 요청서의 “본문 없음”을 지킨다.
- 초기 버전은 사주 계산 facts + 날짜를 입력으로 하는 결정론적 산출을 권장한다. Liner는 이 endpoint의 필수 의존성이 아니다.
- 장문 조언이 필요해져도 별도 optional field/버전으로 추가하고, score 계약은 유지한다.

### 구현 항목

- `dailyfortune` 패키지 또는 `fortune/daily` 하위 경계를 선택해 service, policy, controller를 추가한다.
- KST 기준 날짜를 `Clock`으로 주입해 테스트 가능하게 한다.
- `ReadingSubjectPort`로 대상 인물의 소유권과 출생 입력을 확인한다.
- score policy를 순수 함수로 만들고 경계값 테스트(0, 100)를 만든다.
- API_SPEC와 OpenAPI에서 기존 `POST /daily-fortunes`를 deprecated 처리하거나 제거한다. 제거는 FE와 합의 후 한다.

### 완료 정의

지정 인물에 대해 KST 오늘 날짜와 0~100 지수를 body 없는 GET으로 반환하며, 다른 사용자/삭제 인물은 조회할 수 없다.

## Phase 5 — 구매 상태 조회 및 책임 경계(Q-38) (P1, BE-A 공동)

### BE-B가 먼저 제안할 계약

| 질문 | 제안 | 최종 승인 |
| --- | --- | --- |
| a. 화면 분기 | `status`가 상태 분기의 기준, `nextAction`은 사용자 행동 안내 | BE-A + FE |
| b. `FAILED` 조합 | 상태 전이표를 작성해 `nextAction`의 조합별 의미를 명시 | BE-A |
| c. `failureCode` | COMMON 문서에 안정적인 enum과 사용자 노출 가능 여부를 분리 | BE-A + BE-B |
| d. POST 응답 | POST와 조회 응답이 같은 purchase snapshot을 반환하도록 제안 | BE-A + BE-B |
| e. 처리 중 404 | 동일 `(buyer, quote)` 구매가 생성된 뒤에는 404가 아니라 현재 상태를 반환 | BE-A + BE-B |
| f. receipt/오케스트레이션 | 운세 결과 생성·상태는 BE-B, 지갑/receipt/보상은 BE-A | BE-A |
| g. `REVIEW_REQUIRED` 문의 | 기술 상태/코드는 BE가 제공, 고객 문구·채널은 PD | PD |

### 책임 경계

- BE-B: `reading_purchases`, 운세 생성 상태, reading result, 재시도 가능성, 생성 실패 원인.
- BE-A: 지갑 차감/보상, payment receipt, 환불, wallet balance, 금전적 최종성.
- Controller owner가 BE-B인 운세 구매에서도 BE-A의 `WalletPurchasePort`만 사용하고 BE-A repository를 직접 읽지 않는다.

### 산출물

- 상태 전이표와 response example을 API_SPEC에 추가한다.
- error code/failure code는 COMMON 문서에 추가한다.
- BE-A 확인 전까지 변경되는 enum을 FE 코드의 하드 의존값으로 만들지 않는다.

## Phase 6 — 저녁 작업: 카카오 로그인, 세션, CSRF (P0)

### 목표

FE가 고정 스테이징 도메인에서 카카오로 로그인한 뒤, 세션 쿠키와 CSRF 토큰으로 모든 보호 API를 호출할 수 있게 한다.

### 구현 순서

1. **사용자·세션 저장소**
   - `users`, `user_sessions` migration 및 repository를 만든다.
   - 사용자에는 카카오 subject, 표시 이름 등 최소 식별 정보만 저장한다.
   - 세션에는 원문 토큰을 저장하지 않고 hash, 만료, 폐기 시각, rotation 정보를 저장한다.
2. **카카오 OAuth adapter**
   - `/api/v1/auth/kakao/authorize?returnTo=...`에서 허용된 내부 상대 경로만 검증한다.
   - OAuth `state`와 returnTo는 서버 측 임시 저장소에 둔다.
   - callback에서 state를 검증하고 카카오 subject로 user upsert 후 세션을 발급한다.
   - 고정 스테이징 domain의 redirect URI를 카카오 개발자 콘솔에 등록한다.
3. **세션 인증 filter**
   - session cookie를 검증해 Spring `Authentication.name`에 사용자 UUID를 넣는다.
   - 만료/폐기된 세션은 인증하지 않는다.
   - 로그인 시 기존 세션/세션 고정 공격을 피하도록 session rotation을 수행한다.
4. **`GET /api/v1/session`**
   - 비로그인도 200으로 `{ authenticated: false, csrfToken }`을 반환한다.
   - 로그인 시 `{ authenticated: true, user, expiresAt, csrfToken }`을 반환한다.
   - FE가 변경 요청 전에 `csrfToken`을 `X-CSRF-Token` header에 넣을 수 있게 한다.
5. **CSRF**
   - Spring CSRF 설정의 header 이름을 `X-CSRF-Token`으로 FE 계약과 일치시킨다.
   - `GET /session`에서 현재 세션/브라우저에 대응하는 토큰을 제공한다.
   - CSRF 실패는 기존 envelope의 `403 CSRF_FAILED`로 반환한다.
   - FE의 재시도 규칙은 403 시 `/session` 한 번 재호출 후 원 요청을 딱 한 번만 재시도한다.
6. **로그아웃**
   - `POST /api/v1/auth/logout`은 인증·CSRF를 요구한다.
   - 현재 세션을 즉시 폐기하고 cookie를 만료한다.
7. **CORS/cookie/staging**
   - FE origin만 허용하고 credential 요청을 허용한다.
   - staging HTTPS 환경에서 `Secure`, 적절한 `SameSite`, cookie path/domain을 검증한다.

### 보안 테스트

- authorize의 외부 `returnTo`를 거절한다.
- callback의 state 불일치/재사용을 거절한다.
- 세션 회전 뒤 이전 cookie가 무효인지 확인한다.
- 비로그인 보호 API는 `401 AUTHENTICATION_REQUIRED`인지 확인한다.
- CSRF 없음/틀린 토큰은 `403 CSRF_FAILED`, 올바른 토큰은 POST/PATCH/DELETE 성공인지 확인한다.
- 다른 사용자의 `people`, reading을 읽거나 수정할 수 없는지 확인한다.
- 로그아웃 뒤 같은 cookie로 호출하면 401인지 확인한다.

## 5. FE 전달·회신 계획

### 오늘 즉시 남길 댓글

구현 시작 전에 아래 내용으로 날짜가 있는 회신을 남긴다. 날짜는 실제 완료 가능 시각으로 채운다.

> BE-B가 로그인 전에는 인물 CRUD 및 `ReadingSubjectPort`, 궁합 relationType 계약, 수능 결과 typed response, 오늘의 운세 계약을 우선 구현한다. 저녁에는 카카오 로그인·`GET /session`·CSRF·로그아웃을 한 묶음으로 구현한다. 각 endpoint는 API_SPEC/OpenAPI를 먼저 반영하고, 스테이징 검증 가능 시점을 별도로 공유한다. 수능 생성은 Liner API structured output을 사용한다.

### FE에 전달할 각 완료 신호

| 완료 항목 | FE에 전달할 내용 |
| --- | --- |
| 계약 선반영 | PR/commit, OpenAPI URL, endpoint 및 breaking/non-breaking 여부 |
| 인물 모듈 | 실제 요청 example, 인물 생성→견적→구매 smoke 결과 |
| 수능 결과 | `suneungGuide` JSON example, legacy `sections` 유지 기간 |
| 오늘의 운세 | query parameter·점수 범위·오류 response example |
| 로그인 | staging base URL, 카카오 로그인 가능 여부, cookie/CSRF 사용 example |
| Q-38 | 합의된 상태표·failure code 문서 링크 |

## 6. 커밋/PR 분할

한 PR에 unrelated 기능을 섞지 않는다. 추천 순서는 다음과 같다.

1. `docs(api): specify BE-B people, relation, daily and session contracts`
2. `feat(person): add people persistence and ReadingSubjectPort adapter`
3. `feat(reading): expose typed suneung guide response from Liner output`
4. `feat(daily-fortune): add authenticated today score endpoint`
5. `docs(purchase): propose purchase state and ownership contract`
6. `feat(auth): add Kakao session authentication and CSRF`

각 PR 본문에는 최소한 아래를 넣는다.

- 변경 endpoint와 request/response 예시
- migration 이름 및 빈 DB/upgrade 검증 결과
- 신규/변경 상태 전이와 error code
- Liner 입력/출력 스키마 변경 여부
- 개인정보/세션/cookie 영향
- rollback 방법
- FE 호환성: additive인지 breaking인지

## 7. 실제 완료 판단 체크리스트

### 로그인 전

- [ ] API_SPEC/OpenAPI에 인물, relationType, 수능 typed response, 오늘의 운세 계약이 있다.
- [ ] 인물을 생성·수정·삭제하고 다른 사용자 인물 접근은 막힌다.
- [ ] 실제 `ReadingSubjectPort` 구현체가 저장된 인물을 운세 서비스에 제공한다.
- [ ] 일반 운세·수능운의 견적/구매에서 포트 부재 503이 사라진다.
- [ ] 궁합이 유효한 relationType과 서로 다른 두 소유 인물을 요구한다.
- [ ] 수능 응답이 Liner 성공/fallback 양쪽에서 typed guide를 제공한다.
- [ ] 오늘의 운세가 KST 기준 날짜와 0~100 점수를 반환한다.

### 로그인 후

- [ ] 카카오 authorize/callback이 고정 스테이징 redirect URI에서 동작한다.
- [ ] `GET /session`이 로그인 상태와 csrfToken을 반환한다.
- [ ] FE가 `X-CSRF-Token`을 실어 POST/PATCH/DELETE를 성공시킨다.
- [ ] CSRF 실패는 한 번만 토큰 갱신 후 재시도한다.
- [ ] 로그아웃 뒤 세션 cookie가 즉시 무효다.
- [ ] 인물 생성 → 견적 → 등껍질 차감/구매 → 수능 결과 조회의 E2E를 스테이징에서 확인한다.

## 8. 외부 결정/차단 요소

| 항목 | 필요한 결정 또는 지원 | 담당 | BE-B 대응 |
| --- | --- | --- | --- |
| 고정 스테이징 URL | HTTPS 도메인, 배포 기동 시점 | BE-A + BE-B | 카카오 redirect URI 및 CORS/cookie 설정 반영 |
| 카카오 개발자 콘솔 | redirect URI, client secret, 동의 항목 | BE-B 또는 팀 계정 관리자 | secret은 저장소에 넣지 않고 environment secret으로 관리 |
| Q-38 금전 상태 | receipt, compensation, refund, failure code | BE-A | 상태표 초안 제시 후 승인 대기 |
| 문의/환급 문구 | `REVIEW_REQUIRED`, 실패/취소 노출 문구 | PD | 기술 code와 사용자 문구를 분리 |
| 수능 UI 표기 | 체크리스트 카테고리/순서/문구 | PD | 데이터 structure는 먼저 제공 |

이 표의 외부 결정이 늦어져도 인물 모듈, relationType 검증, typed 수능 결과의 데이터 구조, 오늘의 운세의 점수 계산, 세션의 로컬 테스트는 계속 진행한다.
