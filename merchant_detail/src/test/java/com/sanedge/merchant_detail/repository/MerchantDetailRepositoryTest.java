package com.sanedge.merchant_detail.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.sanedge.merchant_detail.entity.MerchantDetail;

import io.quarkus.hibernate.reactive.panache.Panache;
import com.sanedge.common.test.PostgreSqlResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;

@QuarkusTest
@QuarkusTestResource(PostgreSqlResource.class)
@TestReactiveTransaction
class MerchantDetailRepositoryTest {

    @Inject
    MerchantDetailQueryRepository merchantDetailQueryRepo;

    @Inject
    MerchantDetailCommandRepository merchantDetailCommandRepo;

    private Uni<MerchantDetail> createAndPersistDetail(Integer merchantId, String displayName) {
        MerchantDetail detail = new MerchantDetail();
        detail.setMerchantId(merchantId);
        detail.setDisplayName(displayName);
        detail.setCoverImageUrl("http://example.com/cover.jpg");
        detail.setLogoUrl("http://example.com/logo.jpg");
        detail.setShortDescription("Short desc for " + displayName);
        detail.setWebsiteUrl("http://" + displayName.toLowerCase().replace(" ", "") + ".com");
        return merchantDetailQueryRepo.persist(detail).replaceWith(detail);
    }

    // A Hibernate Reactive session is not thread-safe, so entities must be
    // persisted sequentially instead of via Uni.combine().all()/Uni.join().all().
    private Uni<List<MerchantDetail>> createAndPersistDetails(String... displayNames) {
        Uni<List<MerchantDetail>> chain = Uni.createFrom().item(new ArrayList<MerchantDetail>());
        for (String displayName : displayNames) {
            chain = chain.chain(list -> createAndPersistDetail(1, displayName)
                    .invoke(list::add)
                    .replaceWith(list));
        }
        return chain;
    }

    private Uni<Void> trashSequentially(List<MerchantDetail> details, int... indices) {
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (int index : indices) {
            MerchantDetail detail = details.get(index);
            chain = chain.chain(() -> merchantDetailCommandRepo.trashed(detail.id).replaceWithVoid());
        }
        return chain;
    }

    @Test
    void testCreateAndFindById(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistDetail(1, "Toko Sejahtera")
                .invoke(saved -> {
                    assertThat(saved).isNotNull();
                    assertThat(saved.id).isNotNull();
                    assertThat(saved.getDisplayName()).isEqualTo("Toko Sejahtera");
                })
                .chain(saved -> merchantDetailQueryRepo.findById(saved.id))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getWebsiteUrl()).contains("tokosejahtera");
                })
                .replaceWithVoid());
    }

    @Test
    void testTrashMerchantDetail(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistDetail(1, "Toko Trash")
                .invoke(saved -> assertThat(saved.getDeletedAt()).isNull())
                .chain(saved -> merchantDetailCommandRepo.trashed(saved.id))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testTrashMerchantDetailReturnsNullIfAlreadyTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistDetail(1, "Toko Trash 2")
                .chain(saved -> merchantDetailCommandRepo.trashed(saved.id)
                        .chain(ignored -> merchantDetailCommandRepo.trashed(saved.id)))
                .invoke(trashedAgain -> assertThat(trashedAgain).isNull())
                .replaceWithVoid());
    }

    @Test
    void testTrashMerchantDetailReturnsNullIfNotFound(UniAsserter asserter) {
        asserter.execute(() -> merchantDetailCommandRepo.trashed(99999L)
                .invoke(trashed -> assertThat(trashed).isNull())
                .replaceWithVoid());
    }

    @Test
    void testRestoreMerchantDetail(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistDetail(1, "Toko Restore")
                .chain(saved -> merchantDetailCommandRepo.trashed(saved.id)
                        .chain(ignored -> merchantDetailCommandRepo.restore(saved.id)))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testRestoreMerchantDetailReturnsNullIfNotTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistDetail(1, "Toko Restore 2")
                .chain(saved -> merchantDetailCommandRepo.restore(saved.id))
                .invoke(restored -> assertThat(restored).isNull())
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanent(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistDetail(1, "Toko Delete")
                .chain(saved -> merchantDetailCommandRepo.trashed(saved.id)
                        .chain(ignored -> merchantDetailCommandRepo.deletePermanent(saved.id))
                        .chain(deleted -> {
                            assertThat(deleted).isNotNull();
                            return merchantDetailQueryRepo.findById(saved.id);
                        }))
                .invoke(checkDb -> assertThat(checkDb).isNull())
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanentFailsIfNotTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistDetail(1, "Toko Delete 2")
                .chain(saved -> merchantDetailCommandRepo.deletePermanent(saved.id)
                        .chain(deleted -> {
                            assertThat(deleted).isNull();
                            return merchantDetailQueryRepo.findById(saved.id);
                        }))
                .invoke(checkDb -> assertThat(checkDb).isNotNull())
                .replaceWithVoid());
    }

    @Test
    void testRestoreAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistDetails("Bulk Restore 1", "Bulk Restore 2", "Bulk Restore 3")
                .chain(details -> trashSequentially(details, 0, 1).replaceWith(details))
                .chain(details -> merchantDetailCommandRepo.restoreAllDeleted()
                        .invoke(result -> assertThat(result).isTrue())
                        .chain(() -> Panache.getSession().invoke(s -> s.clear()))
                        .chain(() -> merchantDetailQueryRepo.findById(details.get(0).id))
                        .invoke(item -> assertThat(item.getDeletedAt()).isNull())
                        .chain(() -> merchantDetailQueryRepo.findById(details.get(1).id))
                        .invoke(item -> assertThat(item.getDeletedAt()).isNull())
                        .chain(() -> merchantDetailQueryRepo.findById(details.get(2).id))
                        .invoke(item -> assertThat(item.getDeletedAt()).isNull()))
                .replaceWithVoid());
    }

    @Test
    void testDeleteAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistDetails("Bulk Del 1", "Bulk Del 2", "Bulk Del 3")
                .chain(details -> trashSequentially(details, 0, 1).replaceWith(details))
                .chain(details -> merchantDetailCommandRepo.deleteAllDeleted()
                        .invoke(result -> assertThat(result).isTrue())
                        .chain(() -> Panache.getSession().invoke(s -> s.clear()))
                        .chain(() -> merchantDetailQueryRepo.findById(details.get(0).id))
                        .invoke(item -> assertThat(item).isNull())
                        .chain(() -> merchantDetailQueryRepo.findById(details.get(1).id))
                        .invoke(item -> assertThat(item).isNull())
                        .chain(() -> merchantDetailQueryRepo.findById(details.get(2).id))
                        .invoke(item -> assertThat(item).isNotNull()))
                .replaceWithVoid());
    }
}