package com.sanedge.slider.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;

import com.sanedge.slider.domain.requests.FindAllSliderRequest;
import com.sanedge.slider.entity.Slider;

import com.sanedge.common.test.PostgreSqlResource;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(PostgreSqlResource.class)
@TestReactiveTransaction
class SliderRepositoryTest {

    @Inject
    SliderQueryRepository sliderQueryRepo;

    @Inject
    SliderCommandRepository sliderCommandRepo;

    private Uni<Slider> createAndPersistSlider(String name) {
        Slider slider = new Slider();
        slider.setName(name);
        slider.setImage(name.toLowerCase().replaceAll("[^a-z0-9]+", "-") + ".png");
        return sliderQueryRepo.persist(slider).replaceWith(slider);
    }

    // A Hibernate Reactive session is not thread-safe, so entities must be persisted
    // sequentially instead of via Uni.combine().all() which subscribes in parallel.
    private Uni<List<Slider>> createAndPersistSliders(String... names) {
        Uni<List<Slider>> chain = Uni.createFrom().item(new ArrayList<Slider>());
        for (String name : names) {
            chain = chain.chain(sliders -> createAndPersistSlider(name)
                    .invoke(sliders::add)
                    .replaceWith(sliders));
        }
        return chain;
    }

    // Bulk update/delete bypasses the persistence context, so the context must be
    // cleared before re-reading rows, otherwise stale managed entities are returned.
    private Uni<Void> clearSession() {
        return sliderQueryRepo.getSession()
                .invoke(Mutiny.Session::clear)
                .replaceWithVoid();
    }

    private Uni<List<Slider>> reload(List<Slider> sliders) {
        Uni<List<Slider>> chain = Uni.createFrom().item(new ArrayList<Slider>());
        for (Slider slider : sliders) {
            chain = chain.chain(found -> sliderQueryRepo.findById(slider.id)
                    .invoke(found::add)
                    .replaceWith(found));
        }
        return chain;
    }

    private FindAllSliderRequest pageRequest(int page, int pageSize, String search) {
        FindAllSliderRequest req = new FindAllSliderRequest();
        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(search);
        return req;
    }

