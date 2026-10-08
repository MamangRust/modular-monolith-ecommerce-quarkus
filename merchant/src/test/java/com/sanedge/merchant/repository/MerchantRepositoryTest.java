package com.sanedge.merchant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sanedge.common.enums.Status;
import com.sanedge.merchant.domain.requests.FindAllMerchants;
import com.sanedge.merchant.entity.Merchant;

import com.sanedge.common.test.PostgreSqlResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(PostgreSqlResource.class)
@TestReactiveTransaction
class MerchantRepositoryTest {

    @Inject
    MerchantQueryRepository merchantQueryRepo;

    @Inject
    MerchantCommandRepository merchantCommandRepo;

    private Uni<Long> persist(String name, String description, String address, String email, String phone, Status status) {
        Merchant m = new Merchant();
        m.setUserId(1);
        m.setName(name);
        m.setDescription(description);
        m.setAddress(address);
        m.setContactEmail(email);
        m.setContactPhone(phone);
        m.setStatus(status);
        return merchantQueryRepo.persist(m).map(Merchant::getMerchantId);
    }

    private Uni<Long> persist(String name) {
        return persist(name, "desc " + name, "address " + name, name + "@mail.com", "08123456789", Status.SUCCESS);
    }


    private Uni<Void> clean() {
        return merchantQueryRepo.deleteAll().replaceWithVoid();
    }

    private FindAllMerchants findAllReq(int page, int size, String search) {
        FindAllMerchants r = new FindAllMerchants();
        r.setPage(page);
        r.setPageSize(size);
        r.setSearch(search == null ? "" : search);
        return r;
    }

