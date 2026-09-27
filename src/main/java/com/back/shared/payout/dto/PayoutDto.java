package com.back.shared.payout.dto;

import com.back.standard.modelType.HasModelTypeCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@AllArgsConstructor
@Getter
public class PayoutDto implements HasModelTypeCode {
    private final int id;
    private final LocalDateTime createDate;
    private final LocalDateTime modifyDate;
    private int payeeId;
    private String payeeName;
    private LocalDateTime payoutDate;
    private long amount;
    private boolean isPayeeSystem;

    @Override
    public String getModelTypeCode() {
        return "Payout";
    }
}
