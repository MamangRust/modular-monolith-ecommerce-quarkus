package com.sanedge.banner.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import java.sql.Time;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.sanedge.banner.entity.Banner;
import com.sanedge.banner.domain.requests.FindAllBannerRequest;
import com.sanedge.common.domain.response.PagedResult;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestReactiveTransaction;

@QuarkusTest
@TestReactiveTransaction
class BannerRepositoryTest {

    @Inject
    BannerQueryRepository bannerQueryRepo;

    @Inject
    BannerCommandRepository bannerCommandRepo;

    // --- Helper Method untuk membuat banner secara instan ---
    private Banner createAndPersistBanner(String name, boolean isActive) {
        Banner banner = new Banner();
        banner.setName(name);
        banner.setStartDate(Date.valueOf("2025-01-01"));
        banner.setEndDate(Date.valueOf("2025-12-31"));
        banner.setStartTime(Time.valueOf("00:00:00"));
        banner.setEndTime(Time.valueOf("23:59:59"));
        banner.setIsActive(isActive);
        return bannerQueryRepo.persist(banner).await().indefinitely();
    }

    private Banner createAndPersistBannerWithSchedule(String name, boolean isActive,
            String startDate, String endDate, String startTime, String endTime) {
        Banner banner = new Banner();
        banner.setName(name);
        banner.setStartDate(Date.valueOf(startDate));
        banner.setEndDate(Date.valueOf(endDate));
        banner.setStartTime(Time.valueOf(startTime));
        banner.setEndTime(Time.valueOf(endTime));
        banner.setIsActive(isActive);
        return bannerQueryRepo.persist(banner).await().indefinitely();
    }

    @Test
    void testCreateAndFindById() {
        Banner saved = createAndPersistBannerWithSchedule("Summer Sale", true,
                "2025-06-01", "2025-08-31", "08:00:00", "22:00:00");

        assertThat(saved).isNotNull();
        assertThat(saved.id).isNotNull();
        assertThat(saved.getName()).isEqualTo("Summer Sale");
        assertThat(saved.getIsActive()).isTrue();
        assertThat(saved.getStartDate()).isEqualTo(Date.valueOf("2025-06-01"));
        assertThat(saved.getEndDate()).isEqualTo(Date.valueOf("2025-08-31"));
        assertThat(saved.getStartTime()).isEqualTo(Time.valueOf("08:00:00"));
        assertThat(saved.getEndTime()).isEqualTo(Time.valueOf("22:00:00"));

        Banner found = bannerQueryRepo.findById(saved.id).await().indefinitely();

        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("Summer Sale");
        assertThat(found.getStartDate()).isEqualTo(Date.valueOf("2025-06-01"));
        assertThat(found.getEndDate()).isEqualTo(Date.valueOf("2025-08-31"));
        assertThat(found.getStartTime()).isEqualTo(Time.valueOf("08:00:00"));
        assertThat(found.getEndTime()).isEqualTo(Time.valueOf("22:00:00"));
    }

    @Test
    void testFindByName() {
        createAndPersistBanner("Flash Sale", true);

        Banner found = bannerQueryRepo.findByName("Flash Sale").await().indefinitely();

        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("Flash Sale");
    }

    @Test
    void testFindByNameCaseInsensitive() {
        createAndPersistBanner("Mega Sale", true);

        Banner found = bannerQueryRepo.findByName("MEGA SALE").await().indefinitely();

        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("Mega Sale");
    }

    @Test
    void testFindByNameReturnsNullWhenNotFound() {
        Banner notFound = bannerQueryRepo.findByName("Nonexistent Banner").await().indefinitely();

        assertThat(notFound).isNull();
    }

    @Test
    void testFindByNameExcludesTrashed() {
        Banner saved = createAndPersistBanner("Trashed Banner", true);
        bannerCommandRepo.trash(saved.id).await().indefinitely();

        Banner found = bannerQueryRepo.findByName("Trashed Banner").await().indefinitely();

        assertThat(found).isNull();
    }

