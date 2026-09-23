package com.back;

import com.back.boundedContext.cash.out.WalletRepository;
import com.back.boundedContext.market.out.OrderRepository;
import com.back.global.exception.DomainException;
import com.back.shared.cash.out.CashApiClient;
import com.back.shared.market.out.TossPaymentsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.port=18082",
        "post.api.base-url=http://localhost:18082/api/v1/post",
        "cash.api.base-url=http://localhost:18082/api/v1/cash",
        "spring.datasource.url=jdbc:h2:mem:toss-api-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TossPaymentApiTests {
    @MockitoBean
    private TossPaymentsService toss;
    @Autowired
    private OrderRepository orders;
    @Autowired
    private WalletRepository wallets;
    @Autowired
    private CashApiClient cashApiClient;

    private final RestClient client = RestClient.builder().baseUrl("http://localhost:18082").build();

    private Map<?, ?> confirm(int id, Map<String, Object> body) {
        return client.post().uri("/api/v1/market/orders/{id}/payment/confirm/by/tossPayments", id)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class);
    }

    private Map<String, Object> request(String orderId, long amount) {
        return Map.of("paymentKey", "pk_test", "orderId", orderId, "amount", amount);
    }

    @Test
    void approvesThenPaysAndRejectsReplay() {
        when(toss.confirmCardPayment("pk_test", "order-3-test", 25000)).thenReturn(Map.of("status", "DONE"));
        assertThat(confirm(3, request("order-3-test", 25000)).get("resultCode")).isEqualTo("202-1");
        var order = orders.findById(3).orElseThrow();
        assertThat(order.isPaid()).isTrue();
        assertThat(order.getTossPaymentKey()).isEqualTo("pk_test");
        assertThat(order.getTossOrderId()).isEqualTo("order-3-test");
        assertThat(order.getTossPaymentAmount()).isEqualTo(25000);
        assertThat(wallets.findByHolderId(6).orElseThrow().getBalance()).isZero();
        assertThat(wallets.findByHolderId(2).orElseThrow().getBalance()).isEqualTo(95000);
        assertThatThrownBy(() -> confirm(3, request("order-3-test", 25000)))
                .isInstanceOfSatisfying(RestClientResponseException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        verify(toss, times(1)).confirmCardPayment("pk_test", "order-3-test", 25000);
    }

    @Test
    void approvalFailureLeavesOrderAndWalletUnchanged() {
        when(toss.confirmCardPayment(anyString(), anyString(), anyLong()))
                .thenThrow(new DomainException("400-REJECTED", "승인 거절"));
        assertThatThrownBy(() -> confirm(3, request("order-3-test", 25000)))
                .isInstanceOfSatisfying(RestClientResponseException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(400);
                    assertThat(e.getResponseBodyAsString()).contains("400-REJECTED");
                });
        var order = orders.findById(3).orElseThrow();
        assertThat(order.isPaid()).isFalse();
        assertThat(order.getRequestPaymentDate()).isNull();
        assertThat(order.getTossPaymentKey()).isNull();
        assertThat(wallets.findByHolderId(6).orElseThrow().getBalance()).isZero();
        assertThat(wallets.findByHolderId(2).orElseThrow().getBalance()).isEqualTo(70000);
    }

    @Test
    void rejectsInvalidAmountsAndOrderIdsBeforeCallingToss() {
        for (var body : java.util.List.of(
                request("order-3-test", -1), request("order-3-test", 0),
                request("order-3-test", 24999), request("order-3-test", 25001),
                request("order-2-test", 25000), request("malformed", 25000),
                request("order-99999999999999999-test", 25000),
                Map.<String, Object>of("paymentKey", "pk_test", "orderId", "order-3-test"))) {
            assertThatThrownBy(() -> confirm(3, body))
                    .isInstanceOfSatisfying(RestClientResponseException.class,
                            e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        }
        verifyNoInteractions(toss);
    }

    @Test
    void returnsNotFoundAndReadsWalletViaHttp() {
        assertThatThrownBy(() -> confirm(999, request("order-999-test", 25000)))
                .isInstanceOfSatisfying(RestClientResponseException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
        assertThat(cashApiClient.getBalanceByHolderId(4)).isEqualTo(230000);
        assertThat(cashApiClient.getItemByHolderId(4).getHolderName()).isEqualTo("user1");
        assertThatThrownBy(() -> cashApiClient.getBalanceByHolderId(999))
                .isInstanceOfSatisfying(RestClientResponseException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
        verifyNoInteractions(toss);
    }
}
