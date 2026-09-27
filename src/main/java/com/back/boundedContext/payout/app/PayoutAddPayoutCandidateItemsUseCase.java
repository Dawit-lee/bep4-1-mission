package com.back.boundedContext.payout.app;

import com.back.shared.market.dto.OrderDto;
import com.back.shared.market.dto.OrderItemDto;
import com.back.shared.market.out.MarketApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutAddPayoutCandidateItemsUseCase {
    private final MarketApiClient marketApiClient;

    public void addPayoutCandidateItems(OrderDto order) {
        List<OrderItemDto> items = marketApiClient.getOrderItems(order.getId());

        items.forEach(item -> {
            log.debug("orderItem.id : {}", item.getId());
        });
    }
}
