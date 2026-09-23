package com.back.boundedContext.market.app;

import com.back.boundedContext.market.domain.Order;
import com.back.boundedContext.market.out.OrderRepository;
import com.back.global.exception.DomainException;
import com.back.shared.cash.out.CashApiClient;
import com.back.shared.market.out.TossPaymentsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarketConfirmTossPaymentUseCase {
    private final OrderRepository orderRepository;
    private final CashApiClient cashApiClient;
    private final TossPaymentsService tossPaymentsService;

    @Transactional
    public void confirm(int id, String paymentKey, String orderId, long amount) {
        Order order = orderRepository.findByIdForPayment(id)
                .orElseThrow(() -> new DomainException("404-1", "주문을 찾을 수 없습니다."));

        if (order.isCanceled())
            throw new DomainException("400-1", "이미 취소된 주문입니다.");

        if (order.isPaymentInProgress())
            throw new DomainException("400-2", "이미 결제 진행중인 주문입니다.");

        if (order.isPaid())
            throw new DomainException("400-3", "이미 결제된 주문입니다.");

        if (!Integer.toString(id).equals(orderId.split("-", 3)[1]))
            throw new DomainException("400-5", "주문번호가 일치하지 않습니다.");

        long walletBalance = cashApiClient.getBalanceByHolderId(order.getBuyer().getId());

        if (amount > order.getSalePrice()
                || amount < Math.max(0, order.getSalePrice() - walletBalance))
            throw new DomainException("400-4", "주문 금액과 예치금에 맞지 않는 결제 금액입니다.");

        tossPaymentsService.confirmCardPayment(
                paymentKey,
                orderId,
                amount
        );

        order.recordTossPayment(paymentKey, orderId, amount);
        order.requestPayment(amount);

    }
}
