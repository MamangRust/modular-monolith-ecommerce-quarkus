package com.sanedge.cart.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.hibernate.reactive.mutiny.Mutiny;

import com.sanedge.cart.domain.requests.FindAllCartsRequest;
import com.sanedge.cart.entity.Cart;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.vertx.UniAsserter;
import com.sanedge.common.test.PostgreSqlResource;
import com.sanedge.common.test.ReactiveSequential;
import io.quarkus.test.common.QuarkusTestResource;
import io.smallrye.mutiny.Uni;

@QuarkusTest
@QuarkusTestResource(PostgreSqlResource.class)
@TestReactiveTransaction
class CartRepositoryTest {

    @Inject
    CartQueryRepository cartQueryRepo;

    @Inject
    CartCommandRepository cartCommandRepo;

    private Uni<Cart> createAndPersistCart(Integer userId, String name, Integer price) {
        Cart cart = new Cart();
        cart.setUserId(userId);
        cart.setProductId(1);
        cart.setName(name);
        cart.setPrice(price);
        cart.setImage(name.toLowerCase().replaceAll("[^a-z0-9]+", "-") + ".png");
        cart.setQuantity(1);
        cart.setWeight(100);
        return cartQueryRepo.persist(cart).replaceWith(cart);
    }

    // Persist sequentially: a Hibernate Reactive session is not thread-safe, so
    // Uni.join().all(...) (which subscribes in parallel) trips its action queue.
    private Uni<List<Cart>> createAndPersistCarts(String... names) {
        Uni<List<Cart>> chain = Uni.createFrom().item(new ArrayList<Cart>());
        for (String name : names) {
            chain = chain.chain(carts -> createAndPersistCart(1, name, 1000)
                    .invoke(carts::add)
                    .replaceWith(carts));
        }
        return chain;
    }

    private FindAllCartsRequest buildRequest(Integer userId, int page, int pageSize, String search) {
        FindAllCartsRequest req = new FindAllCartsRequest();
        req.setUserId(userId);
        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(search);
        return req;
    }

    // deleteCartById is a bulk delete that bypasses the persistence context, so
    // the managed entity must be evicted before re-reading it.
    private Uni<Void> clearSession() {
        return cartQueryRepo.getSession().invoke(Mutiny.Session::clear).replaceWithVoid();
    }

    @Test
    void testFindCartsByUser_Success(UniAsserter asserter) {
        asserter.execute(() -> ReactiveSequential.sequential(
                createAndPersistCart(1, "Laptop", 15000000),
                createAndPersistCart(1, "Mouse", 500000))
                .chain(() -> cartQueryRepo.findCartsByUser(buildRequest(1, 1, 10, null)))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindCartsByUser_FiltersByUserId(UniAsserter asserter) {
        asserter.execute(() -> ReactiveSequential.sequential(
                createAndPersistCart(1, "Item A", 1000),
                createAndPersistCart(2, "Item B", 2000))
                .chain(() -> cartQueryRepo.findCartsByUser(buildRequest(1, 1, 10, null)))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Item A");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindCartsByUser_WithSearchKeywordByName(UniAsserter asserter) {
        asserter.execute(() -> ReactiveSequential.sequential(
                createAndPersistCart(1, "Gaming Laptop", 15000000),
                createAndPersistCart(1, "Office Laptop", 10000000),
                createAndPersistCart(1, "Gaming Mouse", 1500000))
                .chain(() -> cartQueryRepo.findCartsByUser(buildRequest(1, 1, 10, "Gaming")))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindCartsByUser_WithSearchKeywordByPrice(UniAsserter asserter) {
        asserter.execute(() -> ReactiveSequential.sequential(
                createAndPersistCart(1, "Item 15K", 15000),
                createAndPersistCart(1, "Item 20K", 20000))
                .chain(() -> cartQueryRepo.findCartsByUser(buildRequest(1, 1, 10, "15000")))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Item 15K");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindCartsByUser_WithPagination(UniAsserter asserter) {
        asserter.execute(() -> ReactiveSequential.sequential(
                createAndPersistCart(1, "Item 1", 1000),
                createAndPersistCart(1, "Item 2", 2000),
                createAndPersistCart(1, "Item 3", 3000))
                .chain(() -> cartQueryRepo.findCartsByUser(buildRequest(1, 1, 2, null)))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(3);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindCartsByUser_ReturnsEmptyWhenNoMatch(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistCart(1, "Laptop", 15000000)
                .chain(ignored -> cartQueryRepo.findCartsByUser(buildRequest(1, 1, 10, "Mouse")))
                .invoke(result -> {
                    assertThat(result.getData()).isEmpty();
                    assertThat(result.getTotalRecords()).isZero();
                })
                .replaceWithVoid());
    }

    @Test
    void testDeleteCartById_Success(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistCart(1, "To Delete", 1000)
                .chain(saved -> cartCommandRepo.deleteCartById(saved.id)
                        .invoke(deleted -> assertThat(deleted).isTrue())
                        .chain(() -> clearSession())
                        .chain(() -> cartQueryRepo.findById(saved.id)))
                .invoke(checkDb -> assertThat(checkDb).isNull())
                .replaceWithVoid());
    }

    @Test
    void testDeleteCartById_ReturnsFalseIfNotFound(UniAsserter asserter) {
        asserter.execute(() -> cartCommandRepo.deleteCartById(99999L)
                .invoke(deleted -> assertThat(deleted).isFalse())
                .replaceWithVoid());
    }

    @Test
    void testDeleteCartsByIds_Success(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistCarts("Bulk 1", "Bulk 2", "Bulk 3")
                .chain(carts -> {
                    List<Long> idsToDelete = List.of(carts.get(0).id, carts.get(1).id);
                    return cartCommandRepo.deleteCartsByIds(idsToDelete)
                            .invoke(deleted -> assertThat(deleted).isTrue())
                            .replaceWith(carts.get(2).id);
                })
                .chain(remainingId -> cartQueryRepo.findById(remainingId))
                .invoke(remainingCart -> {
                    assertThat(remainingCart).isNotNull();
                    assertThat(remainingCart.getName()).isEqualTo("Bulk 3");
                })
                .replaceWithVoid());
    }

    @Test
    void testDeleteCartsByIds_ReturnsFalseIfNoneFound(UniAsserter asserter) {
        asserter.execute(() -> cartCommandRepo.deleteCartsByIds(List.of(99998L, 99999L))
                .invoke(deleted -> assertThat(deleted).isFalse())
                .replaceWithVoid());
    }
}
