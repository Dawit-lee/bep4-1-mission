package com.back;

import com.back.shared.post.dto.PostDto;
import com.back.shared.post.out.PostApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:post-api-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class BackApplicationTests {

    @Autowired
    private PostApiClient postApiClient;

    @Test
    void contextLoads() {
    }

    @Test
    void getsPostsInDescendingIdOrder() {
        assertThat(postApiClient.getItems())
                .extracting(PostDto::getId)
                .containsExactly(6, 5, 4, 3, 2, 1);
    }

    @Test
    void getsPostWithAuthorAndContent() {
        PostDto post = postApiClient.getItem(1);

        assertThat(post.getId()).isEqualTo(1);
        assertThat(post.getTitle()).isEqualTo("제목1");
        assertThat(post.getContent()).isEqualTo("내용1");
        assertThat(post.getAuthorId()).isPositive();
        assertThat(post.getAuthorName()).isNotBlank();
        assertThat(post.getCreateDate()).isNotNull();
        assertThat(post.getModifyDate()).isNotNull();
    }

}