    @Test
    void testCreateAndFindById(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistSlider("Main Banner")
                .invoke(saved -> {
                    assertThat(saved).isNotNull();
                    assertThat(saved.id).isNotNull();
                    assertThat(saved.getName()).isEqualTo("Main Banner");
                })
                .chain(saved -> sliderQueryRepo.findById(saved.id))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getName()).isEqualTo("Main Banner");
                })
                .replaceWithVoid());
    }

    @Test
    void testTrashSlider(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistSlider("Trash Me")
                .invoke(saved -> assertThat(saved.getDeletedAt()).isNull())
                .chain(saved -> sliderCommandRepo.trashed(saved.id))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testTrashSliderReturnsNullIfAlreadyTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistSlider("Trash Me Twice")
                .chain(saved -> sliderCommandRepo.trashed(saved.id)
                        .chain(ignored -> sliderCommandRepo.trashed(saved.id)))
                .invoke(trashedAgain -> assertThat(trashedAgain).isNull())
                .replaceWithVoid());
    }

    @Test
    void testTrashSliderReturnsNullIfNotFound(UniAsserter asserter) {
        asserter.execute(() -> sliderCommandRepo.trashed(99999L)
                .invoke(trashed -> assertThat(trashed).isNull())
                .replaceWithVoid());
    }

    @Test
    void testRestoreSlider(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistSlider("Restore Me")
                .chain(saved -> sliderCommandRepo.trashed(saved.id)
                        .chain(ignored -> sliderCommandRepo.restore(saved.id)))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testRestoreSliderReturnsNullIfNotTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistSlider("Restore Me Not")
                .chain(saved -> sliderCommandRepo.restore(saved.id))
                .invoke(restored -> assertThat(restored).isNull())
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanent(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistSlider("Delete Permanent")
                .chain(saved -> sliderCommandRepo.trashed(saved.id)
                        .chain(ignored -> sliderCommandRepo.deletePermanent(saved.id))
                        .chain(deleted -> {
                            assertThat(deleted).isNotNull();
                            return sliderQueryRepo.findById(saved.id);
                        }))
                .invoke(checkDb -> assertThat(checkDb).isNull())
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanentFailsIfNotTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistSlider("Delete Permanent Fail")
                .chain(saved -> sliderCommandRepo.deletePermanent(saved.id)
                        .chain(deleted -> {
                            assertThat(deleted).isNull();
                            return sliderQueryRepo.findById(saved.id);
                        }))
                .invoke(checkDb -> assertThat(checkDb).isNotNull())
                .replaceWithVoid());
    }

    @Test
    void testRestoreAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistSliders("Bulk Restore 1", "Bulk Restore 2", "Bulk Restore 3")
                .chain(sliders -> sliderCommandRepo.trashed(sliders.get(0).id)
                        .replaceWith(sliders))
                .chain(sliders -> sliderCommandRepo.trashed(sliders.get(1).id)
                        .replaceWith(sliders))
                .chain(sliders -> sliderCommandRepo.restoreAllDeleted()
                        .invoke(result -> assertThat(result).isTrue())
                        .replaceWith(sliders))
                .chain(sliders -> clearSession().replaceWith(sliders))
                .chain(this::reload)
                .invoke(found -> {
                    assertThat(found.get(0).getDeletedAt()).isNull();
                    assertThat(found.get(1).getDeletedAt()).isNull();
                    assertThat(found.get(2).getDeletedAt()).isNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testDeleteAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistSliders("Bulk Delete 1", "Bulk Delete 2", "Bulk Delete 3")
                .chain(sliders -> sliderCommandRepo.trashed(sliders.get(0).id)
                        .replaceWith(sliders))
                .chain(sliders -> sliderCommandRepo.trashed(sliders.get(1).id)
                        .replaceWith(sliders))
                .chain(sliders -> sliderCommandRepo.deleteAllDeleted()
                        .invoke(result -> assertThat(result).isTrue())
                        .replaceWith(sliders))
                .chain(sliders -> clearSession().replaceWith(sliders))
                .chain(this::reload)
                .invoke(found -> {
                    assertThat(found.get(0)).isNull();
                    assertThat(found.get(1)).isNull();
                    assertThat(found.get(2)).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindSlidersExcludesTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistSliders("Active Slider", "Trashed Slider")
                .chain(sliders -> sliderCommandRepo.trashed(sliders.get(1).id)
                        .replaceWith(sliders))
                .chain(sliders -> sliderQueryRepo.findSliders(pageRequest(1, 10, null)))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Active Slider");
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindTrashedSliders(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistSliders("Trashed Only", "Not Trashed")
                .chain(sliders -> sliderCommandRepo.trashed(sliders.get(0).id)
                        .replaceWith(sliders))
                .chain(sliders -> sliderQueryRepo.findTrashedSliders(pageRequest(1, 10, null)))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Trashed Only");
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindSlidersWithSearchKeyword(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistSliders("Summer Promo", "Summer Clearance", "Winter Sale")
                .chain(sliders -> sliderQueryRepo.findSliders(pageRequest(1, 10, "Summer")))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindTrashedSlidersWithSearchKeyword(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistSliders("Trashed Promo", "Trashed Discount", "Active Promo")
                .chain(sliders -> sliderCommandRepo.trashed(sliders.get(0).id)
                        .replaceWith(sliders))
                .chain(sliders -> sliderCommandRepo.trashed(sliders.get(1).id)
                        .replaceWith(sliders))
                .chain(sliders -> sliderQueryRepo.findTrashedSliders(pageRequest(1, 10, "Promo")))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Trashed Promo");
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindSlidersWithPagination(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistSliders(
                "Page Slider 1", "Page Slider 2", "Page Slider 3", "Page Slider 4", "Page Slider 5")
                .chain(sliders -> sliderQueryRepo.findSliders(pageRequest(1, 2, null)))
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                })
                .chain(page1 -> sliderQueryRepo.findSliders(pageRequest(2, 2, null))
                        .invoke(page2 -> {
                            assertThat(page2.getData()).hasSize(2);
                            assertThat(page2.getData().get(0).getName()).isNotIn(
                                    page1.getData().get(0).getName(),
                                    page1.getData().get(1).getName());
                        }))
                .replaceWithVoid());
    }

    @Test
    void testFindSlidersWithEmptySearchReturnsAll(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistSliders("Slider A", "Slider B")
                .chain(sliders -> sliderQueryRepo.findSliders(pageRequest(1, 10, "")))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindSlidersWithNullSearchReturnsAll(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistSliders("Slider C", "Slider D")
                .chain(sliders -> sliderQueryRepo.findSliders(pageRequest(1, 10, null)))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindSlidersSearchReturnsEmptyWhenNoMatch(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistSlider("Summer Sale")
                .chain(ignored -> sliderQueryRepo.findSliders(pageRequest(1, 10, "Winter")))
                .invoke(result -> {
                    assertThat(result.getData()).isEmpty();
                    assertThat(result.getTotalRecords()).isZero();
                })
                .replaceWithVoid());
    }
}
