# 뿌기사주 API 명세서

> 버전: v1 구현 계약
> 작성일: 2026-10-01
> 최종 수정: 2026-10-13
> 상태: 실행 코드·생성 OpenAPI 기준. 별도로 미구현이라 표시한 항목은 목표 계약이다.
> 참조: [`PRD.md`](./PRD.md), [`FUNCTIONAL_SPEC.md`](./FUNCTIONAL_SPEC.md), [`PENDING_DECISIONS.md`](./PENDING_DECISIONS.md), [`COMMON_RESPONSE_AND_ERROR_CODES.md`](./COMMON_RESPONSE_AND_ERROR_CODES.md)

## 1. 기본 규약

### 구성

- Backend: Spring Boot 3 + PostgreSQL
- Public prefix: `/api/v1`
- Frontend는 Next.js `/api` 프록시를 통해 같은 출처로 요청한다.
- JSON 필드명은 `camelCase`, enum은 `UPPER_SNAKE_CASE`를 사용한다.
- 시간은 ISO 8601 UTC로 전송하고 날짜 의미는 `Asia/Seoul`로 계산한다.
- 외부 리소스 ID는 UUID 문자열이다.

### 인증

- 서버 세션 ID를 `HttpOnly + Secure + SameSite=Lax` 쿠키로 전달한다.
- 세션은 기본 30일 뒤 만료한다. 동시 활성 세션은 5개까지이며 새 로그인은 유지하고,
  한도를 넘으면 가장 오래 사용하지 않은 기존 세션부터 폐기한다.
- 인증 필요 엔드포인트는 세션이 없으면 `401 AUTHENTICATION_REQUIRED`를 반환한다.
- 소유권이 없으면 존재 여부를 숨겨야 하는 리소스는 `404`, 그 외는 `403`을 사용한다.
- 상태 변경 요청은 CSRF 토큰 및 Origin 검증을 적용한다.
- 공개 선물 API는 세션 대신 URL 토큰을 bearer secret으로 사용한다.

### 요청 헤더

| 헤더 | 적용 | 설명 |
|---|---|---|
| `Content-Type: application/json` | JSON 요청 | 본문 형식 |
| `X-CSRF-Token` | 세션 기반 변경 요청 | CSRF 방어 |
| `Idempotency-Key` | 명시된 POST | UUID 권장 |
| `X-Request-Id` | 선택 | 클라이언트 추적 ID |

### 멱등성

- 서버는 `사용자/익명 주체 + 명령 종류 + Idempotency-Key + 요청 본문 지문`을 저장한다.
- 동일 키·동일 본문은 최초 응답을 반환한다.
- 동일 키·다른 본문은 `409 IDEMPOTENCY_KEY_REUSED`다.
- 처리 중인 동일 요청은 `409 IDEMPOTENCY_REQUEST_PROCESSING` 또는 기존 작업 상태를 반환한다.
- PG의 멱등성 보장과 별개로 내부 기록을 유지한다.

### 페이지네이션

```json
{
  "data": {
    "items": [],
    "nextCursor": null
  },
  "traceId": "01K..."
}
```

- `limit`: 기본 20, 최대 100
- `cursor`: 서버가 반환한 opaque cursor

### 공통 응답·오류

- 모든 JSON 성공 응답은 `{ "data": ..., "traceId": "..." }` envelope를 사용한다.
- `204 No Content`는 body를 반환하지 않는다.
- 이 문서의 endpoint별 JSON 예시는 별도 표기가 없으면 `data` 내부 payload만 나타낸다.
- 전체 응답 포맷, HTTP status, 예외 코드의 기준 원본은 [`COMMON_RESPONSE_AND_ERROR_CODES.md`](./COMMON_RESPONSE_AND_ERROR_CODES.md)다.
- 오류 응답은 다음 포맷을 사용한다.

```json
{
  "code": "INSUFFICIENT_BALANCE",
  "message": "등껍질이 부족합니다.",
  "traceId": "01K...",
  "fieldErrors": [
    { "field": "birthDate", "reason": "OUT_OF_RANGE" }
  ],
  "details": {
    "required": 55,
    "balance": 40,
    "shortage": 15,
    "currency": "TURTLE_SHELL"
  }
}
```

## 2. 공통 타입

```ts
type FortuneType =
  | "OVERALL"
  | "LOVE"
  | "WEALTH"
  | "COMPATIBILITY"
  | "SINSAL"
  | "SUNEUNG";

type ProductOption = "READING_ONLY" | "READING_WITH_TALISMAN";
type GiftOption = "TALISMAN_ONLY" | "READING_WITH_TALISMAN";
type CalendarType = "SOLAR" | "LUNAR";
type Gender = "MALE" | "FEMALE" | "UNSPECIFIED";
type RelationType = "FAMILY" | "FRIEND" | "LOVER" | "CRUSH" | "WORK_SCHOOL" | "OTHER";
type TalismanStatus = "PENDING" | "READY" | "FAILED";

type ReadingSectionType =
  | "TEXT"
  | "PERIOD_GUIDANCE"
  | "FOOD_RECOMMENDATION"
  | "CHECKLIST";
```

관계 선택 매핑(2026-10-11, B 답변+FE 회신 반영): 엄마/아빠는 FAMILY, 애인은 LOVER,
직접 입력은 OTHER다. 직접 입력 문자열은 현재 API로 전송하지 않는다.
이는 관계 enum 매핑이며 카테고리별 질문 문구/선택 정책(Q-26)의 최종 결정은 별도다.

- 사주 원국, 간지, 십성, 12운성, 합충형파해, 대운·세운은 오픈소스 만세력 라이브러리와 서버 계산 엔진으로만 계산한다.
- 계산 엔진 출력은 서버 내부의 구조화된 JSON으로 정규화하고 Liner API에 전달한다. Liner는 이 JSON에 근거한 사주 해석·설명 문장만 생성한다.
- Liner에는 계산 결과, 상품·이벤트 맥락, 선택형 관심 항목, 허용 섹션을 전달한다. 원본 생년월일시와 자유 메모 등 문장 생성에 불필요한 개인정보는 전달하지 않는다.
- 요청에는 계산 결과에 없는 필드 목록과 허용 근거 키를 포함한다. Liner가 미제공 명리 정보를 계산·보완·추론하거나 허용 범위 밖의 섹션을 반환하면 검증 실패로 처리한다.
- 자유 메모 수집·저장·표시 API는 현재 제공하지 않는다(2026-10-11 회신 대조). 계산 엔진이나 Liner 입력으로 전달하지 않는다.

### 내부 Liner 생성 계약

Liner 연동은 외부에 공개되는 API가 아니다. 백엔드 내부 어댑터가 다음 형태의 입력을 구성한다.

```json
{
  "generationKey": "sha256:...",
  "calculationVersion": "manse-1.0.0",
  "generationVersion": "liner-reading-v1",
  "fortuneType": "SUNEUNG",
  "referenceDate": "2026-11-19",
  "interestKey": "EXAM_FOCUS",
  "facts": {
    "pillars": { "year": "...", "month": "...", "day": "...", "hour": "..." },
    "tenGods": ["..."],
    "twelveStages": ["..."],
    "interactions": ["..."],
    "luckCycles": ["..."]
  },
  "missingFields": [],
  "allowedSections": ["SUMMARY", "PERIODS", "MEAL", "PREPARATION"]
}
```

Liner 응답은 백엔드가 지정한 JSON 스키마를 따라야 한다.

```json
{
  "sections": [
    {
      "key": "SUMMARY",
      "content": "차분하게 순서를 지키면 집중력을 유지하는 데 도움이 돼요.",
      "sourceFactKeys": ["tenGods[0]", "luckCycles[0]"]
    }
  ],
  "omittedSections": []
}
```

- `facts`에는 계산 엔진이 확정한 값만 넣는다. 예시의 배열·객체는 실제 계산 스키마 확정 후 구체화한다.
- `generationKey`는 정규화된 인물정보, 상품 코드, 기준일/이벤트일, `calculationVersion`, `generationVersion`을 결합해 만든다.
- 같은 `generationKey`에 성공 스냅샷이 있으면 Liner를 다시 호출하지 않고 최초 결과를 반환한다.
- Liner 응답은 허용된 JSON 출력 스키마, 섹션 목록, 금지 표현, 근거 키를 검증한 뒤 저장한다.
- 각 생성 섹션의 `sourceFactKeys`는 요청 `facts`에 실제로 존재하는 경로만 참조할 수 있다.
- 응답에 입력으로 제공되지 않은 명리 정보가 포함되거나 필수 검증을 통과하지 못하면 저장하지 않고 제한 횟수만 재시도한다.

