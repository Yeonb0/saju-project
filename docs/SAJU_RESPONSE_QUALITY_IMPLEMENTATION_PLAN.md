# 사주 응답 품질·정확성 향상 구현 계획

작성일: 2026-10-16  
상태: 제안 — 구현 전 명리 검수·정책 확정 필요

## 1. 목적과 완료 기준

이 계획의 목적은 “그럴듯한 문장”을 늘리는 것이 아니라, 계산 사실과 해석 문장의 일치도를 높이고 변경 뒤에도 결과를 재현할 수 있게 만드는 것이다.

완료로 판단하려면 아래 네 조건을 모두 충족해야 한다.

1. 합의된 만세력 골든 데이터셋에서 사주팔자·일간·오행·십성·절기 경계 결과가 기준값과 일치한다.
2. 유료 운세와 수능운의 모든 섹션은 계산된 fact에 연결되고, fact에 없는 천간·지지·신살·점수·사건을 단정하지 않는다.
3. 결과에는 계산/규칙/생성/콘텐츠 버전이 남아, 과거 구매 결과를 당시 기준으로 재현하고 설명할 수 있다.
4. PostgreSQL에서 동시 생성·재시도·프로세스 중단 뒤에도 동일 purchase의 facts 및 immutable snapshot이 달라지지 않는다.

이 계획은 합격, 건강, 재물, 관계의 결과를 예언하거나 보장하는 기능을 목표로 하지 않는다. 결과는 전통 명리 해석을 생활 조언으로 설명하는 참고 정보로 제한한다.

## 2. 현재 구조와 개선 원칙

현재 백엔드는 대체로 다음 경계를 갖는다.

```text
birth profile
  → BirthInputNormalizer / DeterministicSajuEngine
  → CalculationFacts (사주·오행·십성·신살 등)
  → rule/prompt projection
  → Liner 또는 deterministic fallback
  → validator
  → immutable reading snapshot
```

계산 엔진이 사실을 만들고 생성 모델은 이를 설명해야 한다. 생성 모델이 만세력을 다시 계산하거나 결과의 사실을 보완하는 구조는 만들지 않는다.

적용 원칙:

- 입력 정규화, 절기, 음력/윤달, 자시 경계 같은 계산 정책은 코드·테스트·문서에서 하나의 versioned source of truth로 관리한다.
- 학파 차이가 큰 규칙(신살, 격국, 용신, 대운 해석)은 `ruleVersion`별 명시적 표로 분리한다.
- 외부 생성 호출은 DB transaction 밖에서 실행한다. facts와 generation context는 먼저 durable하게 저장한다.
- 공개/공유 응답에는 원본 birth input, 내부 식별자, 다른 사람 정보가 포함되지 않는다.
- 구매 결과는 생성 당시 facts와 snapshot을 수정하지 않는다. 정책 변경은 새 버전의 새 결과에만 적용한다.

## 3. 품질 모델

품질은 서로 다른 네 축으로 측정한다. 한 지표가 높다고 다른 지표가 자동으로 높아지지 않는다.

| 축 | 질문 | 대표 지표 | 책임 영역 |
| --- | --- | --- | --- |
| 계산 정확성 | 계산된 명식이 기준과 같은가 | 골든 데이터 일치율 | calculation |
| 규칙 정확성 | 적용한 신살/용신 규칙이 합의된 표와 같은가 | rule fixture 일치율 | interpretation rules |
| 사실 충실성 | 문장이 facts 밖의 주장을 하지 않는가 | unsupported claim 비율 | generation/validator |
| 전달 품질 | 사용자가 이해하고 행동 힌트를 얻는가 | 전문가·사용자 평가 | content/product |

운영 대시보드나 릴리스 판정에서 이 값들을 하나의 “정확도” 숫자로 합치지 않는다.

## 4. 단계별 구현 계획

### Phase 0 — 정책·검수 범위 확정

#### 결정할 항목

