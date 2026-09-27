package com.back.shared.payout.event;

import com.back.shared.payout.dto.PayoutMemberDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PayoutMemberCreatedEvent {
    private final PayoutMemberDto member;
}
