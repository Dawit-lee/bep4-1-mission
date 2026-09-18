package com.back.boundedContext.post.app;

import com.back.boundedContext.member.domain.Member;
import com.back.boundedContext.post.domain.Post;
import com.back.boundedContext.post.out.PostRepository;
import com.back.global.rsData.RsData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PostWriteUseCase {
	private final PostRepository postRepository;

	public long count() {
		return postRepository.count();
	}

	public Optional<Post> findById(int id) {
		return postRepository.findById(id);
	}

	public RsData<Post> write(Member author, String title, String content) {
		Post post = new Post(author, title, content);

		//TODO : 이벤트 수정
		author.increaseActivityScore(3);

		return new RsData<>("201-1", "%d번 글이 생성되었습니다.".formatted(post.getId()), post);
	}
}