- 기준 시간대: KST 고정 여부 및 역사적 timezone 데이터의 적용 범위
- 일주 전환: 자정 기준인지 야자시를 별도 취급하는지
- 연주/월주 절기 기준: 입춘, 절입 시각, 경계 시각이 없는 입력 처리
- 음력·윤달 변환 데이터의 출처와 업데이트 정책
- 출생 시각 미상 및 절기 경계일 처리: 현재처럼 입력 거절할지, 낮은 신뢰도 결과로 허용할지
- 신살 목록/판정 규칙/중복 표기 방식
- 용신, 희신, 기신, 격국, 대운, 세운을 출시 범위에 포함할지 및 채택 학파
- 미성년자·타인 사주 입력 및 공유 정책

#### 산출물

- `docs/policies/SAJU_CALCULATION_POLICY.md`: 사람이 읽는 계산 정책
- `docs/policies/SAJU_INTERPRETATION_RULES.md`: rule version별 표와 출처/검수자/적용 범위
- `docs/PENDING_DECISIONS.md`에서 미결 항목을 owner와 결정 기한으로 관리

#### 완료 기준

- 명리 검수자가 정책 문서를 승인한다.
- 제품/법무가 단정 금지 문구와 공개/공유 범위를 확인한다.
- 정책에 없는 경우 “추정”으로 계산하지 않고 명시된 오류 또는 제한된 결과로 수렴한다.

### Phase 1 — 골든 데이터셋과 계산 회귀 기반

#### 데이터셋 구성

실제 사용자 정보는 넣지 않는다. 공개 예시, 검수자가 만든 합성 입력, 또는 사용 허가를 받은 완전 비식별 사례만 사용한다.

권장 초기 규모는 100~300건이며, 아래 비율을 포함한다.

| 구간 | 최소 사례 | 확인 내용 |
| --- | ---: | --- |
| 일반 양력·시각 있음 | 50 | 사주팔자, 일간, 오행, 십성 |
| 음력·윤달 | 20 | 변환 및 윤달 적용 |
| 입춘/절입 전후 | 30 | 연주·월주 전환 |
| 23:00~00:59 | 20 | 일주 경계 정책 |
| 출생 시각 미상 | 15 | 허용/거절 정책 |
| 과거/미래 날짜 경계 | 15 | 지원 범위·오류 처리 |
| 궁합 입력 | 10 | 상대방 입력 분리 및 관계 facts |

#### 파일 구조 제안

```text
backend/src/test/resources/saju-golden/
  v1/
    cases.json
    README.md
    provenance.md
```

`cases.json`에는 raw name, user ID, 전화번호, 실제 생년정보를 넣지 않는다. 입력 ID와 기대 facts만 둔다.

```json
{
  "caseId": "solar-kst-2004-03-15-1432",
  "policyVersion": "manse-2026.10-v1",
  "input": {
    "birthDate": "2004-03-15",
    "birthTime": "14:32",
    "birthTimeUnknown": false,
    "calendarType": "SOLAR",
    "leapMonth": false,
    "gender": "FEMALE"
  },
  "expected": {
    "dayMaster": "WATER",
    "pillars": { "year": "갑신", "month": "정묘", "day": "계사", "hour": "기미" },
    "fiveElements": { "WOOD": 2, "FIRE": 2, "EARTH": 1, "METAL": 1, "WATER": 2 }
  }
}
```

#### 테스트

- `DeterministicSajuEngineGoldenTest`: 모든 case의 facts 전체 비교
- `BoundaryPolicyTest`: 절기/자시/윤달/시각 미상만 별도 실패 메시지로 비교
- 실행 시 calculation version이 fixture와 다르면 무조건 실패하도록 하여, 의도된 정책 변경도 reviewer가 인식하게 한다.

#### 완료 기준

- CI에서 골든 테스트가 항상 실행된다.
- 새 계산 버전은 골든 데이터 변경 PR, 검수 근거, 변경 영향 요약 없이는 병합할 수 없다.

### Phase 2 — 해석 규칙 엔진 분리

