package com.back.shared.market.out;

import com.back.global.exception.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class TossPaymentsService {

    private static final String CONFIRM_PATH = "/v1/payments/confirm";

    private final RestClient tossRestClient;
    private final ObjectMapper objectMapper;

    private final String tossSecretKey;

    @Autowired
    public TossPaymentsService(ObjectMapper objectMapper,
                               @Value("${custom.market.toss.payments.secretKey:}") String secretKey,
                               @Value("${custom.market.toss.payments.base-url:https://api.tosspayments.com}") String baseUrl) {
        this(objectMapper, secretKey, RestClient.builder().baseUrl(baseUrl).build());
    }

    TossPaymentsService(ObjectMapper objectMapper, String secretKey, RestClient restClient) {
        this.objectMapper = objectMapper;
        this.tossSecretKey = secretKey;
        this.tossRestClient = restClient;
    }

    public Map<String, Object> confirmCardPayment(String paymentKey, String orderId, long amount) {
        if (tossSecretKey.isBlank() || tossSecretKey.equals("NEED_TO_INPUT")) {
            throw new DomainException("503-TOSS_NOT_CONFIGURED", "토스 페이먼츠 시크릿 키를 설정해주세요.");
        }
        TossPaymentsConfirmRequest requestBody = new TossPaymentsConfirmRequest(
                paymentKey,
                orderId,
                amount
        );

        try {
            ResponseEntity<Map<String, Object>> responseEntity = createConfirmRequest(requestBody)
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<>() {});

            int httpStatus = responseEntity.getStatusCode().value();
            Map<String, Object> responseBody = responseEntity.getBody();

            if (httpStatus != 200) {
                throw createDomainExceptionFromNon200(httpStatus, responseBody);
            }

            if (responseBody == null) {
                throw new DomainException("400-EMPTY_RESPONSE", "토스 결제 승인 응답 바디가 비었습니다.");
            }

            if (!"DONE".equals(responseBody.get("status"))
                    || !paymentKey.equals(responseBody.get("paymentKey"))
                    || !orderId.equals(responseBody.get("orderId"))
                    || !(responseBody.get("totalAmount") instanceof Number total)
                    || new java.math.BigDecimal(total.toString()).compareTo(java.math.BigDecimal.valueOf(amount)) != 0) {
                throw new DomainException("502-TOSS_INVALID_RESPONSE", "토스 승인 결과가 요청한 결제와 일치하지 않습니다.");
            }
            return responseBody;

        } catch (RestClientResponseException e) {
            throw createDomainExceptionFromHttpError(e);
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            throw new DomainException("502-TOSS_CALL_EXCEPTION", "토스 결제 승인 결과를 확인할 수 없습니다.");
        }
    }

    private RestClient.RequestHeadersSpec<?> createConfirmRequest(TossPaymentsConfirmRequest requestBody) {
        return tossRestClient.post()
                .uri(CONFIRM_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBasicAuth(tossSecretKey, ""))
                .body(requestBody);
    }

    private DomainException createDomainExceptionFromNon200(int httpStatus, Map<String, Object> responseBody) {
        if (responseBody == null) {
            return new DomainException("400-HTTP_" + httpStatus, "토스 결제 승인 실패(응답 바디 없음), HTTP " + httpStatus);
        }

        Map<String, Object> body = responseBody;

        String tossCode = extractStringOrDefault(body, "code", "HTTP_" + httpStatus);
        String tossMessage = extractStringOrDefault(body, "message", "토스 결제 승인 실패, HTTP " + httpStatus);

        return new DomainException("400-" + tossCode, tossMessage);
    }

    private DomainException createDomainExceptionFromHttpError(RestClientResponseException e) {
        int httpStatus = e.getStatusCode().value();
        String rawBody = e.getResponseBodyAsString(StandardCharsets.UTF_8);

        if (rawBody == null || rawBody.isBlank()) {
            return new DomainException("400-HTTP_" + httpStatus, "토스 결제 승인 실패(빈 바디), HTTP " + httpStatus);
        }

        try {
            Map<String, Object> errorBody = objectMapper.readValue(rawBody, new TypeReference<>() {
            });
            String tossCode = extractStringOrDefault(errorBody, "code", "HTTP_" + httpStatus);
            String tossMessage = extractStringOrDefault(errorBody, "message", "토스 결제 승인 실패, HTTP " + httpStatus);
            return new DomainException("400-" + tossCode, tossMessage);
        } catch (Exception parseFail) {
            return new DomainException("400-HTTP_" + httpStatus, "토스 결제 승인 실패, HTTP " + httpStatus);
        }
    }

    private String extractStringOrDefault(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value instanceof String s && !s.isBlank()) return s;
        return defaultValue;
    }

    public record TossPaymentsConfirmRequest(String paymentKey, String orderId, long amount) {
    }
}
