package com.sanedge.review_detail.service.impl;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.review_detail.domain.requests.CreateReviewDetailRequest;
import com.sanedge.review_detail.domain.requests.UpdateReviewDetailRequest;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.review_detail.domain.response.ReviewDetailResponse;
import com.sanedge.review_detail.domain.response.ReviewDetailResponseDeleteAt;
import com.sanedge.review_detail.entity.ReviewDetail;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.review_detail.repository.ReviewDetailRepository;
import com.sanedge.review_detail.service.ReviewDetailService;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

@ApplicationScoped
public class ReviewDetailServiceImpl implements ReviewDetailService {
    private static final Logger logger = LoggerFactory.getLogger(ReviewDetailServiceImpl.class);

    ReviewDetailRepository reviewDetailRepository;
    Validator validator;
    OpenTelemetry openTelemetry;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    @Inject
    public ReviewDetailServiceImpl(ReviewDetailRepository reviewDetailRepository,
            Validator validator,
            OpenTelemetry openTelemetry) {
        this.reviewDetailRepository = reviewDetailRepository;
        this.validator = validator;
        this.openTelemetry = openTelemetry;
        this.tracer = openTelemetry.getTracer("review-detail-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("review-detail-service");

        this.requestsTotal = meter.counterBuilder("requests_total")
                .setDescription("Total number of requests")
                .build();
        this.requestDurationSeconds = meter.histogramBuilder("request_duration_seconds")
                .setDescription("Request duration in seconds")
                .setUnit("s")
                .build();
    }

    private <T> void validateRequest(T req) {
        Set<ConstraintViolation<T>> violations = validator.validate(req);
        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (ConstraintViolation<T> violation : violations) {
                sb.append(violation.getPropertyPath()).append(": ").append(violation.getMessage()).append("; ");
            }
            logger.error("Validation failed: {}", sb);
            throw new ConstraintViolationException("Validation failed: " + sb, violations);
        }
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<List<ReviewDetailResponse>>> create(List<CreateReviewDetailRequest> requests) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("createReviewDetails")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "review-detail-service")
                .setAttribute("operation", "create_review_details")
                .startSpan();

        try {
            validateRequest(requests);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            return Uni.createFrom().failure(e);
        }

        List<Uni<ReviewDetailResponse>> unis = new ArrayList<>();
        for (CreateReviewDetailRequest req : requests) {
            Uni<ReviewDetailResponse> itemUni = Uni.createFrom().item(() -> {
                logger.info("🆕 Creating review detail for reviewId={} type={}", req.getReviewId(), req.getType());

                ReviewDetail reviewDetail = new ReviewDetail();
                reviewDetail.setReviewId(req.getReviewId());
                reviewDetail.setType(req.getType());
                reviewDetail.setUrl(req.getFile());
                reviewDetail.setCaption(req.getCaption());
                reviewDetail.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
                reviewDetail.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
                return reviewDetail;
            }).chain(reviewDetail -> reviewDetailRepository.persist(reviewDetail)
                    .map(ReviewDetailResponse::from));

            unis.add(itemUni);
        }

        if (unis.isEmpty()) {
            return Uni.createFrom().item(ApiResponse.success("No requests provided", new ArrayList<>()));
        }

        return Uni.join().all(unis).andCollectFailures()
                .map(responses -> {
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_review_details",
                            AttributeKey.stringKey("status"), "success"));
                    return ApiResponse.success("✅ Review details created successfully!", responses);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to create review details", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_review_details",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                    throw new RuntimeException("Failed to create review details: " + e.getMessage(), e);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_review_details"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<List<ReviewDetailResponse>>> update(List<UpdateReviewDetailRequest> requests) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("updateReviewDetails")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "review-detail-service")
                .setAttribute("operation", "update_review_details")
                .startSpan();

        try {
            validateRequest(requests);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            return Uni.createFrom().failure(e);
        }

        List<Uni<ReviewDetailResponse>> unis = new ArrayList<>();
        for (UpdateReviewDetailRequest req : requests) {
            Uni<ReviewDetailResponse> itemUni = reviewDetailRepository.findById(req.getReviewDetailId().longValue())
                    .chain(reviewDetail -> {
                        if (reviewDetail == null) {
                            logger.warn("Review detail not found id={}", req.getReviewDetailId());
                            throw new ResourceNotFoundException(
                                    "Review detail not found with id=" + req.getReviewDetailId());
                        }

                        return Uni.createFrom().item(() -> {
                            reviewDetail.setType(req.getType());
                            reviewDetail.setUrl(req.getFile());
                            reviewDetail.setCaption(req.getCaption());
                            reviewDetail.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
                            return reviewDetail;
                        }).chain(updated -> reviewDetailRepository.persist(updated)
                                .map(ReviewDetailResponse::from));
                    });

            unis.add(itemUni);
        }

