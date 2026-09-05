package com.sanedge.review.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.review.domain.requests.FindAllReview;
import com.sanedge.review.entity.Review;
import com.sanedge.review.repository.ReviewQueryRepository;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@ExtendWith(MockitoExtension.class)
class ReviewQueryServiceImplTest {

    @Mock
    private ReviewQueryRepository reviewQueryRepository;
    @Mock
    private RedisService redisService;
    @Mock
    private TracingMetrics tracingMetrics;

    private ReviewQueryServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        lenient().when(tracingMetrics.traceAndMeasure(anyString(), anyString(), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<Uni<?>>) inv.getArgument(2)).get());
        lenient().when(tracingMetrics.traceAndMeasure(anyString(), anyString(), any(Attributes.class), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<Uni<?>>) inv.getArgument(3)).get());

        service = new ReviewQueryServiceImpl(
                reviewQueryRepository,
                redisService,
                objectMapper,
                tracingMetrics);
    }

    private Review mkReview(Long id) {
        Review r = new Review();
        try {
            Field idField = r.getClass().getSuperclass().getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(r, id);
        } catch (Exception e) { throw new RuntimeException(e); }
        r.setComment("Great");
        r.setUserId(1);
        r.setProductId(1);
        r.setRating(5);
        r.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
        r.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
        return r;
    }

    @Test
    void findById_Success() {
        lenient().when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(reviewQueryRepository.findReviewById(anyLong())).thenReturn(Uni.createFrom().item(Optional.of(mkReview(1L))));
        ApiResponse<?> result = service.findById(1).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Review retrieved successfully");
    }

    @Test
    void findById_NotFound_ThrowsResourceNotFound() {
        lenient().when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(reviewQueryRepository.findReviewById(anyLong())).thenReturn(Uni.createFrom().item(Optional.empty()));
        org.junit.jupiter.api.Assertions.assertThrows(ResourceNotFoundException.class,
                () -> service.findById(999).await().indefinitely());
    }

    @Test
    void findById_CacheHit_ReturnsCached() {
        Review review = mkReview(1L);
        Object cached = ApiResponse.success("Review retrieved successfully", com.sanedge.review.domain.response.ReviewResponse.from(review));
        String cachedJson;
        try {
            cachedJson = objectMapper.writeValueAsString(cached);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().item(cachedJson));
        ApiResponse<?> result = service.findById(1).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Review retrieved successfully");
    }

    @Test
    void findAll_Success() {
        FindAllReview req = new FindAllReview();
        req.setPage(1);
        req.setPageSize(10);
        lenient().when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(reviewQueryRepository.findReviews(any(FindAllReview.class)))
                .thenReturn(Uni.createFrom().item(new PagedResult<>(List.of(), 0)));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());
        var result = service.findAll(req).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
    }
}
