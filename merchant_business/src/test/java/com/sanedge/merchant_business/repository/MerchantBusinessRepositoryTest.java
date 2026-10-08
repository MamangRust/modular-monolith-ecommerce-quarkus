package com.sanedge.merchant_business.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sanedge.merchant_business.domain.requests.FindAllMerchantRequest;
import com.sanedge.merchant_business.entity.MerchantBusinessInformation;

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
class MerchantBusinessRepositoryTest {

    @Inject
    MerchantBusinessQueryRepository businessQueryRepo;

    @Inject
    MerchantBusinessCommandRepository businessCommandRepo;

    private Uni<Long> persist(String businessType, String taxId, String websiteUrl) {
        MerchantBusinessInformation info = new MerchantBusinessInformation();
        info.setBusinessType(businessType);
        info.setTaxId(taxId);
        info.setWebsiteUrl(websiteUrl);
        
        info.setMerchantId(1);
        return businessQueryRepo.persist(info).map(i -> i.id);
    }

    private Uni<Long> persist(String businessType) {
        return persist(businessType, "TAX-" + businessType, "http://" + businessType.toLowerCase() + ".com");
    }

    private Uni<Void> clean() {
        return businessQueryRepo.deleteAll().replaceWithVoid();
    }

    private FindAllMerchantRequest findAllReq(int page, int size, String search) {
        FindAllMerchantRequest r = new FindAllMerchantRequest();
        r.setPage(page);
        r.setPageSize(size);
        r.setSearch(search == null ? "" : search);
        return r;
    }

    @Test
    void testCreateAndFindById(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Retail"))
                .chain(id -> businessQueryRepo.findById(id))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getBusinessType()).isEqualTo("Retail");
                    assertThat(found.getTaxId()).isEqualTo("TAX-Retail");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindMerchantBusinessInformationById(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Wholesale"))
                .chain(id -> businessQueryRepo.findMerchantBusinessInformationById(id))
                .invoke(info -> {
                    assertThat(info).isNotNull();
                    assertThat(info.getBusinessType()).isEqualTo("Wholesale");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindMerchantBusinessInformationById_ReturnsNullIfNotFound(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> businessQueryRepo.findMerchantBusinessInformationById(999999L))
                .invoke(opt -> assertThat(opt).isNull())
                .replaceWithVoid());
    }

    @Test
    void testFindMerchantBusinessInformationById_ReturnsNullIfTrashed(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Old"))
                .chain(id -> businessCommandRepo.trashed(id)
                        .chain(() -> businessQueryRepo.findMerchantBusinessInformationById(id)))
                .invoke(opt -> assertThat(opt).isNull())
                .replaceWithVoid());
    }