버전 필드의 의미는 다음과 같다.

- `calculationVersion`: 만세력 라이브러리와 서버 계산 규칙 버전
- `generationVersion`: Liner 프롬프트, 모델 설정, 입력·출력 스키마와 검증 규칙 버전
- `contentVersion`: 음식·행운 아이템·부적·고지 문구 등 비생성형 콘텐츠 카탈로그 버전

### PersonInput

```json
{
  "displayName": "민지",
  "birthDate": "2008-03-12",
  "calendarType": "SOLAR",
  "isLeapMonth": false,
  "birthTime": "13:25",
  "birthTimeUnknown": false,
  "gender": "FEMALE"
}
```

- `birthDate`: 1900-01-01~요청일(KST)
- `isLeapMonth`: `LUNAR`일 때만 의미가 있다.
- `birthTime`과 `birthTimeUnknown=true`를 함께 보낼 수 없다.

## 3. 인증·세션

### `GET /auth/kakao/authorize`

- 인증: 불필요
- Query: `returnTo`(내부 상대 경로만 허용)
- 응답: 카카오 OAuth로 302 이동
- 서버가 OAuth `state`와 returnTo를 세션성 저장소에 보관한다.

### `GET /auth/kakao/callback`

- 인증: 불필요
- 카카오 콜백 처리 후 세션 쿠키 발급, 검증된 내부 returnTo로 이동

### `GET /session`

```json
{
  "authenticated": true,
  "userId": "uuid",
  "nickname": "윤진",
  "hasPrimaryPerson": true,
  "expiresAt": "2026-10-31T00:00:00Z",
  "csrfToken": "opaque-token"
}
```

- 응답은 공통 envelope의 `data` 안에 위 필드를 담는다.
- 비로그인일 때 `authenticated=false`이며 나머지 필드는 `null`이다. 이 조회는 401로
  처리하지 않는다.
- 로그인 상태에서 매 조회마다 새 `csrfToken`을 발급한다. 상태 변경 요청은
  `X-CSRF-Token` 헤더로 가장 최근 토큰을 보낸다.

### `POST /auth/logout`

- 인증·CSRF 필요
- 현재 세션 즉시 폐기
- 세션 쿠키를 만료시킨다.
- 응답: body 없는 `204 No Content`

### `GET /sessions`

- 목표 계약이며 현재 실행 API/OpenAPI에는 없다.
- 활성 기기 세션 목록을 제공하려면 사용자가 구분할 수 있는 기기 표시값과 폐기용 공개 ID
  계약을 먼저 확정해야 한다. 원문 세션 토큰은 노출하지 않는다.

### `DELETE /sessions/{sessionId}`

- 목표 계약이며 현재 실행 API/OpenAPI에는 없다. 공개 폐기 ID 계약 전에는 FE가 호출하지 않는다.
- 현재 제공되는 로그아웃은 `POST /auth/logout`으로 현재 브라우저 세션만 폐기한다.

## 4. 사용자·인물

### `GET /me`

```json
{
  "id": "uuid",
  "nickname": "윤진",
  "status": "ACTIVE",
  "walletBalance": 50,
  "hasPrimaryPerson": true,
  "createdAt": "2026-10-01T03:00:00Z",
  "withdrawalRequestedAt": null,
  "scheduledDeletionAt": null
}
```

### `DELETE /me`

- 인증·CSRF 필요
- 유료 잔액이 있으면 `409 PAID_BALANCE_REMAINS`와 환불 안내 정보를 반환한다.
- 탈퇴 접수 성공: `202 Accepted`
- 접수 즉시 모든 활성 세션을 폐기하고 상태를 `WITHDRAWAL_PENDING`으로 바꾼다.
- 접수 30일 후 회원 닉네임·카카오 식별자·인물 입력을 삭제 또는 비식별화한다.
- 결제·원장처럼 법적 보관과 대사에 필요한 기록은 회원 UUID와 함께 유지한다.

### `GET /people`

- 본인과 저장한 사람 목록

```json
{
  "items": [
    {
      "id": "uuid",
      "profileType": "SELF",
      "displayName": "민지",
      "birthDate": "2008-03-12",
      "birthTime": "13:25:00",
      "birthTimeUnknown": false,
      "calendarType": "SOLAR",
      "isLeapMonth": false,
      "gender": "FEMALE",
      "relationType": null
    }
  ]
}
```

### `POST /people`

- 인증·CSRF 필요
- 본인 또는 타인 `PersonInput` 생성
- 타인은 `relation`, `thirdPartyAuthorizationConfirmed=true` 필수
- 타인 최대 10명, 초과 시 `409 PERSON_LIMIT_EXCEEDED`

본인 생성 예시:

```json
{
  "profileType": "SELF",
  "displayName": "민지",
  "birthDate": "2008-03-12",
  "birthTime": "13:25",
  "birthTimeUnknown": false,
  "calendarType": "SOLAR",
  "isLeapMonth": false,
  "gender": "FEMALE",
  "thirdPartyAuthorizationConfirmed": false
}
```

타인 생성 예시:

```json
{
  "profileType": "OTHER",
  "displayName": "엄마",
  "birthDate": "1978-05-10",
  "birthTime": null,
  "birthTimeUnknown": true,
  "calendarType": "LUNAR",
  "isLeapMonth": false,
  "gender": "FEMALE",
  "relationType": "FAMILY",
  "thirdPartyAuthorizationConfirmed": true
}
```

- `profileType`: `SELF | OTHER`
- `relationType`: `FAMILY | FRIEND | LOVER | CRUSH | WORK_SCHOOL | OTHER`
- 화면 관계 매핑: 엄마·아빠 → `FAMILY`, 애인 → `LOVER`, 직접 입력 → `OTHER`
- 본인은 사용자당 활성 프로필 1개만 허용하며 중복 생성은 `409 PRIMARY_PERSON_ALREADY_EXISTS`다.
- 개인정보는 암호화해 저장하고, 다른 사용자의 인물은 존재 여부를 숨기기 위해 `404 PERSON_NOT_FOUND`로 응답한다.

### `GET /people/{personId}`

- 소유자 전용

### `PATCH /people/{personId}`

- 전체 계산 입력이 바뀌면 이후 구매에만 반영한다. 기존 결과는 변경하지 않는다.
- `profileType`은 변경할 수 없다.
- 요청은 `POST /people`에서 `profileType`을 제외한 필드를 사용한다. `OTHER`는 관계와 타인 입력 동의가 계속 필수다.

### `DELETE /people/{personId}`

- 기존 구매 결과는 보존하되 새 구매 대상 목록에서 제거한다.
- 성공 응답은 `204 No Content`다. 삭제된 인물은 이후 목록·견적·구매 대상에서 제외한다.

## 5. 상품 카탈로그

### `GET /products`

Query: `category=TOP_UP|FORTUNE`, 선택적 `fortuneType`. `GIFT`는 후속 구현 대상이다.

구현 범위(2026-10-11, A 로컬): `category=TOP_UP|FORTUNE`을 제공한다. GIFT와 category 생략은 아직 지원하지 않는다.
로그인 없이 현재 활성/판매 기간 내 상품만 조회하고 공통 `data.items` 응답을 사용한다.
FORTUNE은 선택적으로 `fortuneType=OVERALL|LOVE|WEALTH|COMPATIBILITY|SINSAL|SUNEUNG`으로 필터링한다.
category 누락/미지원 값, 알 수 없는 fortuneType, TOP_UP+fortuneType 조합은 400이다.
판매 준비가 안 된 상품은 목록에서 제외된다. 기본 충전/수능 상품은 비활성이며 이번 변경으로 판매를 켜지 않는다.
표시 정보가 미등록된 운세 상품도 제외한다. 이 API는 비활성 상품까지 나열하는 심사용 소개 목록이 아니다.

