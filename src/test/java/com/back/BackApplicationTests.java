package com.back;

import com.back.boundedContext.market.app.MarketFacade;
import com.back.boundedContext.market.domain.CartItem;
import com.back.boundedContext.market.out.CartRepository;
import com.back.boundedContext.market.out.MarketMemberRepository;
import com.back.shared.member.dto.MemberDto;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
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

    @Autowired
    private MarketFacade marketFacade;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private MarketMemberRepository marketMemberRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void createsCartOnMemberCreationAndKeepsItOnMemberUpdate() {
        int memberId = 1000;
        LocalDateTime now = LocalDateTime.now();
        try {
            marketFacade.syncMember(new MemberDto(memberId, now, now, "cart-test", "구매자", 0));
            var cart = cartRepository.findById(memberId).orElseThrow();
            assertThat(cart.getBuyer().getId()).isEqualTo(memberId);
            assertThat(cart.getItemsCount()).isZero();
            assertThat(cart.hasItems()).isFalse();

            marketFacade.syncMember(new MemberDto(memberId, now, now, "cart-test", "수정", 1));
            assertThat(cartRepository.findById(memberId).orElseThrow().getCreateDate())
                    .isEqualTo(cart.getCreateDate());
        } finally {
            cartRepository.findById(memberId).ifPresent(cartRepository::delete);
            marketMemberRepository.findById(memberId).ifPresent(marketMemberRepository::delete);
        }
    }

    @Test
    @Transactional
    void initializesCartItemsAndDoesNotDuplicateThem() {
        marketDataInit.makeBaseCartItems();
        entityManager.flush();
        entityManager.clear();

        for (int i = 1; i <= 3; i++) {
            var buyer = marketFacade.findMemberByUsername("user" + i).orElseThrow();
            var cart = marketFacade.findCartByBuyer(buyer).orElseThrow();
            assertThat(cart.getId()).isEqualTo(buyer.getId());
            assertThat(cart.getItemsCount()).isEqualTo(5 - i);
            assertThat(cart.getItems()).hasSize(5 - i);
            assertThat(cart.getItems()).extracting(item -> item.getProduct().getId())
                    .containsExactlyInAnyOrderElementsOf(
                            java.util.stream.IntStream.rangeClosed(1, 5 - i).boxed().toList());
        }
    }

    @Test
    @Transactional
    void persistsAddedCartItemAndCount() {
        var buyer = marketFacade.findMemberByUsername("user3").orElseThrow();
        var cart = marketFacade.findCartByBuyer(buyer).orElseThrow();
        int cartId = cart.getId();
        cart.addItem(marketFacade.findProductById(6).orElseThrow());
        entityManager.flush();
        entityManager.clear();

        var saved = cartRepository.findById(cartId).orElseThrow();
        assertThat(saved.getItemsCount()).isEqualTo(3);
        assertThat(saved.getItems()).extracting(item -> item.getProduct().getId())
                .containsExactlyInAnyOrder(1, 2, 6);
        assertThat(saved.getItems()).extracting(CartItem::getId).allMatch(id -> id > 0);
    }

    @Test
    @Transactional
    void initializesMissingCartForExistingMember() {
        var buyer = marketFacade.findMemberByUsername("user2").orElseThrow();
        int buyerId = buyer.getId();
        cartRepository.delete(marketFacade.findCartByBuyer(buyer).orElseThrow());
        entityManager.flush();
        entityManager.clear();

        marketDataInit.makeBaseCartItems();
        entityManager.flush();
        entityManager.clear();

        var restored = cartRepository.findById(buyerId).orElseThrow();
        assertThat(restored.getItemsCount()).isEqualTo(3);
        assertThat(restored.getItems()).hasSize(3);
    }

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
