# 0049 : Payout 집행 배치 잡

Spring Batch 잡이 준비된 정산 후보를 수취인별 `Payout`에 `PayoutItem`으로 모은 뒤, 금액이 있는 활성 정산을 집행합니다. 기본 대기 기간은 14일이며, 집행 시 홀딩 지갑에서 수취인 지갑으로 금액을 옮기고 완료된 정산을 표시합니다. 새 DB 초기화 시 데모용으로 후보 날짜를 대기 기간 전으로 조정합니다.

현재 흐름은 회원 이벤트로 정산 회원과 빈 정산을 생성하고, 결제 완료 이벤트로 품목별 정산 후보를 만든 뒤, 배치 잡에서 정산 항목 수집과 지갑 간 집행을 순서대로 처리합니다. 집행 완료 이벤트 뒤에는 수취인별 다음 정산을 생성합니다.

## 설정

`.env.default`를 `.env`로 복사하고 `TOSS_PAYMENTS_SECRET_KEY`에 토스 개발자센터의 **테스트 시크릿 키**를 입력합니다. `.env`는 Git에서 제외됩니다. 실제 키는 소스 코드나 브라우저에 넣지 않습니다.

기본 서버 주소는 `http://localhost:8080`입니다. 내부 글·지갑·주문 품목 조회는 `custom.global.internalBackUrl`을 공유하며, 기본값은 `http://localhost:${server.port:8080}`입니다. 별도 서버로 연결할 때 이 설정을 변경합니다.

## 승인 요청

결제창 인증 성공 후 받은 값을 아래 API에 전달합니다. `orderId`는 `order-3-고유값`처럼 두 번째 구간이 내부 주문 ID인 6~64자의 영문·숫자·`-`·`_` 조합입니다.

`POST /api/v1/market/orders/3/payment/confirm/by/tossPayments`

```json
{"paymentKey":"결제창에서 받은 키","orderId":"order-3-unique123","amount":25000}
```

PG 결제액은 양수이며 주문 판매가를 넘지 않아야 하고, 예치금과 합쳐 주문 판매가 이상이어야 합니다. 승인 응답의 주문번호·금액·결제 키·DONE 상태를 검증하고 승인 정보를 주문에 저장합니다. 같은 주문의 동시 승인 요청은 주문 행 잠금으로 직렬화합니다.

`GET /api/v1/cash/wallets/by-holder/{holderId}`로 지갑을 조회할 수 있습니다. CodePen 테스트 페이지의 POST 요청은 CORS를 허용합니다.

## 구현 흐름

- 회원 가입·수정 이벤트로 `PayoutMember`를 동기화합니다. 최초 생성 시 `PayoutMemberCreatedEvent`를 발행하고 빈 `Payout`을 생성합니다.
- 주문 결제 완료 시 `MarketOrderPaymentCompletedEvent`를 발행합니다. 정산 모듈은 `MarketApiClient`로 `GET /api/v1/market/orders/{id}/items`를 호출합니다.
- 각 주문 품목에 대해 시스템 수수료와 판매자 대금의 `PayoutCandidateItem`을 각각 저장합니다. 판매자 대금은 `Math.round(판매가 × 정산율 / 100)`, 수수료는 판매가에서 판매자 대금을 뺀 값입니다. 기본 정산율은 90%입니다.
- 이벤트 객체는 리스너에서 해석하며 Facade·UseCase에는 DTO와 필요한 값을 전달합니다. 엔티티의 `toDto()`로 변환하고 shared는 boundedContext를 참조하지 않습니다.
- `HasModelTypeCode`와 `ResultType` 인터페이스로 모델 타입 및 결과 응답 규격을 정의합니다.

정산 항목 수집 스텝은 준비된 후보를 모으고, 다음 집행 스텝은 활성 정산을 반복 처리합니다. Spring Batch 메타데이터는 개발·테스트 환경에서 H2 스키마로 초기화합니다.

## 새 DB에서 확인하기

기존 DB에는 지난 이벤트가 자동 재실행되지 않습니다. 초기 데이터를 다시 확인하려면 서버를 정지하고 `db_dev.mv.db`를 백업한 뒤 별도로 보관하고 실행합니다. 새 DB가 생성되며 기존 데이터는 새 DB에 포함되지 않습니다.

초기 데이터에서 회원 6명과 결제된 1번 주문이 만들어집니다. 품목별로 수수료와 판매자 대금 후보가 생성되고, 배치 잡이 후보를 정산에 모아 집행합니다. 완료된 정산에 따라 새 활성 정산도 생성되므로 확인 시점에 따라 정산 행 수와 잔액은 달라집니다.

```sql
SELECT * FROM PAYOUT_MEMBER;                -- 6행
SELECT * FROM PAYOUT_PAYOUT;                -- 수취인별 정산 및 집행 후 새 활성 정산
SELECT * FROM PAYOUT_PAYOUT_CANDIDATE_ITEM;  -- 수집 후 payout_item_id 연결
SELECT * FROM PAYOUT_PAYOUT_ITEM;            -- 정산 후보에서 모인 항목
```

0041의 `createPayout.payee`, 0043의 `orderItem.id` 로그는 이후 단계에서 실제 저장 로직으로 교체되었습니다. 초기화 시 학습용으로 후보 날짜를 대기 기간 이전으로 설정해 곧바로 수집합니다.

## 검증과 범위

`./gradlew --no-daemon clean build`로 검증합니다. 테스트는 메모리 H2와 모의 토스 응답을 사용하며 실제 승인 요청을 보내지 않습니다.

이 단계는 학습용 서버 연동입니다. 사용자 인증·주문 소유자 검증, 결제창 UI, 웹훅 및 승인 후 서버 장애에 대한 조회·복구 처리는 포함하지 않습니다. 실제 사용 전 토스 테스트 키와 결제창에서 발급한 값으로 별도 검증해야 합니다.

- [토스 결제창 연동 가이드](https://docs.tosspayments.com/guides/v2/payment-window/integration)
- [토스 결제 승인 API](https://docs.tosspayments.com/reference#결제-승인)