#### 목표

계산 facts에서 “무엇을 해석할지”를 결정하는 코드를 LLM prompt에 숨기지 않는다. rule engine은 typed output을 반환하고, prompt/화면/API는 이 결과만 소비한다.

#### 제안 모델

```java
record InterpretationProfile(
    String ruleVersion,
    List<Signal> signals,
    List<BalancePoint> balancePoints,
    List<SectionEvidence> sectionEvidence,
    List<Disclosure> disclosures
) {}

record Signal(String key, Strength strength, List<String> factKeys) {}
record BalancePoint(Element element, Reason reason, List<String> factKeys) {}
record SectionEvidence(SectionKey section, List<String> factKeys, List<String> allowedClaims) {}
```

- `Signal`은 “도화살이 있다”처럼 raw label만 두지 말고 출현 위치·횟수·적용 규칙 버전·근거 fact key를 포함한다.
- `BalancePoint`는 용신을 단정하기 전에 “상대적으로 적은 오행”과 “해석상 보완 포인트”를 분리한다.
- 용신/격국처럼 학파 차이가 큰 기능은 검수 전까지 `BalancePoint`를 넘어선 결론을 외부 응답에 내지 않는다.
- `allowedClaims`는 생성 모델이 사용할 수 있는 해석 범주를 제한한다.

#### 버전 관리

`calculationVersion`과 `ruleVersion`은 별개다.

| 변경 | 올릴 버전 |
| --- | --- |
| 절기/음력/사주 계산 변경 | calculationVersion |
| 신살/오행 보완/해석 분류 변경 | ruleVersion |
| 문장/톤/섹션 프롬프트 변경 | generationVersion 또는 contentVersion |

#### 완료 기준

- 유료 운세의 모든 섹션이 `SectionEvidence`를 보유한다.
- 신살/오행/대운의 각 결과가 어떤 rule version과 facts에서 나왔는지 로그가 아닌 durable snapshot에서 확인된다.

### Phase 3 — 생성 모델 사실 충실성 강화

#### 입력 계약

생성 모델에는 아래만 전달한다.

- 정규화된 계산 facts
- `InterpretationProfile`
- 요청한 섹션 목록과 각 섹션의 allowed fact keys
- 허용되는 톤/분량/금지 표현

전달하지 않는 값:

- 사용자 이름, 연락처, raw birth input
- user/person/purchase ID
- “반드시 합격”, “질병이다”, “헤어진다” 같은 결과 유도 문구
- untrusted한 사용자 입력을 system instruction 위치에 넣은 값

#### validator 보강

1. 구조 검증: 섹션 수·제목·최대 길이·JSON schema
2. fact key 검증: 섹션이 최소 하나의 승인된 근거 key를 제출
3. 엔티티 검증: 응답의 간지/오행/신살 label이 facts 또는 rule profile에 존재
4. 금지 단정 검증: 건강 진단·법률/금융 조언·합격/불합격 확정 표현
5. 중복 검증: 섹션 사이 동일 문장 또는 동일 행동 제안의 반복 제한

validator 실패 시 결과를 일부 공개하지 않는다. 기존처럼 bounded retry 뒤 deterministic fallback으로 수렴시키고, 실패 code와 version만 관측 가능하게 남긴다.

#### 완료 기준

- 생성 fixture에서 facts에 없는 entity 언급이 0건이다.
- malformed/unsupported 응답은 public snapshot으로 저장되지 않는다.
- 재시도·fallback에도 원래 generation context와 rule version을 재사용한다.

### Phase 4 — 콘텐츠 catalog와 화면별 최소 projection

#### 오행 미리보기

현재 추가한 `/api/v1/fortune/element-preview`를 기준으로 한다.

- `counts`, `primaryElement`, `headline`, `description`, `balanceHint`, `contentVersion`만 반환
- headline/description은 오행당 5개, hash 선택으로 안정화
- frontend는 count 합계로 막대 퍼센트를 계산
- “부족” 대신 “채워갈 기운”, “균형 포인트”처럼 비단정 표현 사용

