package com.sanedge.review_detail.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.hibernate.reactive.mutiny.Mutiny;

import com.sanedge.review_detail.entity.ReviewDetail;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.vertx.UniAsserter;
import com.sanedge.common.test.PostgreSqlResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(PostgreSqlResource.class)
@TestReactiveTransaction
class ReviewDetailRepositoryTest {

    @Inject
    ReviewDetailRepository reviewDetailRepository;

    private Uni<ReviewDetail> createAndPersistDetail(Long reviewId, String type, String url) {
        ReviewDetail detail = new ReviewDetail();
        detail.setReviewId(reviewId.intValue());
        detail.setType(type);
        detail.setUrl(url);
        detail.setCaption("Test caption for " + type);
        detail.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        detail.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        return reviewDetailRepository.persist(detail).replaceWith(detail);
    }

    // A Hibernate Reactive session is not thread-safe, so entities must be
    // persisted sequentially instead of via Uni.combine().all()/Uni.join().all().
    private Uni<List<ReviewDetail>> createAndPersistDetails(Object[][] specs) {
        Uni<List<ReviewDetail>> chain = Uni.createFrom().item(new ArrayList<ReviewDetail>());
        for (Object[] spec : specs) {
            chain = chain.chain(list -> createAndPersistDetail((Long) spec[0], (String) spec[1], (String) spec[2])
                    .invoke(list::add)
                    .replaceWith(list));
        }
        return chain;
    }

    private Uni<Void> trashSequentially(List<ReviewDetail> details) {
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (ReviewDetail detail : details) {
            chain = chain.chain(() -> reviewDetailRepository.trash(detail.id).replaceWithVoid());
        }
        return chain;
    }

    private Uni<Void> clearSession() {
        return reviewDetailRepository.getSession().invoke(Mutiny.Session::clear).replaceWithVoid();
    }

    private Uni<List<ReviewDetail>> reload(List<ReviewDetail> details) {
        Uni<List<ReviewDetail>> chain = Uni.createFrom().item(new ArrayList<ReviewDetail>());
        for (ReviewDetail detail : details) {
            chain = chain.chain(list -> reviewDetailRepository.findById(detail.id)
                    .invoke(list::add)
                    .replaceWith(list));
        }
        return chain;
    }

    @Test
    void testTrashReviewDetail(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistDetail(1L, "IMAGE", "http://example.com/img.jpg")
                .invoke(saved -> {
                    assertThat(saved).isNotNull();
                    assertThat(saved.id).isNotNull();
                    assertThat(saved.getDeletedAt()).isNull();
                })
                .chain(saved -> reviewDetailRepository.trash(saved.id))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testTrashReviewDetailReturnsNullIfAlreadyTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistDetail(1L, "VIDEO", "http://example.com/vid.mp4")
                .chain(saved -> reviewDetailRepository.trash(saved.id)
                        .chain(ignored -> reviewDetailRepository.trash(saved.id)))
                .invoke(trashedAgain -> assertThat(trashedAgain).isNull())
                .replaceWithVoid());
    }

    @Test
    void testTrashReviewDetailReturnsNullIfNotFound(UniAsserter asserter) {
        asserter.execute(() -> reviewDetailRepository.trash(99999L)
                .invoke(trashed -> assertThat(trashed).isNull())
                .replaceWithVoid());
    }

    @Test
    void testRestoreReviewDetail(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistDetail(1L, "IMAGE", "http://example.com/img2.jpg")
                .chain(saved -> reviewDetailRepository.trash(saved.id)
                        .chain(ignored -> reviewDetailRepository.restore(saved.id)))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testRestoreReviewDetailReturnsNullIfNotTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistDetail(1L, "IMAGE", "http://example.com/img3.jpg")
                .chain(saved -> reviewDetailRepository.restore(saved.id))
                .invoke(restored -> assertThat(restored).isNull())
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanent(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistDetail(1L, "IMAGE", "http://example.com/img4.jpg")
                .chain(saved -> reviewDetailRepository.trash(saved.id)
                        .chain(ignored -> reviewDetailRepository.deletePermanent(saved.id))
                        .chain(deleted -> {
                            assertThat(deleted).isNotNull();
                            return reviewDetailRepository.findById(saved.id);
                        }))
                .invoke(checkDb -> assertThat(checkDb).isNull())
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanentFailsIfNotTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistDetail(1L, "IMAGE", "http://example.com/img5.jpg")
                .chain(saved -> reviewDetailRepository.deletePermanent(saved.id)
                        .chain(deleted -> {
                            assertThat(deleted).isNull();
                            return reviewDetailRepository.findById(saved.id);
                        }))
                .invoke(checkDb -> assertThat(checkDb).isNotNull())
                .replaceWithVoid());
    }

    @Test
    void testRestoreAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistDetails(new Object[][] {
                { 1L, "IMAGE", "url1.jpg" },
                { 1L, "VIDEO", "url2.mp4" },
                { 2L, "IMAGE", "url3.jpg" } })
                .chain(details -> trashSequentially(details.subList(0, 2)).replaceWith(details))
                .chain(details -> reviewDetailRepository.restoreAllDeleted()
                        .chain(result -> {
                            assertThat(result).isTrue();
                            return clearSession().replaceWith(details);
                        }))
                .chain(this::reload)
                .invoke(details -> {
                    assertThat(details.get(0).getDeletedAt()).isNull();
                    assertThat(details.get(1).getDeletedAt()).isNull();
                    assertThat(details.get(2).getDeletedAt()).isNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testDeleteAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistDetails(new Object[][] {
                { 1L, "IMAGE", "url4.jpg" },
                { 1L, "VIDEO", "url5.mp4" },
                { 2L, "IMAGE", "url6.jpg" } })
                .chain(details -> trashSequentially(details.subList(0, 2)).replaceWith(details))
                .chain(details -> reviewDetailRepository.deleteAllDeleted()
                        .chain(result -> {
                            assertThat(result).isTrue();
                            return clearSession().replaceWith(details);
                        }))
                .chain(this::reload)
                .invoke(details -> {
                    assertThat(details.get(0)).isNull();
                    assertThat(details.get(1)).isNull();
                    assertThat(details.get(2)).isNotNull();
                })
                .replaceWithVoid());
    }
}
