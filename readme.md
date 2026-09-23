# 0036 : 토스 페이먼츠 연동

토스 승인 성공 후 기존 주문 결제 이벤트를 발행하여 구매자 예치금과 보관용 지갑을 갱신합니다.

## 설정

`.env.default`를 `.env`로 복사하고 `TOSS_PAYMENTS_SECRET_KEY`에 토스 개발자센터의 **테스트 시크릿 키**를 입력합니다. `.env`는 Git에서 제외됩니다. 실제 키는 소스 코드나 브라우저에 넣지 않습니다.

기본 서버 주소는 `http://localhost:8080`입니다. 서버 포트를 바꾸면 내부 지갑 조회 주소인 `cash.api.base-url`과 글 조회 주소인 `post.api.base-url`도 함께 설정합니다.

## 승인 요청

결제창 인증 성공 후 받은 값을 아래 API에 전달합니다. `orderId`는 `order-3-고유값`처럼 두 번째 구간이 내부 주문 ID인 6~64자의 영문·숫자·`-`·`_` 조합입니다.

`POST /api/v1/market/orders/3/payment/confirm/by/tossPayments`

```json
{"paymentKey":"결제창에서 받은 키","orderId":"order-3-unique123","amount":25000}
```

PG 결제액은 양수이며 주문 판매가를 넘지 않아야 하고, 예치금과 합쳐 주문 판매가 이상이어야 합니다. 승인 응답의 주문번호·금액·결제 키·DONE 상태를 검증하고 승인 정보를 주문에 저장합니다. 같은 주문의 동시 승인 요청은 주문 행 잠금으로 직렬화합니다.

`GET /api/v1/cash/wallets/by-holder/{holderId}`로 지갑을 조회할 수 있습니다. CodePen 테스트 페이지의 POST 요청은 CORS를 허용합니다.

## 검증과 범위

`./gradlew --no-daemon clean build`로 검증합니다. 테스트는 메모리 H2와 모의 토스 응답을 사용하며 실제 승인 요청을 보내지 않습니다.

이 단계는 학습용 서버 연동입니다. 사용자 인증·주문 소유자 검증, 결제창 UI, 웹훅 및 승인 후 서버 장애에 대한 조회·복구 처리는 포함하지 않습니다. 실제 사용 전 토스 테스트 키와 결제창에서 발급한 값으로 별도 검증해야 합니다.

- [토스 결제창 연동 가이드](https://docs.tosspayments.com/guides/v2/payment-window/integration)
- [토스 결제 승인 API](https://docs.tosspayments.com/reference#결제-승인)
