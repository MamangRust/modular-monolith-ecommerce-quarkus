package com.sanedge.banner.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import java.sql.Time;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.hibernate.reactive.mutiny.Mutiny;

import com.sanedge.banner.domain.requests.FindAllBannerRequest;
import com.sanedge.banner.entity.Banner;

import com.sanedge.common.test.PostgreSqlResource;
import com.sanedge.common.test.ReactiveSequential;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(PostgreSqlResource.class)
@TestReactiveTransaction
class BannerRepositoryTest {

    @Inject
    BannerQueryRepository bannerQueryRepo;

    @Inject
    BannerCommandRepository bannerCommandRepo;

    private Uni<Banner> createAndPersistBanner(String name, boolean isActive) {
        Banner banner = new Banner();
        banner.setName(name);
        banner.setStartDate(Date.valueOf("2025-01-01"));
        banner.setEndDate(Date.valueOf("2025-12-31"));
        banner.setStartTime(Time.valueOf("00:00:00"));
        banner.setEndTime(Time.valueOf("23:59:59"));
        banner.setIsActive(isActive);
        return bannerQueryRepo.persist(banner);
    }

    private Uni<Banner> createAndPersistBannerWithSchedule(String name, boolean isActive,
            String startDate, String endDate, String startTime, String endTime) {
        Banner banner = new Banner();
        banner.setName(name);
        banner.setStartDate(Date.valueOf(startDate));
        banner.setEndDate(Date.valueOf(endDate));
        banner.setStartTime(Time.valueOf(startTime));
        banner.setEndTime(Time.valueOf(endTime));
        banner.setIsActive(isActive);
        return bannerQueryRepo.persist(banner);
    }

    // A Hibernate Reactive session is not thread-safe, so entities must be
    // persisted sequentially instead of via Uni.combine().all()/Uni.join().all().
    private Uni<List<Banner>> createAndPersistBanners(String... names) {
        Uni<List<Banner>> chain = Uni.createFrom().item(new ArrayList<Banner>());
        for (String name : names) {
            chain = chain.chain(banners -> createAndPersistBanner(name, true)
                    .invoke(banners::add)
                    .replaceWith(banners));
        }
        return chain;
    }

    private Uni<List<Banner>> createAndPersistBanners(Object[][] specs) {
        Uni<List<Banner>> chain = Uni.createFrom().item(new ArrayList<Banner>());
        for (Object[] spec : specs) {
            chain = chain.chain(banners -> createAndPersistBanner((String) spec[0], (Boolean) spec[1])
                    .invoke(banners::add)
                    .replaceWith(banners));
        }
        return chain;
    }

    private Uni<Void> trashSequentially(List<Banner> banners, int... indices) {
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (int index : indices) {
            Banner banner = banners.get(index);
            chain = chain.chain(() -> bannerCommandRepo.trash(banner.id).replaceWithVoid());
        }
        return chain;
    }

    private Uni<Void> clearSession() {
        return bannerQueryRepo.getSession().invoke(Mutiny.Session::clear).replaceWithVoid();
    }

    private Uni<List<Banner>> reload(List<Banner> banners) {
        Uni<List<Banner>> chain = Uni.createFrom().item(new ArrayList<Banner>());
        for (Banner banner : banners) {
            chain = chain.chain(list -> bannerQueryRepo.findById(banner.id)
                    .invoke(list::add)
                    .replaceWith(list));
        }
        return chain;
    }

    private FindAllBannerRequest pageRequest(int page, int pageSize, String search) {
        FindAllBannerRequest req = new FindAllBannerRequest();
        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(search);
        return req;
    }

