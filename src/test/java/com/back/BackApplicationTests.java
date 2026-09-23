package com.back;

import com.back.boundedContext.market.domain.Product;
import com.back.boundedContext.market.in.MarketDataInit;
import com.back.boundedContext.market.out.ProductRepository;
import com.back.shared.post.dto.PostDto;
import com.back.shared.post.out.PostApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.port=18080",
        "post.api.base-url=http://localhost:18080/api/v1/post",
        "spring.datasource.url=jdbc:h2:mem:post-api-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class BackApplicationTests {

    @Autowired
    private PostApiClient postApiClient;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private MarketDataInit marketDataInit;

    @Test
    @Transactional
    void createsSixProductsFromPosts() {
        var products = productRepository.findAll(Sort.by("sourceId"));
        assertThat(products).hasSize(6);

        String[] sellers = {"user1", "user1", "user1", "user2", "user2", "user3"};
        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            int postId = i + 1;
            assertThat(product.getSourceTypeCode()).isEqualTo("Post");
            assertThat(product.getSourceId()).isEqualTo(postId);
            assertThat(product.getName()).isEqualTo("제목" + postId);
            assertThat(product.getDescription()).isEqualTo("내용" + postId);
            assertThat(product.getSeller().getUsername()).isEqualTo(sellers[i]);
            assertThat(product.getPrice()).isEqualTo(10_000L + i * 5_000L);
            assertThat(product.getSalePrice()).isEqualTo(product.getPrice());
        }
    }

    @Test
    void doesNotDuplicateProductsWhenInitializationRunsAgain() {
        var idsBefore = productRepository.findAll(Sort.by("id")).stream()
                .map(Product::getId).toList();

        marketDataInit.makeBaseProducts();

        assertThat(productRepository.findAll(Sort.by("id")))
                .extracting(Product::getId)
                .containsExactlyElementsOf(idsBefore);
        assertThat(productRepository.count()).isEqualTo(6);
    }

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
