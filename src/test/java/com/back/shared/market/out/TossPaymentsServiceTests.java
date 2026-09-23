package com.back.shared.market.out;

import com.back.global.exception.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TossPaymentsServiceTests {
    private MockRestServiceServer server;
    private TossPaymentsService service;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("https://api.tosspayments.com");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new TossPaymentsService(new ObjectMapper(), "test_secret", builder.build());
    }

    @Test
    void sendsBasicAuthAndValidatesApproval() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic dGVzdF9zZWNyZXQ6"))
                .andExpect(content().json("""
                        {"paymentKey":"pk","orderId":"order-3-test","amount":25000}
                        """))
                .andRespond(withSuccess("""
                        {"paymentKey":"pk","orderId":"order-3-test","totalAmount":25000,"status":"DONE"}
                        """, MediaType.APPLICATION_JSON));
        assertThat(service.confirmCardPayment("pk", "order-3-test", 25000)).containsEntry("status", "DONE");
        server.verify();
    }

    @Test
    void preservesTossErrorCode() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"INVALID_REQUEST\",\"message\":\"승인 거절\"}"));
        assertThatThrownBy(() -> service.confirmCardPayment("pk", "order-3-test", 25000))
                .isInstanceOf(DomainException.class).hasMessageContaining("400-INVALID_REQUEST");
        server.verify();
    }

    @Test
    void rejectsMismatchedOrUnfinishedApproval() {
        for (String body : new String[]{
                "{\"paymentKey\":\"pk\",\"orderId\":\"order-3-test\",\"totalAmount\":1,\"status\":\"DONE\"}",
                "{\"paymentKey\":\"pk\",\"orderId\":\"order-3-test\",\"totalAmount\":25000,\"status\":\"WAITING_FOR_DEPOSIT\"}",
                "{\"paymentKey\":\"other\",\"orderId\":\"order-3-test\",\"totalAmount\":25000,\"status\":\"DONE\"}",
                "{\"paymentKey\":\"pk\",\"orderId\":\"order-2-test\",\"totalAmount\":25000,\"status\":\"DONE\"}"}) {
            server.reset();
            server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            assertThatThrownBy(() -> service.confirmCardPayment("pk", "order-3-test", 25000))
                    .isInstanceOf(DomainException.class).hasMessageContaining("502-TOSS_INVALID_RESPONSE");
            server.verify();
        }
    }

    @Test
    void rejectsEmptyResponse() {
        server.expect(anything()).andRespond(withSuccess());
        assertThatThrownBy(() -> service.confirmCardPayment("pk", "order-3-test", 25000))
                .isInstanceOf(DomainException.class).hasMessageContaining("EMPTY_RESPONSE");
    }

    @Test
    void doesNotExposeRawErrorBody() {
        server.expect(anything()).andRespond(withServerError().body("private gateway details"));
        assertThatThrownBy(() -> service.confirmCardPayment("pk", "order-3-test", 25000))
                .isInstanceOf(DomainException.class).hasMessageNotContaining("private gateway details");
    }

    @Test
    void rejectsMissingSecretBeforeMakingRequest() {
        var unconfigured = new TossPaymentsService(new ObjectMapper(), "", RestClient.create());
        assertThatThrownBy(() -> unconfigured.confirmCardPayment("pk", "order-3-test", 25000))
                .isInstanceOf(DomainException.class).hasMessageContaining("503-TOSS_NOT_CONFIGURED");
    }
}