    @Test
    void testFindByIdReturnsNullWhenNotFound(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> businessQueryRepo.findById(999999L))
                .invoke(n -> assertThat(n).isNull())
                .replaceWithVoid());
    }

    @Test
    void testTrashed(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Trash Me"))
                .chain(id -> businessCommandRepo.trashed(id))
                .invoke(info -> {
                    assertThat(info).isNotNull();
                    assertThat(info.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testTrashed_ReturnsNullIfAlreadyTrashed(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Already Trashed"))
                .chain(id -> businessCommandRepo.trashed(id)
                        .chain(() -> businessCommandRepo.trashed(id)))
                .invoke(a -> assertThat(a).isNull())
                .replaceWithVoid());
    }

    @Test
    void testTrashed_ReturnsNullIfNotFound(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> businessCommandRepo.trashed(99999L))
                .invoke(a -> assertThat(a).isNull())
                .replaceWithVoid());
    }

    @Test
    void testRestore(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Restore Me"))
                .chain(id -> businessCommandRepo.trashed(id)
                        .chain(() -> businessCommandRepo.restore(id)))
                .invoke(info -> {
                    assertThat(info).isNotNull();
                    assertThat(info.getDeletedAt()).isNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testRestore_ReturnsNullIfNotTrashed(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Active"))
                .chain(id -> businessCommandRepo.restore(id))
                .invoke(a -> assertThat(a).isNull())
                .replaceWithVoid());
    }

    @Test
    void testRestore_ReturnsNullIfNotFound(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> businessCommandRepo.restore(99999L))
                .invoke(a -> assertThat(a).isNull())
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanent(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Delete Perm"))
                .chain(id -> businessCommandRepo.trashed(id)
                        .chain(() -> businessCommandRepo.deletePermanent(id))
                        .chain(deleted -> businessQueryRepo.findById(id)))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanent_ReturnsNullIfNotTrashed(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("No Trash"))
                .chain(id -> businessCommandRepo.deletePermanent(id))
                .invoke(d -> assertThat(d).isNull())
                .replaceWithVoid());
    }

    @Test
    void testRestoreAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("A").chain(id -> businessCommandRepo.trashed(id).replaceWithVoid()))
                .chain(() -> persist("B").chain(id -> businessCommandRepo.trashed(id).replaceWithVoid()))
                .chain(() -> businessCommandRepo.restoreAllDeleted())
                .invoke(r -> assertThat(r).isTrue())
                .chain(() -> businessQueryRepo.findActiveMerchantBusinessInformation(findAllReq(1, 10, "")))
                .invoke(page -> assertThat(page.getTotalRecords()).isEqualTo(2))
                .replaceWithVoid());
    }

    @Test
    void testDeleteAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("T1").chain(id -> businessCommandRepo.trashed(id).replaceWithVoid()))
                .chain(() -> persist("T2").chain(id -> businessCommandRepo.trashed(id).replaceWithVoid()))
                .chain(() -> persist("Keep"))
                .chain(() -> businessCommandRepo.deleteAllDeleted())
                .invoke(r -> assertThat(r).isTrue())
                .chain(() -> businessQueryRepo.findActiveMerchantBusinessInformation(findAllReq(1, 10, "")))
                .invoke(page -> assertThat(page.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid());
    }

    @Test
    void testFindActiveMerchantBusinessInformation(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Active1"))
                .chain(() -> persist("Active2"))
                .chain(() -> businessQueryRepo.findActiveMerchantBusinessInformation(findAllReq(1, 10, "")))
                .invoke(page -> assertThat(page.getTotalRecords()).isEqualTo(2))
                .replaceWithVoid());
    }

    @Test
    void testFindTrashedMerchantBusinessInformation(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("T").chain(id -> businessCommandRepo.trashed(id).replaceWithVoid()))
                .chain(() -> persist("Keep"))
                .chain(() -> businessQueryRepo.findTrashedMerchantBusinessInformation(findAllReq(1, 10, "")))
                .invoke(page -> assertThat(page.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid());
    }

    @Test
    void testFindMerchantBusinessInformationWithSearch(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Retail", "TAX-111", "http://retail.com"))
                .chain(() -> persist("Service", "TAX-222", "http://service.com"))
                .chain(() -> persist("Technology", "TAX-333", "http://tech.com"))
                .chain(() -> businessQueryRepo.findMerchantBusinessInformation(findAllReq(1, 10, "service")))
                .invoke(page -> {
                    assertThat(page.getTotalRecords()).isEqualTo(1);
                    assertThat(page.getData().get(0).getBusinessType()).isEqualTo("Service");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindMerchantBusinessInformationWithPagination(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("A"))
                .chain(() -> persist("B"))
                .chain(() -> persist("C"))
                .chain(() -> persist("D"))
                .chain(() -> persist("E"))
                .chain(() -> businessQueryRepo.findMerchantBusinessInformation(findAllReq(1, 2, "")))
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                })
                .chain(() -> businessQueryRepo.findMerchantBusinessInformation(findAllReq(2, 2, "")))
                .invoke(page2 -> assertThat(page2.getData()).hasSize(2))
                .replaceWithVoid());
    }
}