# 0045 : PayoutItem 모으기

결제된 주문의 정산 후보를 대기 기간이 지난 뒤 수취인별 `Payout`에 `PayoutItem`으로 모읍니다. 기본 대기 기간은 14일이며, 각 후보를 정산 항목에 연결해 다시 수집되지 않도록 합니다. 새 DB 초기화 때는 데모용으로 후보 날짜를 대기 기간 전으로 바꾸고 수집을 실행합니다.

현재 흐름은 회원 생성 이벤트로 정산 회원과 빈 정산을 만들고, 주문 결제 완료 이벤트에서 주문 품목별 수수료·판매자 대금 후보를 생성한 뒤, 준비된 후보를 정산 항목으로 모으는 단계까지입니다. 실제 지급 처리는 아직 없습니다.

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

정산 후보는 결제일이 기본 대기 기간을 지난 뒤 수취인별 진행 중인 `Payout`에 `PayoutItem`으로 모입니다. 각 후보에는 생성된 정산 항목이 연결되어 중복 수집을 막습니다. 실제 정산금 지급 처리는 아직 구현하지 않았습니다.

## 새 DB에서 확인하기

기존 DB에는 지난 이벤트가 자동 재실행되지 않습니다. 초기 데이터를 다시 확인하려면 서버를 정지하고 `db_dev.mv.db`를 백업한 뒤 별도로 보관하고 실행합니다. 새 DB가 생성되며 기존 데이터는 새 DB에 포함되지 않습니다.

초기 상태에서는 회원 6명, 정산 6개, 결제된 1번 주문의 품목 4개에 대한 정산 후보 8개가 생성됩니다.

```sql
SELECT * FROM PAYOUT_MEMBER;                -- 6행
SELECT * FROM PAYOUT_PAYOUT;                -- 6행, amount = 0
SELECT * FROM PAYOUT_PAYOUT_CANDIDATE_ITEM;  -- 수집 후 payout_item_id 연결
SELECT * FROM PAYOUT_PAYOUT_ITEM;            -- 정산 후보에서 모인 항목
```

0041의 `createPayout.payee`, 0043의 `orderItem.id` 로그는 이후 단계에서 실제 저장 로직으로 교체되었습니다. 초기화 시 학습용으로 후보 날짜를 대기 기간 이전으로 설정해 곧바로 수집합니다.

## 검증과 범위

`./gradlew --no-daemon clean build`로 검증합니다. 테스트는 메모리 H2와 모의 토스 응답을 사용하며 실제 승인 요청을 보내지 않습니다.

이 단계는 학습용 서버 연동입니다. 사용자 인증·주문 소유자 검증, 결제창 UI, 웹훅 및 승인 후 서버 장애에 대한 조회·복구 처리는 포함하지 않습니다. 실제 사용 전 토스 테스트 키와 결제창에서 발급한 값으로 별도 검증해야 합니다.

- [토스 결제창 연동 가이드](https://docs.tosspayments.com/guides/v2/payment-window/integration)
- [토스 결제 승인 API](https://docs.tosspayments.com/reference#결제-승인)