    @Test
    void testCreateAndFindByMerchantId(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Toko Makmur"))
                .chain(id -> merchantQueryRepo.findMerchantById(id))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getName()).isEqualTo("Toko Makmur");
                    assertThat(found.getStatus()).isEqualTo(Status.SUCCESS);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindMerchantById_ReturnsNullIfNotFound(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> merchantQueryRepo.findMerchantById(999999L))
                .invoke(result -> assertThat(result).isNull())
                .replaceWithVoid());
    }

    @Test
    void testFindMerchantById_ReturnsNullIfTrashed(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Toko Sementara"))
                .chain(id -> merchantCommandRepo.trashed(id)
                        .chain(() -> merchantQueryRepo.findMerchantById(id)))
                .invoke(result -> assertThat(result).isNull())
                .replaceWithVoid());
    }

    @Test
    void testExistsByName_ReturnsTrue(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("UniqueMart"))
                .chain(() -> merchantQueryRepo.existsByName("UniqueMart"))
                .invoke(exists -> assertThat(exists).isTrue())
                .replaceWithVoid());
    }

    @Test
    void testExistsByName_ReturnsFalse(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> merchantQueryRepo.existsByName("GhostStore"))
                .invoke(exists -> assertThat(exists).isFalse())
                .replaceWithVoid());
    }

    @Test
    void testTrashed(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Trash Test"))
                .chain(id -> merchantCommandRepo.trashed(id))
                .invoke(m -> {
                    assertThat(m).isNotNull();
                    assertThat(m.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testTrashed_ReturnsNullIfAlreadyTrashed(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Double Trash"))
                .chain(id -> merchantCommandRepo.trashed(id)
                        .chain(() -> merchantCommandRepo.trashed(id)))
                .invoke(m -> assertThat(m).isNull())
                .replaceWithVoid());
    }

    @Test
    void testTrashed_ReturnsNullIfNotFound(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> merchantCommandRepo.trashed(99999L))
                .invoke(m -> assertThat(m).isNull())
                .replaceWithVoid());
    }

    @Test
    void testRestore(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Restore Me"))
                .chain(id -> merchantCommandRepo.trashed(id)
                        .chain(() -> merchantCommandRepo.restore(id)))
                .invoke(m -> {
                    assertThat(m).isNotNull();
                    assertThat(m.getDeletedAt()).isNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testRestore_ReturnsNullIfNotTrashed(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Active Only"))
                .chain(id -> merchantCommandRepo.restore(id))
                .invoke(m -> assertThat(m).isNull())
                .replaceWithVoid());
    }

    @Test
    void testRestore_ReturnsNullIfNotFound(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> merchantCommandRepo.restore(99999L))
                .invoke(m -> assertThat(m).isNull())
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanent(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Delete Perm"))
                .chain(id -> merchantCommandRepo.deletePermanent(id)
                        .invoke(m -> assertThat(m).isNotNull())
                        .replaceWith(id))
                .chain(id -> merchantQueryRepo.findMerchantById(id))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanent_ReturnsNullIfNotFound(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> merchantCommandRepo.deletePermanent(99999L))
                .invoke(m -> assertThat(m).isNull())
                .replaceWithVoid());
    }

    @Test
    void testRestoreAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("A").chain(id -> merchantCommandRepo.trashed(id).replaceWithVoid()))
                .chain(() -> persist("B").chain(id -> merchantCommandRepo.trashed(id).replaceWithVoid()))
                .chain(() -> merchantCommandRepo.restoreAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> merchantQueryRepo.findActiveMerchants(findAllReq(1, 10, "")))
                .invoke(page -> assertThat(page.getTotalRecords()).isEqualTo(2))
                .replaceWithVoid());
    }

    @Test
    void testDeleteAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Trash1").chain(id -> merchantCommandRepo.trashed(id).replaceWithVoid()))
                .chain(() -> persist("Trash2").chain(id -> merchantCommandRepo.trashed(id).replaceWithVoid()))
                .chain(() -> persist("Active1")) // tetap aktif
                .chain(() -> merchantCommandRepo.deleteAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> merchantQueryRepo.findActiveMerchants(findAllReq(1, 10, "")))
                .invoke(page -> assertThat(page.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid());
    }

    @Test
    void testUpdateStatus_Valid(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Change Status"))
                .chain(id -> merchantCommandRepo.updateStatus(id, "PENDING")
                        .invoke(result -> assertThat(result).isTrue())
                        .replaceWith(id))
                .chain(id -> merchantQueryRepo.findMerchantById(id))
                .invoke(m -> assertThat(m.getStatus()).isEqualTo(Status.PENDING))
                .replaceWithVoid());
    }

    @Test
    void testUpdateStatus_InvalidStatus(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Invalid Status"))
                .chain(id -> merchantCommandRepo.updateStatus(id, "NOT_A_STATUS"))
                .invoke(result -> assertThat(result).isFalse())
                .replaceWithVoid());
    }

    @Test
    void testUpdateStatus_MerchantNotFound(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> merchantCommandRepo.updateStatus(99999L, "ACTIVE"))
                .invoke(result -> assertThat(result).isFalse())
                .replaceWithVoid());
    }

    @Test
    void testFindActiveMerchants(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Active1"))
                .chain(() -> persist("Active2"))
                .chain(() -> merchantQueryRepo.findActiveMerchants(findAllReq(1, 10, "")))
                .invoke(page -> assertThat(page.getTotalRecords()).isEqualTo(2))
                .replaceWithVoid());
    }

    @Test
    void testFindTrashedMerchants(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("ToTrash").chain(id -> merchantCommandRepo.trashed(id).replaceWithVoid()))
                .chain(() -> persist("KeepActive"))
                .chain(() -> merchantQueryRepo.findTrashedMerchants(findAllReq(1, 10, "")))
                .invoke(page -> assertThat(page.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid());
    }

    @Test
    void testFindMerchantsWithSearch(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Apple Store", "Sells gadgets", "123 Main St", "apple@mail.com", "1111", Status.SUCCESS))
                .chain(() -> persist("Banana Shop", "Food market", "456 Oak Ave", "banana@mail.com", "2222", Status.SUCCESS))
                .chain(() -> persist("Tech Corner", "Gadget repair", "789 Pine Rd", "tech@mail.com", "3333", Status.SUCCESS))
                .chain(() -> merchantQueryRepo.findMerchants(findAllReq(1, 10, "gadget")))
                .invoke(page -> {
                    assertThat(page.getTotalRecords()).isEqualTo(2);
                    assertThat(page.getData()).extracting(Merchant::getName)
                            .containsExactlyInAnyOrder("Apple Store", "Tech Corner");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindMerchantsWithPagination(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("M1"))
                .chain(() -> persist("M2"))
                .chain(() -> persist("M3"))
                .chain(() -> persist("M4"))
                .chain(() -> persist("M5"))
                .chain(() -> merchantQueryRepo.findMerchants(findAllReq(1, 2, "")))
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                })
                .chain(() -> merchantQueryRepo.findMerchants(findAllReq(2, 2, "")))
                .invoke(page2 -> assertThat(page2.getData()).hasSize(2))
                .replaceWithVoid());
    }

    @Test
    void testFindByName(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Exact Match"))
                .chain(() -> merchantQueryRepo.findByName("Exact Match"))
                .invoke(m -> {
                    assertThat(m).isNotNull();
                    assertThat(m.getName()).isEqualTo("Exact Match");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindByName_NotFound(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> merchantQueryRepo.findByName("No Such Name"))
                .invoke(m -> assertThat(m).isNull())
                .replaceWithVoid());
    }

    @Test
    void testFindByUserId(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> {
                    Merchant m = new Merchant();
                    m.setUserId(10);
                    m.setName("User Merchant");
                    m.setStatus(Status.SUCCESS);
                    return merchantQueryRepo.persist(m);
                })
                .chain(() -> merchantQueryRepo.findByUserId(10))
                .invoke(list -> {
                    assertThat(list).hasSize(1);
                    assertThat(list.get(0).getName()).isEqualTo("User Merchant");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindByUserId_Null(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> merchantQueryRepo.findByUserId(null))
                .invoke(list -> assertThat(list).isEmpty())
                .replaceWithVoid());
    }
}