```json
{
  "items": [
    {
      "code": "LOVE_READING_ONLY",
      "category": "FORTUNE",
      "fortuneType": "LOVE",
      "option": "READING_ONLY",
      "productName": "애정운",
      "price": { "currency": "TURTLE_SHELL", "amount": 10 },
      "regularPrice": null,
      "paidAmount": null,
      "bonusAmount": null,
      "creditedAmount": null,
      "active": true,
      "saleEndsAt": null
    },
    {
      "code": "TURTLE_SHELL_100",
      "category": "TOP_UP",
      "productName": "등껍질 100개 충전",
      "regularPrice": null,
      "fortuneType": null,
      "option": null,
      "saleEndsAt": null,
      "price": { "currency": "KRW", "amount": 10000 },
      "paidAmount": 100,
      "bonusAmount": 14,
      "creditedAmount": 114,
      "active": true
    }
  ]
}
```

- 비활성 상품은 구매할 수 없다.
- 예시는 각 category 응답의 항목을 함께 설명한 것이다. 실제 응답은 요청 category 하나만 포함한다.
- `regularPrice`는 `{currency, amount}` 또는 null이다. 일반 운세+부적/수능운은 정가 20, 판매가 15개다.
- 목록의 `option`을 견적 요청의 `productOption`으로 매핑한다. TOP_UP의 지급량은 완료 증거가 아닌 예정량이다.
- 선물 상품 원화 가격은 `TBD(P-03A)`가 확정된 뒤 등록한다.

### `POST /quotes/fortune/{category}`

선택한 상품·인물·옵션의 서버 견적을 반환한다.

`category`는 `overall`, `love`, `wealth`, `compatibility`, `sinsal` 중 하나다. 각 값은 독립된 OpenAPI operation과 요청 스키마를 갖는다.

종합운·애정운·재물운·신살 요청:

```json
{
  "personId": "uuid",
  "productOption": "READING_ONLY",
  "questionKey": "CURRENT_RELATIONSHIP"
}
```

궁합 요청은 `counterpartPersonId`, `relationType`이 추가로 필수이며 본인과 상대는 달라야 한다.

```json
{
  "personId": "uuid",
  "counterpartPersonId": "uuid",
  "relationType": "LOVER",
  "productOption": "READING_ONLY",
  "questionKey": "OVERALL_MATCH"
}
```

- 일반 운세 `READING_ONLY`는 등껍질 10개, `READING_WITH_TALISMAN`은 15개다.

응답은 `quoteId`, `productCode`, `productOption`, `questionKey`, `charged`, `expiresAt`,
`walletBalance`, `balanceAfter`, `shortage`, `recommendedTopUp`, `productName`, `regularPrice`를 반환한다.

- `charged`는 일반 운세·수능운 공통의 사용 등껍질 값 `{currency, amount}`다.
- `walletBalance`는 조회 시점의 사용 가능한 유료+보너스 잔액이다.
- `shortage`는 부족분이며 잔액이 충분하면 0이다. 부족하면 `balanceAfter`는 null이다.
- `recommendedTopUp`은 보너스 포함 지급량이 부족분 이상인 활성·판매 기간 내 상품 중
  원화 최저가 상품 코드다. 동률은 코드 순이다. 단일 추천 상품이 없으면 null이며 부족분은 유지한다.
- 조회값은 잔액 예약이 아니다. 실제 구매에서 잔액·판매 가능 여부를 다시 검사한다.

수능운은 `POST /quotes/fortune`을 사용한다. Q-34에 따라 최상위 `currency`·`amount`를
`charged`로 통일했다. FE adapter는 아래 계약과 OpenAPI 파일을 사용한다.

```json
{ "personId": "uuid" }
```

```json
{
  "quoteId": "uuid",
  "productCode": "SUNEUNG_READING_WITH_TALISMAN",
  "productName": "수능운 + 부적",
  "regularPrice": { "currency": "TURTLE_SHELL", "amount": 20 },
  "charged": { "currency": "TURTLE_SHELL", "amount": 15 },
  "walletBalance": 7,
  "balanceAfter": null,
  "shortage": 8,
  "recommendedTopUp": "TURTLE_SHELL_10",
  "event": { "type": "CSAT", "date": "2026-11-19" },
  "expiresAt": "2026-10-01T03:10:00Z"
}
```

- 일반 운세의 견적 fingerprint에는 운세·옵션·인물·질문·궁합 상대와 관계가 모두 포함된다.
- 구매 시 동일 fingerprint를 다시 확인하므로 입력을 바꿔 견적을 재사용할 수 없다.

### `GET /quotes/{quoteId}`

- 인증 필요. 본인 운세/추가 부적 견적의 소유자만 조회한다.
- `quoteId`, `productCode`, `charged`, `expiresAt`, `walletBalance`, `balanceAfter`,
  `shortage`, `recommendedTopUp`, `productName`, `regularPrice`를 반환한다. 일반 견적 POST의 옵션·질문, 수능 event는 이 재조회 DTO에 없다.
- 저장된 가격·만료는 유지하고 잔액·부족분·추천은 재조회 시점에 다시 계산한다.
- 상품명·정가도 견적 생성 시 snapshot을 저장한다. 이후 카탈로그 수정은 기존 견적에 반영하지 않는다.
- 변경 전 견적/구 버전 앱이 생성한 견적은 당시 표시 기록이 없어 productName에 상품 코드를 사용하고 regularPrice는 null이다.
- 본인 만료 견적은 `409 QUOTE_EXPIRED`, 타인/미존재는 같은 `404 RESOURCE_NOT_FOUND`다.
- 차감, 지급, 견적 구매 연결을 수행하지 않으며 인물 이름·생년정보·context hash를 노출하지 않는다.
- 선물 다건 견적은 별도 계약이며 이 경로의 이번 구현 범위에 포함하지 않는다.

## 6. 지갑·충전

### `GET /wallet`

```json
{
  "currency": "TURTLE_SHELL",
  "balance": 50,
  "paidBalance": 40,
  "bonusBalance": 10
}
```

인증된 사용자 본인의 사용 가능한 지급분을 조회한다. 만료·미래 지급분은 제외한다.
지갑이 없으면 잔액 0을 반환하며 조회로 지갑을 생성하지 않는다. `expiring` 상세 목록은 후속이다.

### `GET /wallet/transactions`

- Query: `type`, `cursor`, `limit`
- 유형: `TOP_UP`, `SPEND`, `REFUND`, `ADJUSTMENT`, `EXPIRATION`
- 원장 행은 수정하지 않고 역분개 행을 추가한다.

### `POST /top-up-orders`

- 인증·CSRF·`Idempotency-Key` 필요
- 로컬 구현: 주문 생성, 소유자 GET, 승인·원자적 지급·제한된 조회 복구를 제공한다. 실제 PG/FE E2E와 webhook은 미검증/미구현이다.
- 서버 판매 가능 상품의 가격/유료·보너스 수량을 주문에 저장한다. 같은 사용자/키/상품은
  기존 주문 snapshot을 반환하며 같은 키/다른 상품은 409 IDEMPOTENCY_KEY_REUSED다.
- 키는 비어 있지 않은 최대 512자이며 원본 대신 SHA-256을 저장한다. 키 범위는 충전 주문 생성이다.
- PAYMENT_PENDING의 creditedShellAmount는 예정 수량이다. 실제 지급 완료나 현재 잔액이 아니다.

```json
{ "productCode": "TURTLE_SHELL_50" }
```

```json
{
  "orderId": "uuid",
  "orderName": "등껍질 50 + 보너스 6개",
  "amount": 5000,
  "currency": "KRW",
  "paidShellAmount": 50,
  "bonusShellAmount": 6,
  "creditedShellAmount": 56,
  "status": "PAYMENT_PENDING",
  "processing": false,
  "walletBalance": null,
  "grantedShellAmount": null,
  "nextAction": "NONE",
  "failureCode": null
}
```

충전 상품은 다음 여섯 개를 제공한다.

| productCode | 유료 | 보너스 | 총 지급 | 가격 |
|---|---:|---:|---:|---:|
| `TURTLE_SHELL_10` | 10 | 0 | 10 | 1,000원 |
| `TURTLE_SHELL_30` | 30 | 3 | 33 | 3,000원 |
| `TURTLE_SHELL_50` | 50 | 6 | 56 | 5,000원 |
| `TURTLE_SHELL_100` | 100 | 14 | 114 | 10,000원 |
| `TURTLE_SHELL_300` | 300 | 46 | 346 | 30,000원 |
| `TURTLE_SHELL_500` | 500 | 80 | 580 | 50,000원 |

