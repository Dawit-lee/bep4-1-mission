package com.back.boundedContext.member.app;

import com.back.boundedContext.member.domain.Member;
import com.back.boundedContext.member.out.MemberRepository;
import com.back.global.exception.DomainException;
import com.back.global.rsData.RsData;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MemberFacade {
    private final MemberRepository memberRepository;

    public MemberFacade(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public long count() {
        return memberRepository.count();
    }

    public RsData<Member> join(String username, String password, String nickname) {
        findByUsername(username).ifPresent(m -> {
            throw new DomainException("409-1", "이미 존재하는 username 입니다.");
        });

        Member member = memberRepository.save(new Member(username, password, nickname));

        return new RsData<>("201-1", "%d번 회원이 생성되었습니다.".formatted(member.getId()), member);
    }

    public Optional<Member> findByUsername(String username) {
        return memberRepository.findByUsername(username);
    }
    public Optional<Member> findById(int id) {
        return memberRepository.findById(id);
    }
}