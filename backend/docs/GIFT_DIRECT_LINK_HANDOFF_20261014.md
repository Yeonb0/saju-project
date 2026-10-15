# A 직접 공유 마무리

## 확정된 1차 개발 범위

사용자 2026-10-14 개발 지시: 1차는 구매자가 직접 링크를 복사/카카오 공유하고 알림톡은 2차로 보존한다. 전화번호는 받지 않는다. 실제 구매 차감부터 30일 미만, 주문별 KST 하루 3회, 1분 간격으로 링크를 재발급한다. 기존 정책 v2와 화면 문구는 PD의 v3 반영이 필요하다.

## 구매자 API

- `GET /api/v1/gift-orders/{orderId}/link`: 로그인 구매자 본인만 조회한다.
- `POST /api/v1/gift-orders/{orderId}/link-reissues`: 로그인·소유권·CSRF·Idempotency-Key가 필요하다.
- 응답: orderId, giftId, tokenVersion, expiresAt, shareUrl, reused와 추가 필드 canReissue, remainingReissues, nextReissueAt.
- canReissue/remainingReissues/nextReissueAt는 서버의 현재 허용 상태다. FE에서 날짜·횟수를 다시 계산하지 않는다. 30일 종료·취소·만료면 canReissue=false다.
- 최초 발급/새 재발급 후 원문 조회기간은 24시간이며 이후 shareUrl=null이다. 이는 수신 권한 만료가 아니다. 새 링크가 필요하면 서버가 허용하는 명시적 재발급만 한다.
- 재발급은 이전 hash를 폐기하고 token version을 올리되 원래 수신/재열람 기한을 연장하지 않는다.
- 동일 요청 replay는 새 token/횟수를 만들지 않는다. 후속 재발급으로 바뀐 경우 이전 요청 replay는 이전 version과 shareUrl=null을 반환하며 최신 링크를 대신 노출하지 않는다.
- 서로 다른 주문에 같은 키를 동시에 사용하면 하나만 성공하고 다른 쪽은 409 IDEMPOTENCY_KEY_REUSED다. 실패 주문 token은 회전하지 않는다.
- 제한은 409 GIFT_LINK_REISSUE_LIMIT_EXCEEDED, 허용 기간/상태 밖은 409 GIFT_LINK_REISSUE_NOT_ALLOWED다. 이 code로 분기하고 서버 message를 최종 화면 문구로 고정하지 않는다.

## FE/B 경계

`/g#token=...`는 권한 링크다. FE는 서버가 준 URL을 그대로 사용하고 관측·로그·스토리지에 원문을 남기지 않는다. fragment는 요청 경로·Referer·OG에 실리지 않는다. 공개 안내에 이름이 필요하면 body token으로 B 공개 API에서 받는다. 공개 API·생성·부적·claim은 B 영역이고 이번 변경으로 완주했다고 표시하지 않는다.

수신 접수 이후 재발급 허용 범위는 기존 구현을 유지했다. 이 개발 지시는 추가 권리 제한/수신 정보 초기화 규칙을 정하지 않는다. 정책·FE는 링크 재발급이 기존 링크를 폐기한다는 설명을 포함해야 한다.

## 배포와 유지보수

기존 알림톡 table/worker는 삭제하지 않고 기본 비활성으로 보존한다. #33 관리자/토큰 파기·키 전환과 #39 고객센터 조회를 기반으로 한다. 운영 선물 암호화는 아직 local/test 제한이 있으며 실제 운영 키·B 수신 완주·개인정보 파기 준비를 검증한 뒤 별도 활성화해야 한다. PG 심사용 본인 구매/충전 트랙과 선물 활성화를 분리한다.
