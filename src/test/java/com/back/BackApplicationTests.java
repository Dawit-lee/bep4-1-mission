package com.back;

import com.back.boundedContext.market.app.MarketFacade;
import com.back.boundedContext.market.domain.CartItem;
import com.back.boundedContext.market.out.CartRepository;
import com.back.boundedContext.market.out.MarketMemberRepository;
import com.back.boundedContext.market.out.OrderRepository;
import com.back.boundedContext.cash.out.WalletRepository;
import com.back.global.exception.DomainException;
import com.back.shared.cash.event.CashOrderPaymentFailedEvent;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RecordApplicationEvents
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

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private ApplicationEvents applicationEvents;

    @Test
    void initializesPaidOrderOnlyOnce() {
        var order = marketFacade.findOrderById(1).orElseThrow();
        assertThat(order.isPaid()).isTrue();
        assertThat(order.getRequestPaymentDate()).isNotNull();
        assertThat(walletRepository.findByHolderId(4).orElseThrow().getBalance()).isEqualTo(230_000);
        long holdingBalance = walletRepository.findByHolderId(2).orElseThrow().getBalance();
        assertThat(holdingBalance).isEqualTo(70_000);
        marketDataInit.makeBasePaidOrders();
        assertThat(walletRepository.findByHolderId(2).orElseThrow().getBalance()).isEqualTo(holdingBalance);
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void paysFromWalletAndRejectsRepeatedPayment() {
        var order = marketFacade.findOrderById(2).orElseThrow();
        marketFacade.requestPayment(order, 0);

        assertThat(marketFacade.findOrderById(2).orElseThrow().isPaid()).isTrue();
        assertThat(walletRepository.findByHolderId(5).orElseThrow().getBalance()).isEqualTo(105_000);
        assertThat(walletRepository.findByHolderId(2).orElseThrow().getBalance()).isEqualTo(115_000);
        assertThatThrownBy(() -> marketFacade.requestPayment(order, 0))
                .isInstanceOf(DomainException.class).hasMessageContaining("이미 결제된 주문");
        assertThat(walletRepository.findByHolderId(5).orElseThrow().getBalance()).isEqualTo(105_000);
        assertThat(walletRepository.findByHolderId(2).orElseThrow().getBalance()).isEqualTo(115_000);
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void reportsShortfallAndAllowsPaymentRetryAfterPgTopUp() {
        var order = marketFacade.findOrderById(3).orElseThrow();
        marketFacade.requestPayment(order, 10_000);

        var unpaid = marketFacade.findOrderById(3).orElseThrow();
        assertThat(unpaid.isPaid()).isFalse();
        assertThat(unpaid.getRequestPaymentDate()).isNull();
        assertThat(walletRepository.findByHolderId(6).orElseThrow().getBalance()).isEqualTo(10_000);
        assertThat(walletRepository.findByHolderId(2).orElseThrow().getBalance()).isEqualTo(70_000);
        assertThat(applicationEvents.stream(CashOrderPaymentFailedEvent.class).toList())
                .singleElement().satisfies(event -> {
                    assertThat(event.getShortfallAmount()).isEqualTo(15_000);
                    assertThat(event.getMsg()).contains("3번 주문");
                });

        marketFacade.requestPayment(unpaid, 15_000);

        assertThat(marketFacade.findOrderById(3).orElseThrow().isPaid()).isTrue();
        assertThat(walletRepository.findByHolderId(6).orElseThrow().getBalance()).isZero();
        assertThat(walletRepository.findByHolderId(2).orElseThrow().getBalance()).isEqualTo(95_000);
    }

    @Test
    void rejectsNegativePgAmountWithoutChangingPaymentState() {
        var order = marketFacade.findOrderById(3).orElseThrow();
        assertThatThrownBy(() -> marketFacade.requestPayment(order, -1))
                .isInstanceOf(DomainException.class).hasMessageContaining("음수");
        assertThat(marketFacade.findOrderById(3).orElseThrow().getRequestPaymentDate()).isNull();
        assertThat(walletRepository.findByHolderId(6).orElseThrow().getBalance()).isZero();
    }

    @Test
    @Transactional
    void initializesThreeOrdersWithoutDuplicates() {
        marketDataInit.makeBaseOrders();
        assertThat(marketFacade.ordersCount()).isEqualTo(3);
        assertThat(orderRepository.findAll()).allSatisfy(order -> {
            String username = order.getBuyer().getUsername();
            int expectedItems = switch (username) {
                case "user1" -> 4;
                case "user2" -> 3;
                case "user3" -> 2;
                default -> throw new AssertionError("Unexpected buyer: " + username);
            };
            long expectedPrice = switch (username) {
                case "user1" -> 70_000L;
                case "user2" -> 45_000L;
                default -> 25_000L;
            };
            assertThat(order.getItems()).hasSize(expectedItems);
            assertThat(order.getPrice()).isEqualTo(expectedPrice);
            assertThat(order.getSalePrice()).isEqualTo(expectedPrice);
        });
        for (int i = 1; i <= 3; i++) {
            var buyer = marketFacade.findMemberByUsername("user" + i).orElseThrow();
            var cart = marketFacade.findCartByBuyer(buyer).orElseThrow();
            assertThat(cart.getItemsCount()).isEqualTo(i == 1 ? 4 : 0);
            assertThat(cart.getItems()).hasSize(i == 1 ? 4 : 0);
        }
    }

    @Test
    @Transactional
    void persistsOrderAndRemovesCartItems() {
        var buyer = marketFacade.findMemberByUsername("user1").orElseThrow();
        var cart = marketFacade.findCartByBuyer(buyer).orElseThrow();
        int cartId = cart.getId();
        var result = marketFacade.createOrder(cart);
        int orderId = result.getData().getId();
        assertThat(result.getResultCode()).isEqualTo("201-1");
        assertThat(result.getMsg()).isEqualTo(orderId + "번 주문이 생성되었습니다.");
        entityManager.flush();
        entityManager.clear();

        var savedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(savedOrder.getBuyer().getId()).isEqualTo(cartId);
        assertThat(savedOrder.getPrice()).isEqualTo(70_000L);
        assertThat(savedOrder.getSalePrice()).isEqualTo(70_000L);
        assertThat(savedOrder.getItems()).hasSize(4).allSatisfy(item -> {
            assertThat(item.getId()).isPositive();
            assertThat(item.getOrder().getId()).isEqualTo(orderId);
            assertThat(item.getProductName()).isEqualTo(item.getProduct().getName());
            assertThat(item.getPayoutRate()).isEqualTo(90);
        });
        var savedCart = cartRepository.findById(cartId).orElseThrow();
        assertThat(savedCart.getItems()).isEmpty();
        assertThat(savedCart.getItemsCount()).isZero();
        assertThat(savedCart.hasItems()).isFalse();
        assertThat(entityManager.createQuery(
                "select count(i) from CartItem i where i.cart.id = :id", Long.class)
                .setParameter("id", cartId).getSingleResult()).isZero();
    }

    @Test
    @Transactional
    void retainsOrderPricesWhenProductChanges() {
        var buyer = marketFacade.findMemberByUsername("user3").orElseThrow();
        var cart = marketFacade.findCartByBuyer(buyer).orElseThrow();
        var product = marketFacade.createProduct(buyer, "Post", 99, "할인 상품", "설명", 20_000, 15_000);
        cart.addItem(product);
        int productId = product.getId();
        int orderId = marketFacade.createOrder(cart).getData().getId();
        entityManager.flush();
        entityManager.createQuery("update Product p set p.name = :name, p.price = 30000, p.salePrice = 25000 where p.id = :id")
                .setParameter("name", "변경된 상품").setParameter("id", productId).executeUpdate();
        entityManager.clear();

        var order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getPrice()).isEqualTo(20_000);
        assertThat(order.getSalePrice()).isEqualTo(15_000);
        assertThat(order.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getProductName()).isEqualTo("할인 상품");
            assertThat(item.getPrice()).isEqualTo(20_000);
            assertThat(item.getSalePrice()).isEqualTo(15_000);
            assertThat(item.getProduct().getName()).isEqualTo("변경된 상품");
        });
    }

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
        marketDataInit.makeBaseCartItems();
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
