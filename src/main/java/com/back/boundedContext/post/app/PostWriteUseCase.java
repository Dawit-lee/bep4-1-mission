package com.back.boundedContext.post.app;

import com.back.boundedContext.member.domain.Member;
import com.back.boundedContext.post.domain.Post;
import com.back.boundedContext.post.out.PostRepository;
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

	public Post write(Member author, String title, String content) {
		Post post = new Post(author, title, content);

		//TODO : 이벤트 수정
		author.increaseActivityScore(3);

		return postRepository.save(post);
	}
}