#### catalog 운영 방식

초기에는 코드 상수로 시작할 수 있으나, 문구 수정 빈도가 높아지면 다음으로 전환한다.

```text
content_catalog
  content_key
  locale
  version
  variant_key
  title
  body
  status
  reviewed_by
  reviewed_at
```

새 catalog version은 기존 결과를 수정하지 않는다. 신규 조회/구매만 새 version을 사용한다.

### Phase 5 — 평가와 릴리스 게이트

#### 자동 평가

| 게이트 | 기준 | 실패 시 |
| --- | --- | --- |
| 골든 계산 | 100% 일치 | 릴리스 차단 |
| rule fixture | 100% 일치 | 릴리스 차단 |
| unsupported entity | 0건 | 생성 fallback 또는 릴리스 차단 |
| 금지 단정 문구 | 0건 | 생성 fallback 또는 릴리스 차단 |
| 개인정보 projection | 0건 | 보안 결함으로 처리 |

#### 전문가 샘플 평가

매 release candidate마다 fortune type별 최소 10건을 익명 facts 기준으로 평가한다.

- 계산 근거가 맞는가
- rule 적용이 승인 표와 맞는가
- 문장이 과장/단정적이지 않은가
- 근거와 조언의 연결이 자연스러운가
- 섹션 간 중복이 과도하지 않은가

평가는 1~5점과 구체적 오류 label을 함께 저장한다. 평균 점수만 저장하면 어떤 규칙이나 문구를 고쳐야 하는지 알 수 없다.

### Phase 6 — 운영·복구·관측

#### 저장해야 할 것

- calculationVersion, ruleVersion, generationVersion, contentVersion
- generation key와 input facts fingerprint
- validator 결과 요약과 fallback 여부
- provider 시도 횟수·지연·오류 분류

#### 저장하지 말아야 할 것

- raw birth input, 이름, 전화번호, 공개 링크 token
- provider에 보낸 원문 개인정보
- 전체 prompt/response를 무기한 로그로 보관한 값

#### 알림

- 특정 version의 validator rejection 급증
- fallback 비율 급증
- 골든 데이터 회귀 실패
- 특정 rule version의 사용자 신고/낮은 평가 급증
- generation latency, retry exhaustion, queue backlog

## 5. 데이터베이스·트랜잭션 설계

새 rule/profile 저장이 필요한 경우 additive Flyway migration만 사용한다. 이미 적용된 migration은 수정하지 않는다.

권장 테이블(Phase 2 이후):

```text
reading_interpretation_profiles
  reading_id PK/FK
  rule_version
  profile_json (encrypted 필요 여부를 분류 후 결정)
  facts_fingerprint
  created_at
```

원칙:

- purchase debit/claim과 facts persistence는 짧은 PostgreSQL transaction에서 확정한다.
- Liner/renderer/provider 호출은 그 transaction 밖에서 실행한다.
- profile/snapshot 저장은 lease fence를 확인한 뒤 수행한다.
- 동일 purchase의 재시도는 최초 확정된 generation context·facts·rule version을 읽는다.
- 다중 worker가 있어도 다른 rule version의 결과가 같은 reading을 덮어쓰지 않게 unique/FK/fence를 둔다.

## 6. API 계획

### 유지 API

- `POST /api/v1/fortune/basic`: 상세 facts 확인 용도. 응답 축소 금지(기존 호환성).
- 유료 운세 생성/조회 API: 기존 응답에 버전과 evidence summary를 additive로 추가.

### 추가/확장 API

| API | 용도 | 권한 | 민감 정보 |
| --- | --- | --- | --- |
| `POST /fortune/element-preview` | 확인 화면의 최소 오행 projection | person owner | 반환 금지 |
| `GET /readings/{id}` | 소유 결과와 version/evidence | reading owner | 반환 금지 |
| `GET /public/result-shares/{token}` | 공유 snapshot | bearer | raw birth/ID 금지 |