    @Test
    void testFindByIdReturnsNullWhenNotFound() {
        Banner notFound = bannerQueryRepo.findById(Long.MAX_VALUE).await().indefinitely();

        assertThat(notFound).isNull();
    }

    // ==========================================
    // 2. SOFT DELETE (TRASH) TESTS
    // ==========================================

    @Test
    void testTrashBanner() {
        Banner saved = createAndPersistBanner("Trash Me", true);
        assertThat(saved.getDeletedAt()).isNull();

        Banner trashed = bannerCommandRepo.trash(saved.id).await().indefinitely();

        assertThat(trashed).isNotNull();
        assertThat(trashed.getDeletedAt()).isNotNull();
    }

    @Test
    void testTrashBannerReturnsNullIfAlreadyTrashed() {
        Banner saved = createAndPersistBanner("Trash Me Twice", true);
        bannerCommandRepo.trash(saved.id).await().indefinitely();

        Banner trashedAgain = bannerCommandRepo.trash(saved.id).await().indefinitely();
        assertThat(trashedAgain).isNull();
    }

    @Test
    void testTrashBannerReturnsNullIfNotFound() {
        Banner trashed = bannerCommandRepo.trash(99999L).await().indefinitely();
        assertThat(trashed).isNull();
    }

    // ==========================================
    // 3. RESTORE TESTS
    // ==========================================

    @Test
    void testRestoreBanner() {
        Banner saved = createAndPersistBanner("Restore Me", true);
        bannerCommandRepo.trash(saved.id).await().indefinitely();

        Banner restored = bannerCommandRepo.restore(saved.id).await().indefinitely();

        assertThat(restored).isNotNull();
        assertThat(restored.getDeletedAt()).isNull();
    }

    @Test
    void testRestoreBannerReturnsNullIfNotTrashed() {
        Banner saved = createAndPersistBanner("Restore Me Not", true);

        Banner restored = bannerCommandRepo.restore(saved.id).await().indefinitely();
        assertThat(restored).isNull();
    }

    @Test
    void testRestoreBannerReturnsNullIfNotFound() {
        Banner restored = bannerCommandRepo.restore(99999L).await().indefinitely();
        assertThat(restored).isNull();
    }

    // ==========================================
    // 4. PERMANENT DELETE TESTS
    // ==========================================

    @Test
    void testDeletePermanent() {
        Banner saved = createAndPersistBanner("Delete Permanent", true);
        bannerCommandRepo.trash(saved.id).await().indefinitely();

        Banner deleted = bannerCommandRepo.deletePermanent(saved.id).await().indefinitely();
        assertThat(deleted).isNotNull();

        Banner checkDb = bannerQueryRepo.findById(saved.id).await().indefinitely();
        assertThat(checkDb).isNull();
    }

    @Test
    void testDeletePermanentFailsIfNotTrashed() {
        Banner saved = createAndPersistBanner("Delete Permanent Fail", true);

        Banner deleted = bannerCommandRepo.deletePermanent(saved.id).await().indefinitely();
        assertThat(deleted).isNull();

        Banner checkDb = bannerQueryRepo.findById(saved.id).await().indefinitely();
        assertThat(checkDb).isNotNull();
    }

    @Test
    void testDeletePermanentReturnsNullIfNotFound() {
        Banner deleted = bannerCommandRepo.deletePermanent(99999L).await().indefinitely();
        assertThat(deleted).isNull();
    }

    // ==========================================
    // 5. BULK ACTIONS (RESTORE ALL & DELETE ALL)
    // ==========================================

    @Test
    void testRestoreAllDeleted() {
        Banner b1 = createAndPersistBanner("Bulk Restore 1", true);
        Banner b2 = createAndPersistBanner("Bulk Restore 2", true);
        Banner b3 = createAndPersistBanner("Bulk Restore 3", true);

        bannerCommandRepo.trash(b1.id).await().indefinitely();
        bannerCommandRepo.trash(b2.id).await().indefinitely();

        Boolean result = bannerCommandRepo.restoreAllDeleted().await().indefinitely();
        assertThat(result).isTrue();

        assertThat(bannerQueryRepo.findById(b1.id).await().indefinitely().getDeletedAt()).isNull();
        assertThat(bannerQueryRepo.findById(b2.id).await().indefinitely().getDeletedAt()).isNull();
        assertThat(bannerQueryRepo.findById(b3.id).await().indefinitely().getDeletedAt()).isNull();
    }

