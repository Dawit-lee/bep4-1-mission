package com.back.shared.post.event;

import com.back.shared.post.dto.PostCommentDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PostCommentCreatedEvent {
    private final PostCommentDto postComment;
}
