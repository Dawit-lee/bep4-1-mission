package com.back.boundedContext.post.domain;

import com.back.shared.member.domain.ReplicaMember;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "POST_MEMBER")
@NoArgsConstructor
public class PostMember extends ReplicaMember {
	public PostMember(String username, String password, String nickname) {
		super(username, password, nickname);
	}
}