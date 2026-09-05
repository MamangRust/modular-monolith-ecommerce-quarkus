package com.sanedge.review.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.sanedge.review.domain.requests.FindAllReview;
import com.sanedge.review.entity.Review;

import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
@TestReactiveTransaction
class ReviewRepositoryTest {
    @Inject ReviewQueryRepository reviewQueryRepo;
    @Inject ReviewCommandRepository reviewCommandRepo;

    private Review createReview(String comment, Integer userId, Integer productId) {
        Review r = new Review();
        r.setComment(comment);
        r.setUserId(userId);
        r.setProductId(productId);
        r.setRating(5);
        r.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
        r.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
        return reviewQueryRepo.persist(r).await().indefinitely();
    }

    @Test
    void testCreateAndFindById() {
        Review saved = createReview("Great product!", 1, 1);
        assertThat(saved).isNotNull();
        Review found = reviewQueryRepo.findById(saved.id).await().indefinitely();
        assertThat(found).isNotNull();
    }

    @Test
    void testFindAllExcludesTrashed() {
        createReview("Active Review", 1, 1);
        Review trashed = createReview("Trashed Review", 2, 1);
        reviewCommandRepo.trash(trashed.id).await().indefinitely();
        FindAllReview req = new FindAllReview();
        req.setPage(1);
        req.setPageSize(10);
        var result = reviewQueryRepo.findReviews(req).await().indefinitely();
        assertThat(result.getData()).hasSize(1);
    }

    @Test
    void testTrashReview() {
        Review saved = createReview("ToTrash", 1, 1);
        Review trashed = reviewCommandRepo.trash(saved.id).await().indefinitely();
        assertThat(trashed).isNotNull();
        assertThat(trashed.getDeletedAt()).isNotNull();
    }

    @Test
    void testRestoreReview() {
        Review saved = createReview("ToRestore", 1, 1);
        reviewCommandRepo.trash(saved.id).await().indefinitely();
        Review restored = reviewCommandRepo.restore(saved.id).await().indefinitely();
        assertThat(restored).isNotNull();
        assertThat(restored.getDeletedAt()).isNull();
    }

    @Test
    void testDeletePermanent() {
        Review saved = createReview("ToDelete", 1, 1);
        reviewCommandRepo.trash(saved.id).await().indefinitely();
        Review deleted = reviewCommandRepo.deletePermanent(saved.id).await().indefinitely();
        assertThat(deleted).isNotNull();
        assertThat(reviewQueryRepo.findById(saved.id).await().indefinitely()).isNull();
    }
}
