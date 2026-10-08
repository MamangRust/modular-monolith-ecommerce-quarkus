package com.sanedge.review.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sanedge.review.domain.requests.FindAllReview;
import com.sanedge.review.entity.Review;

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
class ReviewRepositoryTest {

    @Inject
    ReviewQueryRepository reviewQueryRepo;

    @Inject
    ReviewCommandRepository reviewCommandRepo;

    private Uni<Review> createAndPersistReview(String name, String comment, Integer productId) {
        Review review = new Review();
        review.setUserId(1);
        review.setProductId(productId);
        review.setName(name);
        review.setComment(comment);
        review.setRating(5);
        review.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        review.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        return reviewQueryRepo.persist(review).replaceWith(review);
    }

    // A Hibernate Reactive session is not thread-safe, so entities must be
    // persisted sequentially instead of via Uni.combine().all()/Uni.join().all().
    private Uni<List<Review>> createAndPersistReviews(String... names) {
        Uni<List<Review>> chain = Uni.createFrom().item(new ArrayList<Review>());
        for (String name : names) {
            chain = chain.chain(reviews -> createAndPersistReview(name, "Comment " + name, 1)
                    .invoke(reviews::add)
                    .replaceWith(reviews));
        }
        return chain;
    }

    private Uni<Void> trashSequentially(List<Review> reviews, int... indices) {
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (int index : indices) {
            Review review = reviews.get(index);
            chain = chain.chain(() -> reviewCommandRepo.trash(review.id).replaceWithVoid());
        }
        return chain;
    }

    private FindAllReview buildFindAllRequest(int page, int pageSize, String search) {
        FindAllReview req = new FindAllReview();
        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(search);
        return req;
    }

    @Test
    void testCreateAndFindById(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistReview("John Doe", "Great product!", 1)
                .invoke(saved -> {
                    assertThat(saved).isNotNull();
                    assertThat(saved.id).isNotNull();
                    assertThat(saved.getName()).isEqualTo("John Doe");
                })
                .chain(saved -> reviewQueryRepo.findReviewById(saved.id))
                .invoke(opt -> {
                    assertThat(opt).isPresent();
                    assertThat(opt.get().getComment()).isEqualTo("Great product!");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindReviewByIdReturnsEmptyWhenNotFound(UniAsserter asserter) {
        asserter.execute(() -> reviewQueryRepo.findReviewById(99999L)
                .invoke(opt -> assertThat(opt).isEmpty())
                .replaceWithVoid());
    }

    @Test
    void testTrashReview(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistReview("Jane Doe", "Nice item", 1)
                .invoke(saved -> assertThat(saved.getDeletedAt()).isNull())
                .chain(saved -> reviewCommandRepo.trash(saved.id))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testTrashReviewReturnsNullIfAlreadyTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistReview("Alice", "Okay", 1)
                .chain(saved -> reviewCommandRepo.trash(saved.id)
                        .chain(ignored -> reviewCommandRepo.trash(saved.id)))
                .invoke(trashedAgain -> assertThat(trashedAgain).isNull())
                .replaceWithVoid());
    }

    @Test
    void testRestoreReview(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistReview("Bob", "Good", 1)
                .chain(saved -> reviewCommandRepo.trash(saved.id)
                        .chain(ignored -> reviewCommandRepo.restore(saved.id)))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanent(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistReview("Charlie", "Bad", 1)
                .chain(saved -> reviewCommandRepo.trash(saved.id)
                        .chain(ignored -> reviewCommandRepo.deletePermanent(saved.id))
                        .chain(deleted -> {
                            assertThat(deleted).isNotNull();
                            return reviewQueryRepo.findReviewById(saved.id);
                        }))
                .invoke(checkDb -> assertThat(checkDb).isEmpty())
                .replaceWithVoid());
    }

    @Test
    void testRestoreAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistReviews("R1", "R2")
                .chain(reviews -> trashSequentially(reviews, 0, 1).replaceWith(reviews))
                .chain(reviews -> reviewCommandRepo.restoreAllDeleted()
                        .invoke(result -> assertThat(result).isTrue()))
                .replaceWithVoid());
    }

    @Test
    void testDeleteAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistReviews("D1", "D2")
                .chain(reviews -> trashSequentially(reviews, 0, 1).replaceWith(reviews))
                .chain(reviews -> reviewCommandRepo.deleteAllDeleted()
                        .invoke(result -> assertThat(result).isTrue()))
                .replaceWithVoid());
    }

    @Test
    void testFindReviewsExcludesTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistReviews("Active Review", "Trashed Review")
                .chain(reviews -> trashSequentially(reviews, 1).replaceWith(reviews))
                .chain(reviews -> reviewQueryRepo.findReviews(buildFindAllRequest(1, 10, null)))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Active Review");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindTrashedReviews(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistReviews("Trashed Only", "Not Trashed")
                .chain(reviews -> trashSequentially(reviews, 0).replaceWith(reviews))
                .chain(reviews -> reviewQueryRepo.findTrashedReviews(buildFindAllRequest(1, 10, null)))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Trashed Only");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindReviewsWithSearchKeyword(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistReviews("Excellent product", "Excellent service", "Average item")
                .chain(reviews -> reviewQueryRepo.findReviews(buildFindAllRequest(1, 10, "Excellent")))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindReviewsWithPagination(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistReviews("Page1", "Page2", "Page3", "Page4", "Page5")
                .chain(reviews -> reviewQueryRepo.findReviews(buildFindAllRequest(1, 2, null)))
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                })
                .chain(page1 -> reviewQueryRepo.findReviews(buildFindAllRequest(2, 2, null))
                        .invoke(page2 -> {
                            assertThat(page2.getData()).hasSize(2);
                            assertThat(page2.getData().get(0).getName()).isNotIn(
                                    page1.getData().get(0).getName(),
                                    page1.getData().get(1).getName());
                        }))
                .replaceWithVoid());
    }

    @Test
    void testFindReviewsWithNullSearchReturnsAll(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistReviews("Item A", "Item B")
                .chain(reviews -> reviewQueryRepo.findReviews(buildFindAllRequest(1, 10, null)))
                .invoke(result -> assertThat(result.getData()).hasSize(2))
                .replaceWithVoid());
    }

    @Test
    void testFindReviewsSearchReturnsEmptyWhenNoMatch(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistReview("Summer Sale", "Good", 1)
                .chain(ignored -> reviewQueryRepo.findReviews(buildFindAllRequest(1, 10, "Winter")))
                .invoke(result -> {
                    assertThat(result.getData()).isEmpty();
                    assertThat(result.getTotalRecords()).isZero();
                })
                .replaceWithVoid());
    }
}
