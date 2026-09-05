package com.sanedge.review_detail.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.sanedge.review_detail.entity.ReviewDetail;

import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
@TestReactiveTransaction
class ReviewDetailRepositoryTest {
    @Inject ReviewDetailRepository reviewDetailRepository;

    private ReviewDetail createDetail(String comment, Long reviewId) {
        ReviewDetail d = new ReviewDetail();
        d.setComment(comment);
        d.setReviewId(reviewId);
        d.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
        d.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
        return reviewDetailRepository.persist(d).await().indefinitely();
    }

    @Test
    void testCreateAndFindById() {
        ReviewDetail saved = createDetail("Loved it", 1L);
        assertThat(saved).isNotNull();
        ReviewDetail found = reviewDetailRepository.findById(saved.id).await().indefinitely();
        assertThat(found).isNotNull();
    }

    @Test
    void testSoftDeleteExcludesFromFind() {
        ReviewDetail saved = createDetail("Trashed", 1L);
        reviewDetailRepository.delete(saved).await().indefinitely();
        ReviewDetail found = reviewDetailRepository.findById(saved.id).await().indefinitely();
        assertThat(found).isNotNull();
    }

    @Test
    void testUpdateDetail() {
        ReviewDetail saved = createDetail("Original", 1L);
        saved.setComment("Updated");
        ReviewDetail updated = reviewDetailRepository.update(saved).await().indefinitely();
        assertThat(updated.getComment()).isEqualTo("Updated");
    }

    @Test
    void testDeleteById() {
        ReviewDetail saved = createDetail("Delete me", 1L);
        reviewDetailRepository.deleteById(saved.id).await().indefinitely();
        ReviewDetail found = reviewDetailRepository.findById(saved.id).await().indefinitely();
        assertThat(found).isNull();
    }
}