        if (unis.isEmpty()) {
            return Uni.createFrom().item(ApiResponse.success("No requests provided", new ArrayList<>()));
        }

        return Uni.join().all(unis).andCollectFailures()
                .map(responses -> {
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_review_details",
                            AttributeKey.stringKey("status"), "success"));
                    return ApiResponse.success("✅ Review details updated successfully!", responses);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to update review details", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_review_details",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                    throw new RuntimeException("Failed to update review details: " + e.getMessage(), e);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_review_details"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<ReviewDetailResponseDeleteAt>> trash(Integer reviewDetailId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("trashReviewDetail")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "review-detail-service")
                .setAttribute("operation", "trash_review_detail")
                .setAttribute("review.detail.id", reviewDetailId.toString())
                .startSpan();

        logger.info("🗑️ Trashing review detail id={}", reviewDetailId);

        return reviewDetailRepository.trashed(reviewDetailId.longValue())
                .map(trashed -> {
                    if (trashed == null) {
                        throw new ResourceNotFoundException("Review detail not found or already trashed");
                    }
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_review_detail",
                            AttributeKey.stringKey("status"), "success"));
                    return ApiResponse.success("🗑️ Review detail trashed successfully!",
                            ReviewDetailResponseDeleteAt.from(trashed));
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to trash review detail id={}", reviewDetailId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_review_detail",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_review_detail"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<ReviewDetailResponseDeleteAt>> restore(Integer reviewDetailId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreReviewDetail")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "review-detail-service")
                .setAttribute("operation", "restore_review_detail")
                .setAttribute("review.detail.id", reviewDetailId.toString())
                .startSpan();

        logger.info("♻️ Restoring review detail id={}", reviewDetailId);

        return reviewDetailRepository.restore(reviewDetailId.longValue())
                .map(restored -> {
                    if (restored == null) {
                        throw new ResourceNotFoundException("Review detail not found or not trashed");
                    }
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_review_detail",
                            AttributeKey.stringKey("status"), "success"));
                    return ApiResponse.success("♻️ Review detail restored successfully!",
                            ReviewDetailResponseDeleteAt.from(restored));
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to restore review detail id={}", reviewDetailId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_review_detail",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_review_detail"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> delete(Integer reviewDetailId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteReviewDetail")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "review-detail-service")
                .setAttribute("operation", "delete_review_detail_permanent")
                .setAttribute("review.detail.id", reviewDetailId.toString())
                .startSpan();

        logger.warn("🧨 Permanently deleting review detail id={}", reviewDetailId);

        return reviewDetailRepository.deletePermanent(reviewDetailId.longValue())
                .map(deleted -> {
                    if (!deleted) {
                        throw new ResourceNotFoundException("Failed to permanently delete review detail");
                    }
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_review_detail_permanent",
                            AttributeKey.stringKey("status"), "success"));
                    return ApiResponse.success("🧨 Review detail permanently deleted!", true);
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to permanently delete review detail id={}", reviewDetailId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_review_detail_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_review_detail_permanent"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> restoreAll() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreAllReviewDetails")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "review-detail-service")
                .setAttribute("operation", "restore_all_review_details")
                .startSpan();

        logger.info("🔄 Restoring ALL trashed review details");

        return reviewDetailRepository.restoreAllDeleted()
                .map(restored -> {
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_review_details",
                            AttributeKey.stringKey("status"), "success"));
                    return ApiResponse.success("🔄 All review details restored successfully!", restored);
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to restore all review details", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_review_details",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_review_details"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteAll() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteAllReviewDetails")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "review-detail-service")
                .setAttribute("operation", "delete_all_review_details_permanent")
                .startSpan();

        logger.warn("💣 Permanently deleting ALL trashed review details");

        return reviewDetailRepository.deleteAllDeleted()
                .map(deleted -> {
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_review_details_permanent",
                            AttributeKey.stringKey("status"), "success"));
                    return ApiResponse.success("💣 All review details permanently deleted!", deleted);
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to delete all review details", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_review_details_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_review_details_permanent"));
                });
    }

    @Override
    public Uni<com.sanedge.common.domain.response.ApiResponsePagination<List<ReviewDetailResponse>>> findAll(int page, int size, String search) {
        String query = "";
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        if (search != null && !search.trim().isEmpty()) {
            query = "caption LIKE :search OR url LIKE :search";
            params.put("search", "%" + search + "%");
        }
        
        Uni<List<ReviewDetail>> listUni;
        Uni<Long> countUni;
        if (query.isEmpty()) {
            listUni = reviewDetailRepository.findAll().page(page - 1, size).list();
            countUni = reviewDetailRepository.count();
        } else {
            listUni = reviewDetailRepository.find(query, params).page(page - 1, size).list();
            countUni = reviewDetailRepository.count(query, params);
        }

        return Uni.combine().all().unis(listUni, countUni).asTuple()
                .map(tuple -> {
                    List<ReviewDetailResponse> list = tuple.getItem1().stream()
                            .map(ReviewDetailResponse::from)
                            .collect(java.util.stream.Collectors.toList());
                    long totalCount = tuple.getItem2();
                    int totalPages = (int) ((totalCount + size - 1) / size);
                    com.sanedge.common.domain.response.PaginationMeta pagination = new com.sanedge.common.domain.response.PaginationMeta(
                            page,
                            size,
                            totalPages,
                            (int) totalCount
                    );
                    return new com.sanedge.common.domain.response.ApiResponsePagination<>("success", "success", list, pagination);
                });
    }

    @Override
    public Uni<com.sanedge.common.domain.response.ApiResponsePagination<List<ReviewDetailResponseDeleteAt>>> findByActive(int page, int size, String search) {
        String query = "deletedAt IS NULL";
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        if (search != null && !search.trim().isEmpty()) {
            query += " AND (caption LIKE :search OR url LIKE :search)";
            params.put("search", "%" + search + "%");
        }
        
        Uni<List<ReviewDetail>> listUni;
        Uni<Long> countUni;
        if (params.isEmpty()) {
            listUni = reviewDetailRepository.find(query).page(page - 1, size).list();
            countUni = reviewDetailRepository.count(query);
        } else {
            listUni = reviewDetailRepository.find(query, params).page(page - 1, size).list();
            countUni = reviewDetailRepository.count(query, params);
        }

        return Uni.combine().all().unis(listUni, countUni).asTuple()
                .map(tuple -> {
                    List<ReviewDetailResponseDeleteAt> list = tuple.getItem1().stream()
                            .map(ReviewDetailResponseDeleteAt::from)
                            .collect(java.util.stream.Collectors.toList());
                    long totalCount = tuple.getItem2();
                    int totalPages = (int) ((totalCount + size - 1) / size);
                    com.sanedge.common.domain.response.PaginationMeta pagination = new com.sanedge.common.domain.response.PaginationMeta(
                            page,
                            size,
                            totalPages,
                            (int) totalCount
                    );
                    return new com.sanedge.common.domain.response.ApiResponsePagination<>("success", "success", list, pagination);
                });
    }

    @Override
    public Uni<com.sanedge.common.domain.response.ApiResponsePagination<List<ReviewDetailResponseDeleteAt>>> findByTrashed(int page, int size, String search) {
        String query = "deletedAt IS NOT NULL";
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        if (search != null && !search.trim().isEmpty()) {
            query += " AND (caption LIKE :search OR url LIKE :search)";
            params.put("search", "%" + search + "%");
        }
        
        Uni<List<ReviewDetail>> listUni;
        Uni<Long> countUni;
        if (params.isEmpty()) {
            listUni = reviewDetailRepository.find(query).page(page - 1, size).list();
            countUni = reviewDetailRepository.count(query);
        } else {
            listUni = reviewDetailRepository.find(query, params).page(page - 1, size).list();
            countUni = reviewDetailRepository.count(query, params);
        }

        return Uni.combine().all().unis(listUni, countUni).asTuple()
                .map(tuple -> {
                    List<ReviewDetailResponseDeleteAt> list = tuple.getItem1().stream()
                            .map(ReviewDetailResponseDeleteAt::from)
                            .collect(java.util.stream.Collectors.toList());
                    long totalCount = tuple.getItem2();
                    int totalPages = (int) ((totalCount + size - 1) / size);
                    com.sanedge.common.domain.response.PaginationMeta pagination = new com.sanedge.common.domain.response.PaginationMeta(
                            page,
                            size,
                            totalPages,
                            (int) totalCount
                    );
                    return new com.sanedge.common.domain.response.ApiResponsePagination<>("success", "success", list, pagination);
                });
    }

    @Override
    public Uni<ApiResponse<ReviewDetailResponse>> findById(Integer id) {
        return reviewDetailRepository.findById(id.longValue())
                .map(detail -> {
                    if (detail == null) {
                        throw new ResourceNotFoundException("Review detail not found with id=" + id);
                    }
                    return ApiResponse.success("success", ReviewDetailResponse.from(detail));
                });
    }
}
