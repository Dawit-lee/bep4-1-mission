package com.back.boundedContext.cash.in;

import com.back.boundedContext.cash.app.CashFacade;
import com.back.shared.cash.dto.WalletDto;
import com.back.global.exception.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cash/wallets")
@RequiredArgsConstructor
public class ApiV1WalletController {
    private final CashFacade cashFacade;

    @GetMapping("/by-holder/{holderId}")
    @Transactional(readOnly = true)
    public WalletDto getItemByHolder(
            @PathVariable int holderId
    ) {
        return cashFacade
                .findWalletByHolderId(holderId)
                .map(WalletDto::new)
                .orElseThrow(() -> new DomainException("404-1", "지갑을 찾을 수 없습니다."));
    }
}
