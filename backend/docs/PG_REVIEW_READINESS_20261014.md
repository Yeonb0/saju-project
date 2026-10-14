# PG 심사 우선 개발·검증 인계

## A 구현 완료 범위

직접 공유 개발은 #41에서 30일/일 3회/1분 재발급·멱등성·공유 문서 정렬을 완료했다. 공개 수신/생성·부적·claim·FE 화면 완주는 B/FE와 별도다. PG 심사 우선 트랙은 본인 콘텐츠/등껍질 충전이며 선물 활성화와 분리한다.

토스 API adapter의 기존 test_gsk 전용·production 일괄 차단을 환경별 키 검증으로 바꿨다.

| 환경 | 허용 key 종류 | 추가 조건 |
|---|---|---|
| local/test/staging | test_gsk 또는 test_sk | live-enabled=false. 공유 환경은 공식 HTTPS 주소만 허용 |
| production/prod | live_gsk 또는 live_sk | 공식 HTTPS 주소·명시적 TOSS_LIVE_ENABLED=true, local/test/staging 혼합 금지 |

TOSS_ADAPTER=disabled와 TOSS_LIVE_ENABLED=false는 기본이다. test 단독 환경에서만 loopback HTTP stub을 허용한다. 잘못된 키·호스트·프로필·timeout은 기동 실패이고 오류에 원문 key/URL을 넣지 않는다. 이 구현은 실제 merchant/카드사 승인·운영 활성화를 수행하지 않는다.

기존 승인/조회/취소의 멱등 키·결과 불명 조회·원장 receipt 기반 지급은 유지한다. 키 규칙 변경은 지갑·주문 상태를 강제로 변경하지 않는다. 실제 provider 통신은 DB 잠금 밖이며 timeout은 실패/추가 지급으로 단정하지 않는다.

## 현재 심사 준비 source 점검

루트에서 `./backend/scripts/Test-PgReadiness.ps1`을 실행했다. .env/서버 키를 읽거나 네트워크/결제 API를 호출하지 않는다. 공개 사업자 source·정책 component·공유 OpenAPI만 확인한다. `-Strict`는 source 누락 또는 수동 확인 항목이 남으면 exit 2다. 결과는 backend/build/pg-readiness.json이며 버전 관리하지 않는다.

| 항목 | 현재 source 상태 | 다음 담당/작업 |
|---|---|---|
| 사업자 상호/대표/등록번호/주소/email | source 존재, 제출 서류와 실제 표시 일치는 미검증 | 대표·FE 확인 |
| 고객센터 전화번호 | null | 대표·PD 확정 → FE 사업자 source 반영 |
| 통신판매업 신고/면제 표시 | null | 대표가 적용 여부·토스 카드사 요구 확인 → PD 문구 확정 |
| 호스팅 제공자 | null | A가 실제 업체/법인명·국가/리전 확정 → PD/FE 반영 |
| terms/privacy/refund 본문 | 공통 PolicyPage의 빈 article | PD 정책 v3 확정 → FE 본문·시행일 반영 |
| 상품/충전 생성/승인/주문 조회 계약 | source 존재 | 실제 staging에서 동작·노출 검증 |

이 결과는 PG 승인 증거가 아니며 SOURCE_PRESENT도 렌더링/외부 서비스 정상 작동을 뜻하지 않는다. 공개 약관/사업자 값은 임의로 만들지 않았다. 빈 정책을 임시 알림톡 v2 원고로 채우지도 않았다.

## 다음 검증 순서

1. 대표/PD: 사업자 값·정책 v3·등껍질 충전 상품의 토스 심사 분류/한도/환불 조건을 확인한다. 토스 안내의 특정 충전 한도를 우리 서비스에 임의로 적용하지 않는다.
2. A/B/FE: 고정 HTTPS frontend/backend proxy, 카카오 callback·쿠키·CSRF와 만료 복귀를 확인한다.
3. 사용자에게 실제 테스트 키의 안전한 서버 설정을 확인받은 뒤 테스트 결제창→인증→승인→CREDITED/원장 1회 지급→주문 조회를 확인한다. 시크릿을 채팅/FE에 보내지 않는다.
4. 관리자 CS 접수→환불 실행→PG 조회·원장 대사와 승인 응답 유실/새로고침/중복 요청/금액 불일치를 확인한다. 반환 완료는 영속 receipt로 판단한다.
5. 운영자·삭제/복구/대사 worker 설정·감사와 migration 적용 이력을 점검한다. PR33의 앞 번호 migration 적용 절차를 별도 DB에서 먼저 확인한다.
6. 대표가 실제 PG 계약/카드사 심사 상태를 확인한다. production 전환은 승인 확인·운영 점검 후 별도 작업이다.

## 확인한 공식 근거

- [토스 API key](https://docs.tosspayments.com/reference/using-api/api-keys): test/live와 gsk/sk 구분 및 클라이언트/시크릿 분리.
- [토스 Payments API](https://docs.tosspayments.com/en/api-guide): 테스트 test_sk/test_gsk, 운영 live_sk/live_gsk와 Basic 인증.
- [2026-10-06 카드사 심사 체크리스트](https://www.tosspayments.com/blog/articles/53711?from=main): 사업자/사이트·테스트 결제창·충전 분류 조건과 계약 이후 심사. 계약/심사 진행은 아직 직접 확인하지 않았다.

실제 승인 가능한 날짜·업종/충전 한도·정책 법적 적합성을 이 문서로 확정하지 않는다.

## 검증 기록

별도 PostgreSQL 17과 synthetic loopback provider로 payment/config/confirmation/refund 선별 120건 통과. 최종 `check bootJar`는 685건 중 673 통과·12 선택 제외·실패/오류 0이다. 라이브 키 설정 검사에는 synthetic 문자열만 사용했고 provider를 호출하지 않았다. 점검 스크립트는 source blocker 4개·수동 확인 6개를 보고했으며 Strict exit 2를 확인했다. 실제 사이트·키·PG 결제/계약/심사 상태는 확인하지 않았다.
