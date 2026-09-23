package com.back.boundedContext.market.in;

import com.back.boundedContext.market.app.MarketConfirmTossPaymentUseCase;
import com.back.global.rsData.RsData;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/market/orders")
@RequiredArgsConstructor
public class ApiV1OrderController {
    private final MarketConfirmTossPaymentUseCase marketConfirmTossPaymentUseCase;

    public record ConfirmPaymentByTossPaymentsReqBody(
            @NotBlank @Size(max = 200) String paymentKey,
            @NotBlank @Size(min = 6, max = 64)
            @Pattern(regexp = "[A-Za-z0-9_]+-[1-9][0-9]*-[A-Za-z0-9_-]+") String orderId,
            @NotNull @Positive Long amount
    ) {
    }

    @CrossOrigin(
            origins = {
                    "https://cdpn.io",
                    "https://codepen.io"
            },
            allowedHeaders = "*",
            methods = {RequestMethod.POST}
    )
    @PostMapping("/{id}/payment/confirm/by/tossPayments")
    public RsData<Void> confirmPaymentByTossPayments(
            @PathVariable int id,
            @Valid @RequestBody ConfirmPaymentByTossPaymentsReqBody reqBody
    ) {
        marketConfirmTossPaymentUseCase.confirm(id, reqBody.paymentKey(), reqBody.orderId(), reqBody.amount());

        return new RsData<>("202-1", "결제 프로세스가 시작되었습니다.");
    }
}
