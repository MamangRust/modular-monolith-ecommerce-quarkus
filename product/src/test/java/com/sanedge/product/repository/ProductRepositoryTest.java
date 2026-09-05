package com.sanedge.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;

import org.junit.jupiter.api.Test;

import com.sanedge.product.domain.requests.FindAllProductByMerchantRequest;
import com.sanedge.product.entity.Product;

import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
@TestReactiveTransaction
class ProductRepositoryTest {

    @Inject ProductQueryRepository productQueryRepo;
    @Inject ProductCommandRepository productCommandRepo;

    private Product createProduct(String name, Integer merchantId) {
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
        return productQueryRepo.persist(p).await().indefinitely();
    }

    @Test
    void testCreateAndFindById() {
        Product saved = createProduct("Phone", 1);
        assertThat(saved).isNotNull();
        assertThat(saved.id).isNotNull();
        assertThat(saved.getName()).isEqualTo("Phone");

        Product found = productQueryRepo.findById(saved.id).await().indefinitely();
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("Phone");
    }

    @Test
    void testTrashProduct() {
        Product saved = createProduct("ToTrash", 1);
        Product trashed = productCommandRepo.trashed(saved.id).await().indefinitely();
        assertThat(trashed).isNotNull();
        assertThat(trashed.getDeletedAt()).isNotNull();
    }

    @Test
    void testTrashReturnsNullIfAlreadyTrashed() {
        Product saved = createProduct("TrashTwice", 1);
        productCommandRepo.trashed(saved.id).await().indefinitely();
        Product second = productCommandRepo.trashed(saved.id).await().indefinitely();
        assertThat(second).isNull();
    }

    @Test
    void testRestoreProduct() {
        Product saved = createProduct("ToRestore", 1);
        productCommandRepo.trashed(saved.id).await().indefinitely();
        Product restored = productCommandRepo.restore(saved.id).await().indefinitely();
        assertThat(restored).isNotNull();
        assertThat(restored.getDeletedAt()).isNull();
    }

    @Test
    void testDeletePermanent() {
        Product saved = createProduct("ToDelete", 1);
        productCommandRepo.trashed(saved.id).await().indefinitely();
        Product deleted = productCommandRepo.deletePermanent(saved.id).await().indefinitely();
        assertThat(deleted).isNotNull();
        Product check = productQueryRepo.findById(saved.id).await().indefinitely();
        assertThat(check).isNull();
    }

    @Test
    void testFindProductsByMerchant() {
        createProduct("Phone1", 1);
        createProduct("Phone2", 2);
        FindAllProductByMerchantRequest req = new FindAllProductByMerchantRequest();
        req.setMerchantId(1);
        req.setCategoryId(0);
        req.setMinPrice(0);
        req.setMaxPrice(999999999);
        req.setPage(1);
        req.setPageSize(10);
        req.setSearch("");
        var result = productQueryRepo.findByMerchant(req).await().indefinitely();
        assertThat(result.getData()).isNotEmpty();
    }

    @Test
    void testRestoreAllDeleted() {
        Boolean result = productCommandRepo.restoreAllDeleted().await().indefinitely();
        assertThat(result).isNotNull();
    }

    @Test
    void testDeleteAllDeleted() {
        Boolean result = productCommandRepo.deleteAllDeleted().await().indefinitely();
        assertThat(result).isNotNull();
    }
}