- 승인 완료 시 유료분과 보너스분을 별도 원장 행 또는 동일 거래의 구분 가능한 lot으로 기록한다.
- 지급 만료는 유료 5년/보너스 1년(Asia/Seoul 달력 기준)이다. 차감은 만료가 빠른 지급분부터, 만료가 같으면 보너스부터, 그것도 같으면 먼저 지급된 지급분부터 한다 (확정 P-02A, 2026-10-12 사용자 재확인).

### `POST /top-up-orders/{orderId}/confirm`

- 인증·CSRF·`Idempotency-Key` 필요

```json
{
  "paymentKey": "provider-payment-key",
  "orderId": "uuid",
  "amount": 5000
}
```

- 서버 저장 주문 금액과 PG 금액을 비교한다.
- 클라이언트 금액을 신뢰하지 않는다.
- 200은 처리 결과 응답, 202는 아직 처리 중이다. HTTP 200만으로 충전 완료를 표시하지 않는다.
- **status=CREDITED만** 지급 완료다. 이때 processing=false, grantedShellAmount=실제 지급량,
  walletBalance=해당 지급 시점 잔액 snapshot, nextAction=VIEW_WALLET이다. 현재 잔액은 GET /wallet으로 다시 조회한다.
- 결과 불명은 PAYMENT_PENDING/processing=true/nextAction=POLL이며 잔액·실제 지급량은 null이다.
- creditedShellAmount는 기존 계약의 예정 지급량이며 지급 증거가 아니다.
- 기본 최대 5회 확인 후에도 불명인 경우 PAYMENT_PENDING/processing=false/CONTACT_SUPPORT,
  failureCode=PAYMENT_REVIEW_REQUIRED로 남는다. 실패·환불·새 결제를 자동으로 안내하지 않는다.
- 검증된 거절/취소는 FAILED/CANCELED, processing=false, RETRY_PAYMENT와 PAYMENT_DECLINED/PAYMENT_CANCELED다.
- failureCode는 주문 결과 필드이며 공통 ApiError.code와 구분한다.
- 승인 준비·점유 저장 후 PG는 DB 트랜잭션 밖에서 호출한다. 주문→실행→지갑→lot 잠금 아래
  지급분·원장·잔액·receipt·CREDITED를 하나의 트랜잭션으로 커밋한다.
- 자동 조회 복구는 TOSS_RECOVERY_ENABLED=true일 때만 동작한다. 기본 false이며 adapter 준비 없이 켜지 않는다.
- 같은 주문 재전송은 저장된 대응을 재사용하고 지급을 반복하지 않는다. 원본 paymentKey는 저장하지 않는다.

### `GET /top-up-orders/{orderId}`

- 인증 필요, 타인/없는 주문은 동일 RESOURCE_NOT_FOUND(404). 생성/승인과 동일 응답 DTO다.
- 상태: `CREATED`, `PAYMENT_PENDING`, `PAID`, `CREDITED`, `FAILED`, `CANCELED`, `REFUNDED`, `REFUND_PENDING`, `PARTIALLY_REFUNDED`
- `REFUND_PENDING`은 미완료/검토 중, `PARTIALLY_REFUNDED`는 원 결제액보다 적은 잔여 유료분 환불이 확인된 주문의 외부 projection이다. DB 주문 상태는 환불 완료 시 `REFUNDED`이며 원 결제액/지급량 스냅샷은 유지한다. 실제 현금 반환액은 소유자 `GET /top-up-orders/{orderId}/refund`의 `amount`와 `state=REFUNDED`·`completedAt`으로 확인한다. 일반 사용자 환불 POST는 제공하지 않는다.

### `GET /top-up-orders`

- 인증한 사용자가 소유한 충전 주문만 `createdAt DESC, orderId DESC` 순서로 반환한다.
- `size` 기본값은 20, 허용 범위는 1~50이다. `cursor`는 서버가 반환한 불투명 문자열을 그대로 사용한다.
- 응답은 `{ items, nextCursor, hasNext }`이며 각 항목은 단건 조회와 같은 상태 projection 및 `createdAt`을 포함한다.
- 결제키, 멱등키 원문·해시, 공급자 원문과 다른 사용자의 주문은 반환하지 않는다.

### `POST /webhooks/toss-payments`

- 아래는 목표 계약이며 현재 구현된 엔드포인트가 아니다.
- 공개 인터넷 엔드포인트지만 PG 서명·이벤트 ID를 검증한다.
- 웹훅 원문에 민감정보를 남기지 않는다.
- 이벤트 ID를 멱등 처리한다.

## 7. 오늘의 운세

### `GET /daily-fortunes/today`

