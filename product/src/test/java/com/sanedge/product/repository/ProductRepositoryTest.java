package com.sanedge.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;

import org.junit.jupiter.api.Test;

import com.sanedge.product.domain.requests.FindAllProductByMerchantRequest;
import com.sanedge.product.entity.Product;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.vertx.UniAsserter;
import com.sanedge.common.test.PostgreSqlResource;
import com.sanedge.common.test.ReactiveSequential;
import io.quarkus.test.common.QuarkusTestResource;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(PostgreSqlResource.class)
@TestReactiveTransaction
class ProductRepositoryTest {

    @Inject
    ProductQueryRepository productQueryRepo;
    @Inject
    ProductCommandRepository productCommandRepo;

    private Uni<Product> createAndPersistProduct(String name, Integer merchantId) {
        Product p = new Product();
        p.setMerchantId(merchantId);
        p.setCategoryId(1);
        p.setName(name);
        p.setDescription("Test description for " + name);
        p.setPrice(100000);
        p.setCountInStock(10);
        p.setBrand("TestBrand");
        p.setWeight(500);
        p.setRating(4.5f);
        p.setSlugProduct(name.toLowerCase().replace(" ", "-"));
        p.setImageProduct("http://example.com/" + name + ".jpg");
        p.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        p.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        return productQueryRepo.persist(p).replaceWith(p);
    }

    @Test
    void testCreateAndFindById(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistProduct("Phone", 1)
                .invoke(saved -> {
                    assertThat(saved).isNotNull();
                    assertThat(saved.id).isNotNull();
                    assertThat(saved.getName()).isEqualTo("Phone");
                })
                .chain(saved -> productQueryRepo.findById(saved.id))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getName()).isEqualTo("Phone");
                })
                .replaceWithVoid());
    }

    @Test
    void testTrashProduct(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistProduct("ToTrash", 1)
                .chain(saved -> productCommandRepo.trashed(saved.id))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testTrashReturnsNullIfAlreadyTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistProduct("TrashTwice", 1)
                .chain(saved -> productCommandRepo.trashed(saved.id)
                        .chain(ignored -> productCommandRepo.trashed(saved.id)))
                .invoke(second -> assertThat(second).isNull())
                .replaceWithVoid());
    }

    @Test
    void testRestoreProduct(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistProduct("ToRestore", 1)
                .chain(saved -> productCommandRepo.trashed(saved.id)
                        .chain(ignored -> productCommandRepo.restore(saved.id)))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanent(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistProduct("ToDelete", 1)
                .chain(saved -> productCommandRepo.trashed(saved.id)
                        .chain(ignored -> productCommandRepo.deletePermanent(saved.id))
                        .chain(deleted -> {
                            assertThat(deleted).isNotNull();
                            return productQueryRepo.findById(saved.id);
                        }))
                .invoke(check -> assertThat(check).isNull())
                .replaceWithVoid());
    }

    @Test
    void testFindProductsByMerchant(UniAsserter asserter) {
        asserter.execute(() -> ReactiveSequential.sequential(
                createAndPersistProduct("Phone1", 1),
                createAndPersistProduct("Phone2", 2))
                .chain(() -> {
                    FindAllProductByMerchantRequest req = new FindAllProductByMerchantRequest();
                    req.setMerchantId(1);
                    req.setCategoryId(0);
                    req.setMinPrice(0);
                    req.setMaxPrice(999999999);
                    req.setPage(1);
                    req.setPageSize(10);
                    req.setSearch("");
                    return productQueryRepo.findByMerchant(req);
                })
                .invoke(result -> assertThat(result.getData()).isNotEmpty())
                .replaceWithVoid());
    }

    @Test
    void testRestoreAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> productCommandRepo.restoreAllDeleted()
                .invoke(result -> assertThat(result).isNotNull())
                .replaceWithVoid());
    }

    @Test
    void testDeleteAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> productCommandRepo.deleteAllDeleted()
                .invoke(result -> assertThat(result).isNotNull())
                .replaceWithVoid());
    }
}