    @Test
    void testRestoreAllDeletedReturnsFalseWhenNoTrashed() {
        createAndPersistBanner("No Trashed Here", true);

        Boolean result = bannerCommandRepo.restoreAllDeleted().await().indefinitely();
        assertThat(result).isFalse();
    }

    @Test
    void testDeleteAllDeleted() {
        Banner b1 = createAndPersistBanner("Bulk Delete 1", true);
        Banner b2 = createAndPersistBanner("Bulk Delete 2", true);
        Banner b3 = createAndPersistBanner("Bulk Delete 3", true);

        bannerCommandRepo.trash(b1.id).await().indefinitely();
        bannerCommandRepo.trash(b2.id).await().indefinitely();

        Boolean result = bannerCommandRepo.deleteAllDeleted().await().indefinitely();
        assertThat(result).isTrue();

        assertThat(bannerQueryRepo.findById(b1.id).await().indefinitely()).isNull();
        assertThat(bannerQueryRepo.findById(b2.id).await().indefinitely()).isNull();
        assertThat(bannerQueryRepo.findById(b3.id).await().indefinitely()).isNotNull();
    }

    @Test
    void testDeleteAllDeletedReturnsFalseWhenNoTrashed() {
        createAndPersistBanner("No Trashed To Delete", true);

        Boolean result = bannerCommandRepo.deleteAllDeleted().await().indefinitely();
        assertThat(result).isFalse();
    }

    // ==========================================
    // 6. PAGED & FILTERED QUERIES TESTS
    // ==========================================