- 인증 필요
- request body 없음
- Query: `personId` (필수, 현재 사용자가 소유한 활성 인물)
- 날짜는 서버의 KST 오늘을 사용한다.

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
  "content": {
    "oneLineFortune": "작은 자신감을 행동으로 옮기기 좋은 하루예요.",
    "luckyColor": "민트",
    "luckyItem": "책갈피",
    "luckyPlace": "편안한 휴식 공간",
    "todayAction": "비교보다 지금 할 수 있는 한 걸음에 집중해 보세요."
  },
  "calculationVersion": "manse-2026.10-v1",
  "contentVersion": "daily-2026.10-v2"
}
```

- 모든 지수는 0~100 정수다.
- `content`는 한줄 운세, 행운 색·아이템·장소, 오늘의 행동을 담는다. 값은 UI 표시용
  짧은 문자열이며 장문 생성 결과나 개인 출생 입력을 포함하지 않는다.
- 동일 인물·날짜·계산/content 버전의 결과는 항상 같다.
- Liner API를 호출하지 않고 서버의 결정론적 만세력 계산 결과와 KST 날짜만 사용한다.
- 인물 없음·삭제·소유권 없음은 존재 여부를 숨기기 위해 `404 PERSON_NOT_FOUND`로 응답한다.

## 8. 유료 운세 구매·결과

### `POST /fortune/basic`

- 인증·CSRF 필요
- 결제 전 정보 확인 화면의 기본 원국·오행 분석에 사용한다.
- 요청에 포함된 인물정보는 계산에만 사용하며 이 API 자체는 저장하지 않는다.
- 응답에는 사용자 ID, 이름, 원본 생년월일시를 포함하지 않는다.

저장된 인물을 선택한 FE는 원문 생년정보 대신 아래 입력을 사용한다.

```json
{ "personId": "uuid" }
```

- `personId`와 원문 생년 필드를 함께 보내거나 두 입력 모두 누락하면 `400 VALIDATION_FAILED`다.
- 서버는 로그인한 사용자의 인물만 조회한다. 타인/미존재는 같은 `404 RESOURCE_NOT_FOUND`다.
- 실제 인물 저장소 adapter로 현재 사용자가 소유한 활성 인물을 조회한다. 저장소 의존성 장애는
  `503 READING_FULFILLMENT_UNAVAILABLE`로 처리할 수 있지만, 정상 구성에서 미연결 상태는 아니다.
- 저장된 절기 경계일 인물의 출생 시간이 미상이면 원문 입력과 같은 `422 BIRTH_TIME_REQUIRED_AT_TERM`이다.

기존 원문 입력도 유지한다.

```json
{
  "birthDate": "2004-03-15",
  "birthTime": "14:32",
  "birthTimeUnknown": false,
  "calendarType": "SOLAR",
  "leapMonth": false,
  "gender": "FEMALE"
}
```

응답은 원국 네 기둥, 일간, 오행 분포, 십성, 12운성, 합·충·형·파·해와 `calculationVersion`을 반환한다. 시간 미상인 경우 시주와 시주 기반 값은 `null`이다. 절기 경계일에 시간이 미상이면 `BIRTH_TIME_REQUIRED_AT_TERM`을 반환한다.

### `POST /reading-purchases/{category}`

- 인증·CSRF·`Idempotency-Key` 필요
- 잔액 차감은 이 명령 내부에서 수행한다.

```json
{
  "quoteId": "uuid",
  "personId": "uuid",
  "productOption": "READING_ONLY",
  "questionKey": "CURRENT_RELATIONSHIP"
}
```

견적 발급 때 사용한 필드를 동일하게 전송해야 한다. 궁합은 견적과 마찬가지로 `counterpartPersonId`, `relationType`이 필수다. 수능운은 기존 `POST /reading-purchases` 계약을 유지한다.

현재 구매 HTTP 공통 경계는 사용자별 `Idempotency-Key`를 요청 경로와 파싱된 요청 내용에
영속적으로 연결한다. 같은 키로 견적/선택/경로를 바꾸면 `409 IDEMPOTENCY_KEY_REUSED`다.
같은 키·같은 요청은 기존 구매 처리로 전달한다. 이것만으로 최초 응답 재사용이나
만료 후 완료 재조회, 중단 구매 재개까지 구현된 것은 아니다.
키는 1~512자(공백만인 값 제외)이며 입력/인증/CSRF 검증을 통과한 요청에 적용한다.
세부 범위와 보존/배포 제한은 `backend/docs/PURCHASE_REQUEST_BINDINGS.md`를 참고한다.

| fortuneType | 허용 questionKey | 생성 section 순서 |
|---|---|---|
| `OVERALL` | `OVERALL_FLOW`, `RELATIONSHIPS`, `STUDY_AND_WORK`, `WEALTH`, `CONDITION` | `SUMMARY`, `CURRENT_FLOW`, `RELATIONSHIPS`, `STUDY_AND_WORK`, `WEALTH_FLOW`, `CONDITION`, `LUCKY_POINT`, `MISSING_ELEMENT` |
| `LOVE` | `CURRENT_RELATIONSHIP`, `NEW_RELATIONSHIP`, `RECONCILIATION`, `MARRIAGE` | `SUMMARY`, `CURRENT_FLOW`, `GOOD_PERIOD`, `CAUTION`, `ACTION_TIP`, `MISSING_ELEMENT` |
| `WEALTH` | `OVERALL_WEALTH`, `INCOME`, `SPENDING`, `INVESTMENT`, `CAREER_FINANCE` | `SUMMARY`, `WEALTH_FLOW`, `INCOME`, `SPENDING_CAUTION`, `GOOD_PERIOD`, `ACTION_TIP`, `MISSING_ELEMENT` |
| `COMPATIBILITY` | `OVERALL_MATCH`, `COMMUNICATION`, `CONFLICT`, `LONG_TERM_POTENTIAL` | `SUMMARY`, `MATCH_STRENGTH`, `MATCH_CONFLICT`, `COMMUNICATION`, `RELATIONSHIP_TIP`, `MISSING_ELEMENT` |
| `SINSAL` | `OVERALL_SINSAL`, `RELATIONSHIPS`, `WORK_AND_STUDY`, `WEALTH` | `SUMMARY`, `SPECIAL_STARS`, `BALANCING_SPECIAL_STARS`, `MISSING_ELEMENT` |

각 상세 API는 내부 section 배열을 그대로 노출하지 않고 다음과 같은 고정 필드로 변환한다.

| category | 맞춤 결과 필드 (`meta`, `summary` 제외) |
|---|---|
| `overall` | `currentFlow`, `relationships`, `studyAndWork`, `wealthFlow`, `condition`, `luckyPoint`, `missingElement` |
| `love` | `currentFlow`, `goodPeriod`, `caution`, `actionTip`, `missingElement` |
| `wealth` | `wealthFlow`, `income`, `spendingCaution`, `goodPeriod`, `actionTip`, `missingElement` |
| `compatibility` | `counterpart`, `matchStrength`, `matchConflict`, `communication`, `relationshipTip`, `missingElement` |
| `sinsal` | `specialStars`, `balancingGuide`, `missingElement` |

성공 `201`:

```json
{
  "purchaseId": "uuid",
  "readingId": "uuid",
  "status": "FULFILLED",
  "compensationStatus": "NOT_REQUIRED",
  "nextAction": "VIEW_READING",
  "calculationVersion": "manse-1.0.0",
  "generationVersion": "liner-general-reading-v2",
  "contentVersion": "love-2026.10.08",
  "charged": { "currency": "TURTLE_SHELL", "amount": 10 },
  "balance": 40,
  "reading": {
    "meta": {
      "id": "uuid",
      "productOption": "READING_ONLY",
      "subjectDisplayName": "민지",
      "questionKey": "CURRENT_RELATIONSHIP",
      "generationMode": "LINER",
      "disclaimers": ["FOR_ENTERTAINMENT"],
      "talisman": null
    },
    "summary": { "content": "...", "sourceFactKeys": ["dayMaster.hanja"] },
    "currentFlow": { "content": "...", "sourceFactKeys": ["fiveElements.counts.WOOD"] },
    "goodPeriod": { "content": "...", "sourceFactKeys": ["luck.annual[0].year"] },
    "caution": { "content": "...", "sourceFactKeys": ["relations.values[0].type"] },
    "actionTip": { "content": "...", "sourceFactKeys": ["dayMaster.element"] },
    "missingElement": { "content": "...", "sourceFactKeys": ["fiveElements.missing[0]"] }
  }
}
```

- Liner 호출 또는 응답 검증이 3회 실패하면 같은 허용 섹션의 결정론적 fallback 문구를 저장한다. fallback까지 실패하면 구매를 실패 처리하고 차감을 역분개한다.
- 동일한 `Idempotency-Key` 또는 `generationKey`의 재요청은 외부 호출을 중복 실행하지 않고 진행 중 상태나 최초 완료 결과를 반환한다.
- 부적 fulfillment는 `PENDING` 또는 `READY`를 반환하며, 부적 생성 시작 자체가 실패하면 구매를 완료하지 않고 등껍질을 복구한다. 비동기 완료 방식은 `TBD(F-06)` 확정에 따라 확장한다.

### `GET /reading-purchases/by-quote/{quoteId}`

- 인증 필요, 구매자 본인만 조회할 수 있다.
- `(buyerUserId, quoteId)` 조합은 최대 한 구매를 갖는다.
- 아직 구매 row가 생성되지 않았거나 다른 사용자의 견적이면 `404 PURCHASE_NOT_FOUND`다.
- POST가 purchase row를 commit한 뒤에는 처리 중이어도 404가 아니라 현재 상태를 반환한다.

```json
{
  "purchaseId": "uuid",
  "quoteId": "uuid",
  "readingId": null,
  "status": "FAILED",
  "compensationStatus": "PENDING",
  "nextAction": "WAIT",
  "failureCode": "READING_GENERATION_FAILED",
  "createdAt": "2026-10-11T03:00:00Z",
  "fulfilledAt": null
}
```

화면의 상태 분기 기준은 `status`이며, `nextAction`은 버튼·안내 행동을 결정한다.

| status | compensationStatus | nextAction | 의미 |
| --- | --- | --- | --- |
| `CREATED`, `DEBITED`, `GENERATING` | `NOT_REQUIRED` | `WAIT` | 구매 처리 중 |
| `FULFILLED` | `NOT_REQUIRED` | `VIEW_READING` | 결과 열람 가능 |
| `FAILED` | `PENDING` | `WAIT` | 차감 보상 진행 중 |
| `FAILED` | `REVIEW_REQUIRED` | `CONTACT_SUPPORT` | 자동 보상 판단 불가, 운영 확인 필요 |
| `FAILED` | `NOT_REQUIRED` | `RETRY_PURCHASE` | 차감 전에 실패했으므로 보상 불필요 |
| `FAILED` | `COMPLETED` | `RETRY_PURCHASE` | 이전 데이터 호환 조합이며 신규 흐름은 `REFUNDED`로 정규화 |
| `REFUNDED` | `COMPLETED` | `RETRY_PURCHASE` | 보상 완료, 다시 구매 가능 |

- `compensationStatus`: `NOT_REQUIRED`, `PENDING`, `COMPLETED`, `REVIEW_REQUIRED`
- `nextAction`: `WAIT`, `VIEW_READING`, `RETRY_PURCHASE`, `CONTACT_SUPPORT`
- POST 구매 응답에도 `compensationStatus`와 `nextAction`을 반환한다.
- receipt와 지갑 차감·보상 실행은 BE-A의 `WalletPurchasePort` 책임이다. BE-B는 운세 purchase 상태와 결과 생성 orchestration을 소유한다.

#### 수능운 결과 구조

수능운의 기존 `sections`는 이전 클라이언트 호환을 위해 유지한다. 신규 화면은 문자열을 파싱하지 않고 `suneungGuide`를 사용한다. 구매 성공 응답의 `reading`과 수능운 상세 조회는 같은 구조를 반환한다.

```json
{
  "sections": [{ "key": "SUMMARY", "content": "...", "sourceFactKeys": ["dayMaster.hanja"] }],
  "suneungGuide": {
    "summary": "...",
    "examDayGuide": "...",
    "examPeriods": [
      { "period": "BEFORE_ENTRY", "guidance": "..." },
      { "period": "KOREAN", "guidance": "..." },
      { "period": "MATH", "guidance": "..." },
      { "period": "LUNCH", "guidance": "..." },
      { "period": "ENGLISH", "guidance": "..." },
      { "period": "ELECTIVES", "guidance": "..." },
      { "period": "FINAL_CHECK", "guidance": "..." }
    ],
    "focusTips": ["..."],
    "foodRecommendations": [
      { "item": "평소 잘 맞았던 식사", "reason": "..." }
    ],
    "preparationChecklist": [
      { "id": "ADMISSION_TICKET", "label": "수험표", "category": "DOCUMENT" },
      { "id": "IDENTIFICATION", "label": "신분증", "category": "DOCUMENT" }
    ],
    "anxietyManagement": ["..."],
    "missingElementAdvice": "..."
  }
}
```

- `preparationChecklist`의 체크 여부는 FE 상태이며 서버 결과에 저장하지 않는다.
- checklist category는 `DOCUMENT`, `WRITING_TOOL`, `TIMEPIECE`, `FOOD`, `CLOTHING`, `OTHER`다.
- 신규 Liner 수능 요청은 수능 전용 JSON Schema로 `suneungGuide`를 생성한다.
- Liner 실패 시 deterministic fallback도 동일한 `suneungGuide` 구조를 보장한다.
- 과거 저장 결과에 `suneungGuide`가 없을 수 있으므로 FE는 전환 기간 동안 legacy `sections` 표시를 지원한다.

### `GET /readings/{category}`

- 본인 구매 결과 목록, cursor pagination
- Query: `personId`, `cursor`, `size` (기본 20, 최대 50)
- URL의 카테고리에 해당하는 결과만 반환하므로 별도 `fortuneType` 필터는 받지 않는다.
- 정렬: `createdAt DESC`, 동일 시각에는 `id DESC`

```json
{
  "items": [
    {
      "id": "uuid",
      "productOption": "READING_ONLY",
      "subjectDisplayName": "민지",
      "counterpartDisplayName": null,
      "questionKey": "CURRENT_RELATIONSHIP",
      "generationMode": "LINER",
      "createdAt": "2026-10-08T00:00:00Z"
    }
  ],
  "nextCursor": null,
  "hasNext": false
}
```

### `GET /readings/{category}/{readingId}`

```json
{
  "meta": {
    "id": "uuid",
    "productOption": "READING_ONLY",
    "subjectDisplayName": "민지",
    "questionKey": "OVERALL_MATCH",
    "calculationVersion": "manse-2026.10-v1",
    "generationVersion": "liner-general-reading-v2",
    "contentVersion": "compatibility-2026.10.08",
    "generationMode": "LINER",
    "createdAt": "2026-10-08T00:00:00Z",
    "disclaimers": ["FOR_ENTERTAINMENT"],
    "talisman": null
  },
  "counterpart": { "personId": "uuid", "displayName": "서준", "relationType": "LOVER" },
  "summary": { "content": "...", "sourceFactKeys": ["dayMaster.hanja"] },
  "matchStrength": { "content": "...", "sourceFactKeys": ["counterpart.dayMaster.hanja"] },
  "matchConflict": { "content": "...", "sourceFactKeys": ["relations.values[0].type"] },
  "communication": { "content": "...", "sourceFactKeys": ["dayMaster.element"] },
  "relationshipTip": { "content": "...", "sourceFactKeys": ["counterpart.fiveElements.missing[0]"] },
  "missingElement": { "content": "...", "sourceFactKeys": ["fiveElements.missing[0]"] }
}
```

- 계산 원국·내부 시드·원본 생년정보는 필요한 범위를 넘어 노출하지 않는다.
- 상세 결과는 소유자만 조회할 수 있으며 다른 사용자의 ID도 `READING_NOT_FOUND`로 숨긴다.
- 다른 카테고리 경로로 결과를 요청해도 `READING_NOT_FOUND`를 반환한다.
- 기존 `GET /readings`, `GET /readings/{readingId}`는 수능운과 이전 클라이언트의 마이그레이션 호환용으로 유지한다.

### `POST /readings/{readingId}/shares`

- 인증·소유권·CSRF·`Idempotency-Key` 필요
- 카카오톡 공유하기에 사용할 비식별 공유 리소스와 템플릿 데이터를 생성한다.
- 공유 리소스에는 사용자가 선택한 공개 가능 결과 output·브랜드 이미지·서비스 진입 URL을 포함할 수 있으며 인적정보는 포함하지 않는다. 전체/일부 섹션 범위는 `TBD(G-11)`이다.

### `POST /readings/{readingId}/talisman-purchases`

- 인증·CSRF·`Idempotency-Key` 필요
- `READING_ONLY` 사주 상품을 구매한 소유자만 가능
- 15 등껍질을 서버에서 확인·차감

## 9. 부적

### `GET /talismans`

- 로그인 사용자의 구매·수신 부적 목록
- Query: `animal`, `type`, `cursor`
- 동일 동물·종류 그룹에 `count` 제공

### `GET /talismans/{talismanId}`

```json
{
  "id": "uuid",
  "type": "FOCUS",
  "animal": "RAT",
  "status": "READY",
  "thumbnailUrl": "https://cdn...",
  "description": "...",
  "acquiredAt": "2026-10-20T01:02:03Z",
  "source": "PURCHASE",
  "share": {
    "title": "뿌기가 전하는 행운 부적",
    "description": "부적을 확인해 보세요.",
    "imageUrl": "https://cdn.../talisman-share.webp",
    "webUrl": "https://service.example/share/opaque-id"
  }
}
```

- `share`는 카카오톡 공유하기용이며 개인정보와 비공개 원본 signed URL을 포함하지 않는다.

### `POST /talismans/{talismanId}/download-url`

- 인증·권한·CSRF 필요
- 10분 signed URL 반환

```json
{ "url": "https://...", "expiresAt": "2026-10-20T01:12:03Z" }
```

### `POST /talismans/{talismanId}/shares`

- 인증·권한·CSRF·`Idempotency-Key` 필요
- 개인정보와 비공개 원본 URL이 없는 카카오톡 공유용 리소스를 생성한다.

### `GET /shares/{shareId}`

- 인증 없이 접근 가능한 공유 랜딩 데이터다.
- 불투명 난수 ID를 사용하며 공개용 제목·설명·이미지·서비스 CTA만 반환한다.
- 회원 ID, reading/talisman 내부 ID와 생년정보는 반환하지 않는다. 공개할 결과 범위는 `TBD(G-11)`에 따른다.

### `POST /daily-fortunes/today/share?personId={personId}`

- 인증·CSRF·인물 소유권 확인이 필요하다.
- 날짜, 지수 5개, 한줄 운세, 행운 색·아이템·장소만 비식별 스냅샷으로 저장한다.
- 같은 사용자·인물·KST 날짜의 철회되지 않고 만료되지 않은 공유는 같은 링크를 반환한다.
- 응답은 `shareId`, 공개 `token`, `expiresAt`, 비식별 `fortune`이며 이름·생년월일시·성별·personId·userId를 포함하지 않는다.

### `GET /daily-fortune-shares/{token}`

- 인증 없이 조회한다. 32바이트 난수 기반 URL-safe token을 사용한다.
- 생성 시점의 비식별 스냅샷만 반환한다. 24시간이 지나면 `SHARE_EXPIRED`, 철회되면 `SHARE_REVOKED`다.

### `DELETE /daily-fortune-shares/{shareId}`

- 인증·CSRF가 필요하며 생성자만 철회할 수 있다. 타인 또는 없는 shareId는 동일한 `SHARE_NOT_FOUND`다.

### `POST /talismans/{talismanId}/claim`

- 로그인한 선물 수신자가 공개 토큰으로 열람한 부적을 계정에 연결한다.
- 같은 획득 건을 중복 연결하지 않는다.

## 10. 선물 견적·주문

> 2026-10-13 구현 계약: 선물은 등껍질 결제이며 수능 1명만 지원한다. 견적·암호화 초안·`POST /gift-orders/{orderId}/purchase`·구매 상태 GET·`POST /gift-orders/{orderId}/cancel`을 local/test 기본 비활성으로 구현했다. ACTIVE는 차감/권한 저장이며 발송/콘텐츠 완료가 아니다. 현재 계약과 남은 작업은 `backend/docs/GIFT_IMPLEMENTATION_20261013.md`, backend 전용 OpenAPI는 `backend/docs/openapi/gift-operations-v1.json`을 따른다. 과거 원화 confirm은 지원하지 않는다. 11장의 공개 수신·파일 API는 설계 초안으로 미구현이며 FE 연결 근거로 사용하지 않는다.

### `POST /gift-quotes`

```json
{
  "fortuneType": "SUNEUNG",
  "option": "TALISMAN_ONLY"
}
```

- 응답에는 등껍질 가격(`charged`), 제공 콘텐츠, 견적 만료 시각, 현재 잔액·부족량·충전 추천을 포함한다. 인증·CSRF가 필요하다.
- 응답의 `includedContents`에는 옵션별 제공물(`LETTER`, `TALISMAN`, `READING`, `SUNEUNG_CHECKLIST`)을 명시한다.
- 선물 옵션은 `TALISMAN_ONLY`와 `READING_WITH_TALISMAN`만 허용하며 `READING_ONLY`는 거부한다.
- 수능 `TALISMAN_ONLY`에는 편지·부적·수능 체크리스트가 포함되고 전체 수능 사주 결과는 포함되지 않는다.
- 1차는 수능 1명만 지원한다. 부적 단독 10개, 사주+부적 15개이며 상품은 기본 비활성이다. `GET /gift-quotes/{quoteId}`는 본인 견적 조회다.

### `POST /gift-orders`

- 인증·CSRF·`Idempotency-Key` 필요

```json
{
  "quoteId": "uuid",
  "recipientName": "민지",
  "recipientPhone": "01012345678",
  "senderName": "윤진",
  "message": "긴장하지 말고 잘 보고 와!"
}
```

- 수신자 생년정보를 받지 않는다.
- `recipientPhone`은 알림톡 발송을 위한 필수 입력이다. 저장·조회·로그에서는 암호화 또는 마스킹하며 보관기간은 `TBD(G-10A)`다.
- 메시지는 200 code point·제어문자 검증을 수행한다. 금칙어 정책은 미구현이다. 새 CREATED 초안은 미차감이며 `charged=null`이다.

### `POST /gift-orders/{orderId}/purchase`

- 인증·CSRF·`Idempotency-Key` 필요
- 본문 없이 명시적인 등껍질 구매 의도를 등록하고 기존 차감 receipt로 재개한다. 선물에 원화/토스 confirm은 없다.
- 차감 확인 후 gift 권한·token hash·암호화 발송 의도를 저장한다. ACTIVE는 이 저장의 완료이며 실제 알림톡 발송/콘텐츠 생성 완료가 아니다.
- 원문 token은 암호화 outbox에만 저장하고 일반 응답에 반환하지 않는다. 실제 발송·삭제 worker는 후속이며 운영 활성화는 금지한다.
- `GET /gift-orders/{orderId}/purchase`로 같은 금융 상태를 조회한다. PENDING/CANCEL_PENDING의 POST 응답은 202다.

```json
{
  "orderId": "uuid",
  "giftId": "uuid",
  "status": "ACTIVE",
  "chargedShells": 10,
  "returnedShells": null,
  "approvedAt": "2026-10-13T00:00:00Z",
  "nextAction": "NONE"
}
```

### `GET /gift-orders`

- local/test 구현: 구매자의 보낸 선물 목록. size 기본 20, 1~50, cursor는 서버 응답을 그대로 사용한다.
- items/nextCursor/hasNext 응답, 금융 status와 deliveryStatus를 분리한다. chargedShells/deliveryStatus/accessExpiresAt은 null 가능하다. 상세 계약은 `backend/docs/GIFT_DELIVERY_HANDOFF_20261013.md`를 따른다.
- 원문 토큰과 전체 휴대전화번호를 반환하지 않는다.

### `GET /gift-orders/{orderId}`

- 구매자 소유 주문·선물 상태
- 실제 금융 상태와 charged를 반환한다. expiresAt은 원 견적 기한이며 선물 링크 만료일이 아니다.

### `GET /gift-orders/{orderId}/delivery`

- 구매자 전용 발송 상태와 canResend/remainingResends/nextResendAt. 실제 대행사 adapter 미연결 시 canResend=false다.
- 발송 상태는 PENDING/IN_FLIGHT/RETRY/UNKNOWN/SENT/FAILED/REVIEW_REQUIRED/CANCELED이며 금융 상태와 별개다.

### `POST /gift-orders/{orderId}/deliveries`

- local/test 구현, 실제 대행사 adapter는 미연결
- 인증·CSRF·`Idempotency-Key` 필요
- 구 링크를 폐기하고 같은 수신자에게 새 token의 발송 job을 예약한다. 202는 예약이지 전달 완료가 아니다. token 원문은 반환하지 않는다.
- 주문별 재발송+재발급 합산 일 3회·1분 간격, 같은 요청 키 재전송은 횟수를 중복 사용하지 않는다. UNKNOWN/IN_FLIGHT/검토 상태에는 수동 요청하지 않는다.
- 실제 구매 차감 시각부터 30일 미만에만 새 발송을 허용한다. 30일 이후 전화번호 정리는 별도 worker로 수행하며 기존 90일 링크 권한과 결과 불명 조회는 유지한다.

### `POST /gift-orders/{orderId}/link-reissues`

- local/test 구현, 실제 대행사 adapter는 미연결
- 인증·CSRF·`Idempotency-Key` 필요
- 기존 토큰을 폐기하고 새 토큰의 발송 작업을 예약한다. 202이며 선물 만료일은 연장하지 않는다.
- 응답은 발송 상태와 요청 가능 여부이고 새 원문 토큰을 반환하지 않는다. 마스킹 번호는 주문/목록에서 조회한다.

### `POST /webhooks/alimtalk`

- 설계·미구현
- 알림톡 대행사의 서명과 이벤트 ID를 검증하고 멱등 처리한다.
- 발송 성공·실패·수신 불가 상태를 선물 전달 기록에 반영한다.
- 원문 휴대전화번호와 선물 토큰을 로그에 남기지 않는다.

### `POST /gift-orders/{orderId}/cancel`

- 구매자 인증·CSRF·Idempotency-Key가 필요하다. 실제 등껍질 차감 후 7일 이내, 최초 콘텐츠 제공 전 ACTIVE 선물만 취소한다.
- 링크/발송 차단과 취소 의도를 먼저 저장한다. 원 거래 유료/보너스를 원 지급분·만료일로 반환하고 receipt를 확인한 뒤 CANCELED다. 현금 환불이 아니다.
- CANCEL_PENDING은 미완료이며 POST 202다. 5회 실패 소진은 REVIEW_REQUIRED다. 공개 콘텐츠 제공 API는 아직 없으며 B의 최초 제공 처리는 같은 선물 잠금과 조정해야 한다.

## 11. 공개 선물 API

> 이 장은 수신/생성/파일 접근의 설계이며 아직 실행 API가 없다. 내부 token 검증만 추가했고 임의 부적 접근은 차단한다. 최초 제공·취소의 경쟁과 B 콘텐츠 연결을 구현하기 전 공개하지 않는다.

경로의 `{token}`은 로그·분석·에러 보고에서 마스킹한다.

### `GET /gifts/{token}`

```json
{
  "status": "UNCLAIMED",
  "recipientName": "민지",
  "senderName": "윤진",
  "fortuneType": "SUNEUNG",
  "option": "TALISMAN_ONLY",
  "message": "긴장하지 말고 잘 보고 와!",
  "recipientSubmissionRequired": true
}
```

- 메시지는 API로 전달하되 OG 메타데이터에는 사용하지 않는다.
- `TALISMAN_ONLY`인 수능 선물은 부적·편지·수능 체크리스트만 노출하고 전체 사주 섹션은 반환하지 않는다.
- 만료/폐기 토큰은 각각 410을 반환하고 선물 상세를 노출하지 않는다.

### `POST /gifts/{token}/recipient`

- `Idempotency-Key` 필요
- `PersonInput` 제출, 최초 1회만 허용
- `409 ALREADY_SUBMITTED`에는 기존 입력값을 반환하지 않는다.

### `GET /gifts/{token}/result`

```json
{
  "giftStatus": "ACTIVE",
  "contentKinds": ["LETTER", "TALISMAN", "SUNEUNG_CHECKLIST"],
  "talismanId": "public-opaque-id",
  "checklistId": "public-opaque-id",
  "expiresAt": "2027-02-27T14:59:59Z"
}
```

- 수신자 정보 제출과 결과 생성이 완료된 뒤 단건 선물 전체를 반환한다.
- `TALISMAN_ONLY`에는 `readingId`를 반환하지 않는다. `READING_WITH_TALISMAN`에는 `READING`과 `readingId`를 추가한다.

### `POST /gifts/{token}/talismans/{talismanId}/download-url`

- 유효 토큰과 공개 상태 확인 후 10분 signed URL 반환

## 12. 관리자 API

아래는 PR #25 및 2026-10-13 고객센터 환불 후속으로 구현한 관리자 금융 API다. 기본 비활성이며 실제 로그인 세션과 DB 운영자 권한이 필요하다. 조회도 감사 기록을 남기고 POST에는 CSRF·복구 권한·사유·요청키가 필요하다. 전체 경로는 `/api/v1` 기준이다.

| Method | Path | 기능 |
|---|---|---|
| GET | `/admin/top-up-orders` | 충전 주문 조회 |
| GET | `/admin/refunds` | 현금 환불 상태 조회 |
| GET | `/admin/wallets/{userId}` | 지갑 저장 잔액·사용 가능 지급분 점검 |
| GET | `/admin/wallets/{userId}/transactions` | 원장 조회 |
| GET | `/admin/incidents` | 운영 이상 조회 |
| GET | `/admin/audit-events` | 감사 기록 조회 |
| POST | `/admin/top-up-orders/{orderId}/reconcile` | PG 조회에 근거한 주문·환불 상태 재확인. 새 PG 승인/취소 호출 없음 |
| GET | `/admin/recovery-commands/{commandId}` | 재확인 작업 결과 조회 |
| POST | `/admin/refund-cases` | 본인/주문 대조 후 고객센터 접수 기록. PG 호출/회수 없음 |
| GET | `/admin/refund-cases/{caseId}` | 접수·환불 상태와 금액·회수량 조회 |
| GET | `/admin/refund-cases/{caseId}/quote` | 현재 잔여 유료분 환불액·보너스 회수량 확인 |
| POST | `/admin/refund-cases/{caseId}/execute` | 확인한 금액·수량 재검사, 회수 예약 후 PG 전액/부분 취소 |

관리자 전용 계약은 `backend/docs/openapi/admin-operations-v1.json`에 있다. 접수는 `orderId/customerUserId/supportReference/receivedAt`, 실행은 quote의 `expectedAmount/expectedPaidShells/expectedBonusShells`를 받는다. 임의 금액 지정이 아니라 서버 계산값의 확인이며 변경 시 409 `REFUND_QUOTE_CHANGED`다. 실제 결제액 × 잔여 유료분 ÷ 최초 유료 지급량(원 미만 올림), 수수료 0, 남은 보너스 회수·사용 보너스 무공제다. 최초 실행 권한·감사·영속 요청키 및 미완료 복구를 적용한다. 임의 잔액 조정·제한 해제·생년정보 수정·가상계좌 계좌 입력은 제공하지 않는다. 세부 절차: `backend/docs/CUSTOMER_CENTER_REFUNDS_20261013.md`.

## 13. 상태 머신

### 충전 주문

`CREATED → PAYMENT_PENDING → PAID → CREDITED`

종료/예외: `FAILED`, `CANCELED`, `REFUNDED`

### 본인 구매

`CREATED → DEBITED → GENERATING → FULFILLED`

실패: `FAILED → REFUNDED`

#### 구매 상태 조회 계약

- GET /api/v1/reading-purchases/by-quote/{quoteId}만 제공한다. `purchaseId` 단건
  조회 endpoint는 제공하지 않는다.
- 실제 사용자 소유권 검사, 구매가 없거나 다른 사용자의 quote는 `404 PURCHASE_NOT_FOUND`다.
- 이미 생성된 구매 조회/replay는 견적 만료와 무관하다. 최초 구매에만 만료 검사를 적용한다.
- 공통 envelope의 `data`는 다음 모양이다.

```json
{
  "purchaseId": "uuid",
  "quoteId": "uuid",
  "status": "GENERATING",
  "readingId": null,
  "compensationStatus": "NOT_REQUIRED",
  "failureCode": null,
  "nextAction": "WAIT",
  "createdAt": "2026-10-10T10:00:00Z",
  "fulfilledAt": null
}
```

readingId는 FULFILLED에서만 값이 있다. compensationStatus는 NOT_REQUIRED/PENDING/COMPLETED/REVIEW_REQUIRED,
nextAction은 WAIT/VIEW_READING/RETRY_PURCHASE/CONTACT_SUPPORT다. 잔액은 GET /wallet로 별도 조회한다.
REFUNDED는 보상 receipt 확인 후에만 저장한다. 새 조회 계약의 완료 표시는
status=REFUNDED와 compensationStatus=COMPLETED가 일치해야 하며 오류 code만으로 표시하지 않는다.
lease 만료만으로 FAILED/환급으로 전이하지 않고, 환급 이후 늦은 생성 결과를 노출하지 않는다.
전체 상태 조합은 8장 표를 따른다. 지갑 차감/보상 receipt는 BE-A, 구매 상태와 결과 생성은 BE-B가 담당한다.

### 선물 주문·선물

`CREATED → PAYMENT_PENDING → PAID → ACTIVE → COMPLETED`

예외: `CANCELED`, `REFUNDED`, `EXPIRED`

### 알림톡 전달

`PENDING → SENT | FAILED`

알림톡 전달 실패는 결제·선물 활성 상태와 별도로 기록한다. 자동 재시도 횟수와 SMS 대체 여부는 `TBD(G-10)`다.

### 수신 상태

`UNCLAIMED → RECIPIENT_SUBMITTED → ACTIVE → COMPLETED → EXPIRED`

링크 재발급: 기존 토큰 `REVOKED`

## 14. 보안·개인정보

- 선물 토큰은 32바이트 이상 난수 Base64URL로 만들고 gift의 영구 저장값은 해시만 유지한다. 알림톡 발송용 원문 URL은 암호화된 outbox에 한시 저장 후 삭제한다.
- 알림톡 수신 휴대전화번호는 암호화 저장하고 조회 화면·로그·Sentry·분석 데이터에서 마스킹한다.
- 비밀번호·PG 시크릿·OAuth 시크릿·토큰 원문을 로그에 기록하지 않는다.
- URL 경로 토큰은 액세스 로그·Sentry에서 마스킹한다.
- R2 원본은 비공개이며 signed URL은 10분 만료다.
- 관리자 개인정보 조회와 모든 금전성 변경을 감사 로그에 남긴다.
- PostHog에는 실제 인적정보·결과 본문·메시지를 전송하지 않는다.
- 공개 API에 rate limit과 abuse 방어를 적용한다.

## 15. OpenAPI·호환성

- Springdoc으로 OpenAPI JSON을 생성한다.
- 프론트는 `openapi-typescript`로 타입을 생성하며 수동 API 타입을 만들지 않는다.
- v1에서 필드 삭제·의미 변경·enum 재사용을 금지한다.
- 새 enum 값은 프론트의 unknown 처리 전략과 함께 배포한다.
- P0 `TBD`가 해결되기 전 선물 가격·제공 개시·환불 관련 스키마는 draft로 표시한다.

## 16. 확정 전 TBD

| 결정 | 영향 API |
|---|---|
| 선물 원화 가격 | `/products`, `/gift-quotes`, 주문 승인 |
| 미사용 링크 만료 | 공개 gift 조회·410 정책 |
| 선물 제공 개시 기준 | refund 요청·응답·취소 가능 상태 |
| 보너스/유료 등껍질 차감 순서 | 지갑 원장·환불 가능 잔액 계산 |
| 알림톡 대행사·템플릿·재시도·SMS 대체 | 선물 주문 승인·전달 상태·웹훅 |
| 알림톡 전화번호 보관기간 | 선물 주문 데이터·삭제 작업·관리자 조회 |
| 신살 결과·부적 구성 | 상품 카탈로그·Liner 스키마·결과 응답 |
| 개인 결과 공유 범위·만료·철회 | `/shares`, 카카오 공유 템플릿, 공개 랜딩 |
| 자시·역법·띠 기준 | person 검증·reading 생성·talisman 동물 |
| 부적 동기/비동기 | 구매 응답·talisman 상태·폴링 |
| CSRF·세션 초과 | 인증 필터·세션 API |
