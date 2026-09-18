package com.back.boundedContext.cash.app;

import com.back.boundedContext.cash.domain.CashMember;
import com.back.boundedContext.cash.out.CashMemberRepository;
import com.back.shared.member.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CashFacade {
	private final CashMemberRepository cashMemberRepository;

	@Transactional
	public CashMember syncMember(MemberDto member) {
		CashMember _member = new CashMember(
				member.getId(),
				member.getCreateDate(),
				member.getModifyDate(),
				member.getUsername(),
				"",
				member.getNickname(),
				member.getActivityScore()
		);

		return cashMemberRepository.save(_member);
	}
}