    @Test
    void testCreateAndFindById(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistBannerWithSchedule("Summer Sale", true,
                "2025-06-01", "2025-08-31", "08:00:00", "22:00:00")
                .invoke(saved -> {
                    assertThat(saved).isNotNull();
                    assertThat(saved.id).isNotNull();
                    assertThat(saved.getName()).isEqualTo("Summer Sale");
                    assertThat(saved.getIsActive()).isTrue();
                    assertThat(saved.getStartDate()).isEqualTo(Date.valueOf("2025-06-01"));
                    assertThat(saved.getEndDate()).isEqualTo(Date.valueOf("2025-08-31"));
                    assertThat(saved.getStartTime()).isEqualTo(Time.valueOf("08:00:00"));
                    assertThat(saved.getEndTime()).isEqualTo(Time.valueOf("22:00:00"));
                })
                .chain(saved -> bannerQueryRepo.findById(saved.id))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getName()).isEqualTo("Summer Sale");
                    assertThat(found.getStartDate()).isEqualTo(Date.valueOf("2025-06-01"));
                    assertThat(found.getEndDate()).isEqualTo(Date.valueOf("2025-08-31"));
                    assertThat(found.getStartTime()).isEqualTo(Time.valueOf("08:00:00"));
                    assertThat(found.getEndTime()).isEqualTo(Time.valueOf("22:00:00"));
                })
                .replaceWithVoid());
    }

    @Test
    void testFindByName(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistBanner("Flash Sale", true)
                .chain(ignored -> bannerQueryRepo.findByName("Flash Sale"))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getName()).isEqualTo("Flash Sale");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindByNameCaseInsensitive(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistBanner("Mega Sale", true)
                .chain(ignored -> bannerQueryRepo.findByName("MEGA SALE"))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getName()).isEqualTo("Mega Sale");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindByNameReturnsNullWhenNotFound(UniAsserter asserter) {
        asserter.execute(() -> bannerQueryRepo.findByName("Nonexistent Banner")
                .invoke(notFound -> assertThat(notFound).isNull())
                .replaceWithVoid());
    }

    @Test
    void testFindByNameExcludesTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistBanner("Trashed Banner", true)
                .chain(saved -> bannerCommandRepo.trash(saved.id)
                        .chain(ignored -> bannerQueryRepo.findByName("Trashed Banner")))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid());
    }

    @Test
    void testFindByIdReturnsNullWhenNotFound(UniAsserter asserter) {
        asserter.execute(() -> bannerQueryRepo.findById(Long.MAX_VALUE)
                .invoke(notFound -> assertThat(notFound).isNull())
                .replaceWithVoid());
    }

    @Test
    void testTrashBanner(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistBanner("Trash Me", true)
                .invoke(saved -> assertThat(saved.getDeletedAt()).isNull())
                .chain(saved -> bannerCommandRepo.trash(saved.id))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testTrashBannerReturnsNullIfAlreadyTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistBanner("Trash Me Twice", true)
                .chain(saved -> bannerCommandRepo.trash(saved.id)
                        .chain(ignored -> bannerCommandRepo.trash(saved.id)))
                .invoke(trashedAgain -> assertThat(trashedAgain).isNull())
                .replaceWithVoid());
    }

    @Test
    void testTrashBannerReturnsNullIfNotFound(UniAsserter asserter) {
        asserter.execute(() -> bannerCommandRepo.trash(99999L)
                .invoke(trashed -> assertThat(trashed).isNull())
                .replaceWithVoid());
    }

    @Test
    void testRestoreBanner(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistBanner("Restore Me", true)
                .chain(saved -> bannerCommandRepo.trash(saved.id)
                        .chain(ignored -> bannerCommandRepo.restore(saved.id)))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testRestoreBannerReturnsNullIfNotTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistBanner("Restore Me Not", true)
                .chain(saved -> bannerCommandRepo.restore(saved.id))
                .invoke(restored -> assertThat(restored).isNull())
                .replaceWithVoid());
    }

    @Test
    void testRestoreBannerReturnsNullIfNotFound(UniAsserter asserter) {
        asserter.execute(() -> bannerCommandRepo.restore(99999L)
                .invoke(restored -> assertThat(restored).isNull())
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanent(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistBanner("Delete Permanent", true)
                .chain(saved -> bannerCommandRepo.trash(saved.id)
                        .chain(ignored -> bannerCommandRepo.deletePermanent(saved.id))
                        .chain(deleted -> {
                            assertThat(deleted).isNotNull();
                            return bannerQueryRepo.findById(saved.id);
                        }))
                .invoke(checkDb -> assertThat(checkDb).isNull())
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanentFailsIfNotTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistBanner("Delete Permanent Fail", true)
                .chain(saved -> bannerCommandRepo.deletePermanent(saved.id)
                        .chain(deleted -> {
                            assertThat(deleted).isNull();
                            return bannerQueryRepo.findById(saved.id);
                        }))
                .invoke(checkDb -> assertThat(checkDb).isNotNull())
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanentReturnsNullIfNotFound(UniAsserter asserter) {
        asserter.execute(() -> bannerCommandRepo.deletePermanent(99999L)
                .invoke(deleted -> assertThat(deleted).isNull())
                .replaceWithVoid());
    }

    @Test
    void testRestoreAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistBanners("Bulk Restore 1", "Bulk Restore 2", "Bulk Restore 3")
                .chain(banners -> trashSequentially(banners, 0, 1).replaceWith(banners))
                .chain(banners -> bannerCommandRepo.restoreAllDeleted()
                        .chain(result -> {
                            assertThat(result).isTrue();
                            return clearSession().replaceWith(banners);
                        }))
                .chain(this::reload)
                .invoke(banners -> {
                    assertThat(banners.get(0).getDeletedAt()).isNull();
                    assertThat(banners.get(1).getDeletedAt()).isNull();
                    assertThat(banners.get(2).getDeletedAt()).isNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testRestoreAllDeletedReturnsFalseWhenNoTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistBanner("No Trashed Here", true)
                .chain(ignored -> bannerCommandRepo.restoreAllDeleted())
                .invoke(result -> assertThat(result).isFalse())
                .replaceWithVoid());
    }

    @Test
    void testDeleteAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistBanners("Bulk Delete 1", "Bulk Delete 2", "Bulk Delete 3")
                .chain(banners -> trashSequentially(banners, 0, 1).replaceWith(banners))
                .chain(banners -> bannerCommandRepo.deleteAllDeleted()
                        .chain(result -> {
                            assertThat(result).isTrue();
                            return clearSession().replaceWith(banners);
                        }))
                .chain(this::reload)
                .invoke(banners -> {
                    assertThat(banners.get(0)).isNull();
                    assertThat(banners.get(1)).isNull();
                    assertThat(banners.get(2)).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testDeleteAllDeletedReturnsFalseWhenNoTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistBanner("No Trashed To Delete", true)
                .chain(ignored -> bannerCommandRepo.deleteAllDeleted())
                .invoke(result -> assertThat(result).isFalse())
                .replaceWithVoid());
    }

    @Test
    void testFindBannersExcludesTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistBanners("Active Banner", "Trashed Banner")
                .chain(banners -> trashSequentially(banners, 1).replaceWith(banners))
                .chain(banners -> bannerQueryRepo.findBanners(pageRequest(1, 10, null)))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Active Banner");
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindActiveBanners(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistBanners(new Object[][] {
                { "Active One", true },
                { "Inactive One", false },
                { "Trashed Active", true } })
                .chain(banners -> trashSequentially(banners, 2).replaceWith(banners))
                .chain(banners -> bannerQueryRepo.findActiveBanners(pageRequest(1, 10, null)))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Active One");
                    assertThat(result.getData().get(0).getIsActive()).isTrue();
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindTrashedBanners(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistBanners("Trashed Only", "Not Trashed")
                .chain(banners -> trashSequentially(banners, 0).replaceWith(banners))
                .chain(banners -> bannerQueryRepo.findTrashedBanners(pageRequest(1, 10, null)))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Trashed Only");
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindBannersWithSearchKeyword(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistBanners("Summer Promotion", "Summer Clearance", "Winter Sale")
                .chain(banners -> bannerQueryRepo.findBanners(pageRequest(1, 10, "Summer")))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindActiveBannersWithSearchKeyword(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistBanners(new Object[][] {
                { "Active Flash", true },
                { "Active Deal", true },
                { "Inactive Flash", false } })
                .chain(banners -> bannerQueryRepo.findActiveBanners(pageRequest(1, 10, "Flash")))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Active Flash");
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindTrashedBannersWithSearchKeyword(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistBanners("Trashed Promo", "Trashed Discount", "Active Promo")
                .chain(banners -> trashSequentially(banners, 0, 1).replaceWith(banners))
                .chain(banners -> bannerQueryRepo.findTrashedBanners(pageRequest(1, 10, "Promo")))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Trashed Promo");
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindBannersWithPagination(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistBanners("Page Banner 1", "Page Banner 2", "Page Banner 3",
                "Page Banner 4", "Page Banner 5")
                .chain(banners -> bannerQueryRepo.findBanners(pageRequest(1, 2, null)))
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                })
                .chain(page1 -> bannerQueryRepo.findBanners(pageRequest(2, 2, null))
                        .invoke(page2 -> {
                            assertThat(page2.getData()).hasSize(2);
                            assertThat(page2.getData().get(0).getName()).isNotIn(
                                    page1.getData().get(0).getName(),
                                    page1.getData().get(1).getName());
                        }))
                .chain(page2 -> bannerQueryRepo.findBanners(pageRequest(3, 2, null)))
                .invoke(page3 -> assertThat(page3.getData()).hasSize(1))
                .replaceWithVoid());
    }

    @Test
    void testFindBannersWithZeroPageDefaultsToFirstPage(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistBanners("Default Page 1", "Default Page 2", "Default Page 3")
                .chain(banners -> bannerQueryRepo.findBanners(pageRequest(0, 10, null)))
                .invoke(result -> assertThat(result.getData()).hasSize(3))
                .replaceWithVoid());
    }

    @Test
    void testFindBannersWithZeroPageSizeDefaultsToTen(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistBanners("Default Size 1", "Default Size 2", "Default Size 3",
                "Default Size 4", "Default Size 5", "Default Size 6", "Default Size 7", "Default Size 8",
                "Default Size 9", "Default Size 10", "Default Size 11", "Default Size 12", "Default Size 13",
                "Default Size 14", "Default Size 15")
                .chain(banners -> bannerQueryRepo.findBanners(pageRequest(1, 0, null)))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(10);
                    assertThat(result.getTotalRecords()).isEqualTo(15);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindBannersWithEmptySearchReturnsAll(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistBanners("Banner A", "Banner B")
                .chain(banners -> bannerQueryRepo.findBanners(pageRequest(1, 10, "")))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindBannersWithNullSearchReturnsAll(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistBanners("Banner C", "Banner D")
                .chain(banners -> bannerQueryRepo.findBanners(pageRequest(1, 10, null)))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindBannersSearchReturnsEmptyWhenNoMatch(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistBanner("Summer Sale", true)
                .chain(ignored -> bannerQueryRepo.findBanners(pageRequest(1, 10, "Winter")))
                .invoke(result -> {
                    assertThat(result.getData()).isEmpty();
                    assertThat(result.getTotalRecords()).isZero();
                })
                .replaceWithVoid());
    }

    @Test
    void testTrashRestoreAndFindAgain(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistBanner("Lifecycle Banner", true)
                .chain(saved -> bannerQueryRepo.findByName("Lifecycle Banner")
                        .invoke(found1 -> assertThat(found1).isNotNull())
                        .replaceWith(saved))
                .chain(saved -> bannerCommandRepo.trash(saved.id)
                        .replaceWith(saved))
                .chain(saved -> bannerQueryRepo.findByName("Lifecycle Banner")
                        .invoke(found2 -> assertThat(found2).isNull())
                        .replaceWith(saved))
                .chain(saved -> bannerQueryRepo.findTrashedBanners(pageRequest(1, 10, null))
                        .invoke(trashedResult -> assertThat(trashedResult.getData()).hasSize(1))
                        .replaceWith(saved))
                .chain(saved -> bannerCommandRepo.restore(saved.id)
                        .replaceWith(saved))
                .chain(saved -> bannerQueryRepo.findByName("Lifecycle Banner"))
                .invoke(found3 -> {
                    assertThat(found3).isNotNull();
                    assertThat(found3.getDeletedAt()).isNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testTrashAndPermanentDeleteLifecycle(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistBanner("Full Lifecycle", true)
                .chain(saved -> bannerCommandRepo.trash(saved.id)
                        .invoke(trashed -> assertThat(trashed).isNotNull())
                        .replaceWith(saved))
                .chain(saved -> bannerQueryRepo.findTrashedBanners(pageRequest(1, 10, null))
                        .invoke(trashedResult -> assertThat(trashedResult.getData()).hasSize(1))
                        .replaceWith(saved))
                .chain(saved -> bannerCommandRepo.deletePermanent(saved.id)
                        .invoke(deleted -> assertThat(deleted).isNotNull())
                        .replaceWith(saved))
                .chain(saved -> bannerQueryRepo.findTrashedBanners(pageRequest(1, 10, null))
                        .invoke(afterDelete -> assertThat(afterDelete.getData()).isEmpty())
                        .replaceWith(saved))
                .chain(saved -> bannerQueryRepo.findById(saved.id))
                .invoke(checkDb -> assertThat(checkDb).isNull())
                .replaceWithVoid());
    }
}