    @Test
    void testFindBannersExcludesTrashed() {
        createAndPersistBanner("Active Banner", true);
        Banner trashed = createAndPersistBanner("Trashed Banner", true);
        bannerCommandRepo.trash(trashed.id).await().indefinitely();

        FindAllBannerRequest req = new FindAllBannerRequest();
        req.setPage(1);
        req.setPageSize(10);

        PagedResult<Banner> result = bannerQueryRepo.findBanners(req).await().indefinitely();

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getName()).isEqualTo("Active Banner");
        assertThat(result.getTotalRecords()).isEqualTo(1);
    }

    @Test
    void testFindActiveBanners() {
        createAndPersistBanner("Active One", true);
        createAndPersistBanner("Inactive One", false);
        Banner trashed = createAndPersistBanner("Trashed Active", true);
        bannerCommandRepo.trash(trashed.id).await().indefinitely();

        FindAllBannerRequest req = new FindAllBannerRequest();
        req.setPage(1);
        req.setPageSize(10);

        PagedResult<Banner> result = bannerQueryRepo.findActiveBanners(req).await().indefinitely();

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getName()).isEqualTo("Active One");
        assertThat(result.getData().get(0).getIsActive()).isTrue();
        assertThat(result.getTotalRecords()).isEqualTo(1);
    }

    @Test
    void testFindTrashedBanners() {
        Banner trashed = createAndPersistBanner("Trashed Only", true);
        createAndPersistBanner("Not Trashed", true);
        bannerCommandRepo.trash(trashed.id).await().indefinitely();

        FindAllBannerRequest req = new FindAllBannerRequest();
        req.setPage(1);
        req.setPageSize(10);

        PagedResult<Banner> result = bannerQueryRepo.findTrashedBanners(req).await().indefinitely();

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getName()).isEqualTo("Trashed Only");
        assertThat(result.getTotalRecords()).isEqualTo(1);
    }

    @Test
    void testFindBannersWithSearchKeyword() {
        createAndPersistBanner("Summer Promotion", true);
        createAndPersistBanner("Summer Clearance", true);
        createAndPersistBanner("Winter Sale", true);

        FindAllBannerRequest req = new FindAllBannerRequest();
        req.setPage(1);
        req.setPageSize(10);
        req.setSearch("Summer");

        PagedResult<Banner> result = bannerQueryRepo.findBanners(req).await().indefinitely();

        assertThat(result.getData()).hasSize(2);
        assertThat(result.getTotalRecords()).isEqualTo(2);
    }

    @Test
    void testFindActiveBannersWithSearchKeyword() {
        createAndPersistBanner("Active Flash", true);
        createAndPersistBanner("Active Deal", true);
        createAndPersistBanner("Inactive Flash", false);

        FindAllBannerRequest req = new FindAllBannerRequest();
        req.setPage(1);
        req.setPageSize(10);
        req.setSearch("Flash");

        PagedResult<Banner> result = bannerQueryRepo.findActiveBanners(req).await().indefinitely();

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getName()).isEqualTo("Active Flash");
        assertThat(result.getTotalRecords()).isEqualTo(1);
    }

    @Test
    void testFindTrashedBannersWithSearchKeyword() {
        Banner t1 = createAndPersistBanner("Trashed Promo", true);
        Banner t2 = createAndPersistBanner("Trashed Discount", true);
        createAndPersistBanner("Active Promo", true);

        bannerCommandRepo.trash(t1.id).await().indefinitely();
        bannerCommandRepo.trash(t2.id).await().indefinitely();

        FindAllBannerRequest req = new FindAllBannerRequest();
        req.setPage(1);
        req.setPageSize(10);
        req.setSearch("Promo");

        PagedResult<Banner> result = bannerQueryRepo.findTrashedBanners(req).await().indefinitely();

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getName()).isEqualTo("Trashed Promo");
        assertThat(result.getTotalRecords()).isEqualTo(1);
    }

    @Test
    void testFindBannersWithPagination() {
        for (int i = 1; i <= 5; i++) {
            createAndPersistBanner("Page Banner " + i, true);
        }

        FindAllBannerRequest reqPage1 = new FindAllBannerRequest();
        reqPage1.setPage(1);
        reqPage1.setPageSize(2);

        PagedResult<Banner> page1 = bannerQueryRepo.findBanners(reqPage1).await().indefinitely();

        assertThat(page1.getData()).hasSize(2);
        assertThat(page1.getTotalRecords()).isEqualTo(5);

        FindAllBannerRequest reqPage2 = new FindAllBannerRequest();
        reqPage2.setPage(2);
        reqPage2.setPageSize(2);

        PagedResult<Banner> page2 = bannerQueryRepo.findBanners(reqPage2).await().indefinitely();

        assertThat(page2.getData()).hasSize(2);
        assertThat(page2.getData().get(0).getName()).isNotIn(page1.getData().get(0).getName(),
                page1.getData().get(1).getName());

        FindAllBannerRequest reqPage3 = new FindAllBannerRequest();
        reqPage3.setPage(3);
        reqPage3.setPageSize(2);

        PagedResult<Banner> page3 = bannerQueryRepo.findBanners(reqPage3).await().indefinitely();

        assertThat(page3.getData()).hasSize(1);
    }

    // ==========================================
    // 7. EDGE CASES - PAGINATION DEFAULTS
    // ==========================================

    @Test
    void testFindBannersWithZeroPageDefaultsToFirstPage() {
        for (int i = 1; i <= 3; i++) {
            createAndPersistBanner("Default Page " + i, true);
        }

        FindAllBannerRequest req = new FindAllBannerRequest();
        req.setPage(0);
        req.setPageSize(10);

        PagedResult<Banner> result = bannerQueryRepo.findBanners(req).await().indefinitely();

        assertThat(result.getData()).hasSize(3);
    }

    @Test
    void testFindBannersWithZeroPageSizeDefaultsToTen() {
        for (int i = 1; i <= 15; i++) {
            createAndPersistBanner("Default Size " + i, true);
        }

        FindAllBannerRequest req = new FindAllBannerRequest();
        req.setPage(1);
        req.setPageSize(0);

        PagedResult<Banner> result = bannerQueryRepo.findBanners(req).await().indefinitely();

        assertThat(result.getData()).hasSize(10);
        assertThat(result.getTotalRecords()).isEqualTo(15);
    }

    @Test
    void testFindBannersWithEmptySearchReturnsAll() {
        createAndPersistBanner("Banner A", true);
        createAndPersistBanner("Banner B", true);

        FindAllBannerRequest req = new FindAllBannerRequest();
        req.setPage(1);
        req.setPageSize(10);
        req.setSearch("");

        PagedResult<Banner> result = bannerQueryRepo.findBanners(req).await().indefinitely();

        assertThat(result.getData()).hasSize(2);
        assertThat(result.getTotalRecords()).isEqualTo(2);
    }

    @Test
    void testFindBannersWithNullSearchReturnsAll() {
        createAndPersistBanner("Banner C", true);
        createAndPersistBanner("Banner D", true);

        FindAllBannerRequest req = new FindAllBannerRequest();
        req.setPage(1);
        req.setPageSize(10);
        req.setSearch(null);

        PagedResult<Banner> result = bannerQueryRepo.findBanners(req).await().indefinitely();

        assertThat(result.getData()).hasSize(2);
        assertThat(result.getTotalRecords()).isEqualTo(2);
    }

    @Test
    void testFindBannersSearchReturnsEmptyWhenNoMatch() {
        createAndPersistBanner("Summer Sale", true);

        FindAllBannerRequest req = new FindAllBannerRequest();
        req.setPage(1);
        req.setPageSize(10);
        req.setSearch("Winter");

        PagedResult<Banner> result = bannerQueryRepo.findBanners(req).await().indefinitely();

        assertThat(result.getData()).isEmpty();
        assertThat(result.getTotalRecords()).isZero();
    }

    // ==========================================
    // 8. INTEGRATION - COMBINED OPERATIONS
    // ==========================================

    @Test
    void testTrashRestoreAndFindAgain() {
        Banner saved = createAndPersistBanner("Lifecycle Banner", true);

        Banner found1 = bannerQueryRepo.findByName("Lifecycle Banner").await().indefinitely();
        assertThat(found1).isNotNull();

        bannerCommandRepo.trash(saved.id).await().indefinitely();

        Banner found2 = bannerQueryRepo.findByName("Lifecycle Banner").await().indefinitely();
        assertThat(found2).isNull();

        FindAllBannerRequest req = new FindAllBannerRequest();
        req.setPage(1);
        req.setPageSize(10);
        PagedResult<Banner> trashedResult = bannerQueryRepo.findTrashedBanners(req).await().indefinitely();
        assertThat(trashedResult.getData()).hasSize(1);

        bannerCommandRepo.restore(saved.id).await().indefinitely();

        Banner found3 = bannerQueryRepo.findByName("Lifecycle Banner").await().indefinitely();
        assertThat(found3).isNotNull();
        assertThat(found3.getDeletedAt()).isNull();
    }

    @Test
    void testTrashAndPermanentDeleteLifecycle() {
        Banner saved = createAndPersistBanner("Full Lifecycle", true);

        Banner trashed = bannerCommandRepo.trash(saved.id).await().indefinitely();
        assertThat(trashed).isNotNull();

        FindAllBannerRequest req = new FindAllBannerRequest();
        req.setPage(1);
        req.setPageSize(10);
        PagedResult<Banner> trashedResult = bannerQueryRepo.findTrashedBanners(req).await().indefinitely();
        assertThat(trashedResult.getData()).hasSize(1);

        Banner deleted = bannerCommandRepo.deletePermanent(saved.id).await().indefinitely();
        assertThat(deleted).isNotNull();

        PagedResult<Banner> afterDelete = bannerQueryRepo.findTrashedBanners(req).await().indefinitely();
        assertThat(afterDelete.getData()).isEmpty();

        Banner checkDb = bannerQueryRepo.findById(saved.id).await().indefinitely();
        assertThat(checkDb).isNull();
    }
}