evidence를 사용자 화면에 모두 공개할 필요는 없다. 다만 `calculationVersion`, `ruleVersion`, `contentVersion`은 문제 조사와 고객 문의 대응을 위해 owner response에 제공하는 것을 권장한다.

## 7. 테스트 계획

### 단위 테스트

- lunar/leap month/solar term boundary normalization
- deterministic engine golden cases
- each rule profile fixture
- content catalog 선택의 안정성·version 변경
- validator: hallucinated entity, 금지 단정, 누락 evidence, 중복 문장

### PostgreSQL 통합 테스트

- 동일 purchase 동시 생성에서 facts/profile/snapshot 1건만 생성
- provider timeout → retry → fallback/REVIEW_REQUIRED
- process interruption을 가정한 lease expiry와 stale completion 거절
- migration from empty DB 및 이전 데이터 legacy read
- owner가 아닌 user의 person/reading/share 접근 거절

### staging/운영 전 점검

- 실제 timezone/ingress/DB와 같은 PostgreSQL에서 골든 suite
- provider 실제 timeout/429/invalid JSON fault injection
- worker 두 프로세스 동시 실행
- p95 generation latency, fallback rate, queue capacity
- DB backup restore 뒤 reading/profile/snapshot 관계 대사

## 8. 권장 실행 순서와 예상 산출물

| 순서 | 작업 | 선행 조건 | 산출물 |
| ---: | --- | --- | --- |
| 1 | 정책 확정 | 명리 담당자 | policy 문서 |
| 2 | 골든 100건 | 정책 1차 | fixture + engine regression |
| 3 | 신살 rule profile 분리 | 골든 기반 | rule engine + tests |
| 4 | validator 확장 | rule profile | rejection/fallback tests |
| 5 | catalog version 운영 | 카피 검수 | content fixtures |
| 6 | version/evidence persistence | migration review | additive schema + PG tests |
| 7 | staging fault/load/restore | 환경 준비 | 측정 보고서 |

처음부터 용신·격국·대운을 동시에 출시하지 않는다. 골든 데이터로 안정화된 사주팔자/오행/십성/신살부터 versioned rule로 고정하고, 각 고급 해석은 별도 rule version과 명리 검수 후 추가한다.

## 9. 역할과 의사결정

| 역할 | 책임 |
| --- | --- |
| 명리 검수자 | 계산/규칙 표, 골든 데이터 정답, 사례 검수 |
| 백엔드 | engine/rule/validator/transaction/recovery |
| 프론트엔드 | 최소 projection 렌더, version 호환, 단정 문구 미노출 |
| 콘텐츠 | 카피 catalog, 금지 표현, 버전 release note |
| 운영 | metric/alert, recovery runbook, restore rehearsal |
| 제품/법무 | 고지 문구, 미성년·타인 데이터·공유 정책 |

## 10. 지금 바로 시작할 작업

1. 명리 검수자와 `SAJU_CALCULATION_POLICY.md`의 절기/자시/윤달/시각 미상 결정을 확정한다.
2. 합성 입력 30건으로 골든 데이터셋 v1 초안을 만들고, 기존 `DeterministicSajuEngineRegressionTest`에 연결한다.
3. 현재 구현한 13종 신살을 rule fixture로 분해해 사례별 기대 결과를 추가한다.
4. 생성 validator에 facts 밖 천간·지지·신살 언급 차단부터 추가한다.
5. 이후 100건까지 골든 데이터를 확장하면서 versioning/운영 지표를 붙인다.

## 11. 이번 문서의 비범위

- 실제 PG사 결제, 현금 환불, 부적 이미지 렌더링 adapter
- 사주를 의료·법률·투자 판단 도구로 만드는 기능
- 실제 사용자 생년정보를 테스트 fixture로 수집·보관하는 작업
- 명리 학파 차이를 제품/검수 승인 없이 하나의 정답으로 단정하는 작업
