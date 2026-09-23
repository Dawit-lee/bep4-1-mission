package com.back.boundedContext.market.domain;

import com.back.global.jpa.entity.BaseIdAndTime;
import com.back.global.exception.DomainException;
import com.back.shared.market.dto.OrderDto;
import com.back.shared.market.event.MarketOrderPaymentRequestedEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static jakarta.persistence.CascadeType.PERSIST;
import static jakarta.persistence.CascadeType.REMOVE;
import static jakarta.persistence.FetchType.LAZY;

@Entity
@Table(name = "MARKET_ORDER")
@NoArgsConstructor
@Getter
public class Order extends BaseIdAndTime {
    @ManyToOne(fetch = LAZY)
    private MarketMember buyer;
    private LocalDateTime cancelDate;
    private LocalDateTime requestPaymentDate;
    private LocalDateTime paymentDate;
    private long price;
    private long salePrice;
    private String tossPaymentKey;
    private String tossOrderId;
    private Long tossPaymentAmount;

    @OneToMany(mappedBy = "order", cascade = {PERSIST, REMOVE}, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public Order(Cart cart) {
        this.buyer = cart.getBuyer();

        cart.getItems().forEach(item -> {
            addItem(item.getProduct());
        });
    }

    public void addItem(Product product) {
        OrderItem orderItem = new OrderItem(
                this,
                product,
                product.getName(),
                product.getPrice(),
                product.getSalePrice()
        );

        items.add(orderItem);

        price += product.getPrice();
        salePrice += product.getSalePrice();
    }

    public void completePayment() {
        paymentDate = LocalDateTime.now();
    }

    public boolean isPaid() {
        return paymentDate != null;
    }

    public void requestPayment(long pgPaymentAmount) {
        if (isCanceled()) {
            throw new DomainException("400-4", "이미 취소된 주문입니다.");
        }
        if (pgPaymentAmount < 0) {
            throw new DomainException("400-1", "PG 결제 금액은 음수일 수 없습니다.");
        }
        if (isPaid()) {
            throw new DomainException("400-2", "이미 결제된 주문입니다.");
        }
        if (requestPaymentDate != null) {
            throw new DomainException("400-3", "결제가 진행 중인 주문입니다.");
        }
        requestPaymentDate = LocalDateTime.now();

        publishEvent(
                new MarketOrderPaymentRequestedEvent(
                        new OrderDto(this),
                        pgPaymentAmount
                )
        );
    }

    public void cancelRequestPayment() {
        requestPaymentDate = null;
    }

    public boolean isCanceled() {
        return cancelDate != null;
    }

    public void recordTossPayment(String paymentKey, String orderId, long amount) {
        tossPaymentKey = paymentKey;
        tossOrderId = orderId;
        tossPaymentAmount = amount;
    }

    public boolean isPaymentInProgress() {
        return requestPaymentDate != null && paymentDate == null && cancelDate == null;
    }
}
