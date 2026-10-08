package com.sanedge.transaction.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.transaction.domain.requests.MonthAmountTransactionRequest;
import com.sanedge.transaction.domain.response.TransactionMonthlyAmountFailedResponse;
import com.sanedge.transaction.domain.response.TransactionMonthlyAmountSuccessResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyAmountFailedResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyAmountSuccessResponse;
import com.sanedge.transaction.repository.stats.TransactionAmountStatusRepository;
import com.sanedge.transaction.service.stats.TransactionAmountService;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TransactionAmountServiceImpl implements TransactionAmountService {
    private static final Logger logger = LoggerFactory.getLogger(TransactionAmountServiceImpl.class);

    private final TransactionAmountStatusRepository repository;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    private final TracingMetrics tracingMetrics;

    private static final long STATS_CACHE_TTL_SECONDS = 600;

    @Inject
    public TransactionAmountServiceImpl(TransactionAmountStatusRepository repository,
            RedisService redisService,
            ObjectMapper objectMapper,
            TracingMetrics tracingMetrics) {
        this.repository = repository;
        this.redisService = redisService;
        this.objectMapper = objectMapper;
        this.tracingMetrics = tracingMetrics;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            logger.error("Error serializing object to JSON", e);
            throw new RuntimeException("Failed to serialize object", e);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            logger.error("Error deserializing JSON to object", e);
            throw new RuntimeException("Failed to deserialize JSON", e);
        }
    }

    @Override
    public Uni<ApiResponse<List<TransactionMonthlyAmountSuccessResponse>>> findMonthlyAmountSuccess(
            MonthAmountTransactionRequest req) {
        if (req.getYear() == null || req.getMonth() == null) {
            return Uni.createFrom().item(ApiResponse.error("Year and Month must not be null"));
        }
        if (req.getMonth() < 1 || req.getMonth() > 12) {
            return Uni.createFrom().item(ApiResponse.error("Month must be between 1 and 12"));
        }

        String cacheKey = String.format("transaction:stats:amount:success:monthly:%d:%d", req.getYear(),
                req.getMonth());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        ApiResponse<List<TransactionMonthlyAmountSuccessResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<TransactionMonthlyAmountSuccessResponse>>>() {
                                });
                        return Uni.createFrom().item(response);
                    }

                    return tracingMetrics.traceAndMeasure("findMonthlyAmountSuccess",
                            "find_monthly_amount_success",
                            Attributes.builder()
                                    .put("year", req.getYear().toString())
                                    .put("month", req.getMonth().toString())
                                    .build(),
                            () -> repository.findMonthlyTransactionSuccess(req.getYear(), req.getMonth())
                                    .chain(rawData -> {
                                        List<TransactionMonthlyAmountSuccessResponse> responseList = rawData.stream()
                                                .map(TransactionMonthlyAmountSuccessResponse::from)
                                                .collect(Collectors.toList());

                                        ApiResponse<List<TransactionMonthlyAmountSuccessResponse>> response = ApiResponse
                                                .success("Monthly amount success retrieved successfully",
                                                        responseList);

                                        return redisService
                                                .setWithExpirationReactive(cacheKey, toJson(response),
                                                        STATS_CACHE_TTL_SECONDS)
                                                .map(v -> response);
                                    }));
                });
    }

    @Override
    public Uni<ApiResponse<List<TransactionYearlyAmountSuccessResponse>>> findYearlyAmountSuccess(Integer year) {
        if (year == null) {
            return Uni.createFrom().item(ApiResponse.error("Year must not be null"));
        }

        String cacheKey = "transaction:stats:amount:success:yearly:" + year;

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        ApiResponse<List<TransactionYearlyAmountSuccessResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<TransactionYearlyAmountSuccessResponse>>>() {
                                });
                        return Uni.createFrom().item(response);
                    }

                    return tracingMetrics.traceAndMeasure("findYearlyAmountSuccess", "find_yearly_amount_success",
                            Attributes.builder().put("year", year.toString()).build(),
                            () -> repository.findYearlyTransactionSuccess(year)
                                    .chain(rawData -> {
                                        List<TransactionYearlyAmountSuccessResponse> responseList = rawData.stream()
                                                .map(TransactionYearlyAmountSuccessResponse::from)
                                                .collect(Collectors.toList());

                                        ApiResponse<List<TransactionYearlyAmountSuccessResponse>> response = ApiResponse
                                                .success("Yearly amount success retrieved successfully", responseList);

                                        return redisService
                                                .setWithExpirationReactive(cacheKey, toJson(response),
                                                        STATS_CACHE_TTL_SECONDS)
                                                .map(v -> response);
                                    }));
                });
    }

    @Override
    public Uni<ApiResponse<List<TransactionMonthlyAmountFailedResponse>>> findMonthlyAmountFailed(
            MonthAmountTransactionRequest req) {
        if (req.getYear() == null || req.getMonth() == null) {
            return Uni.createFrom().item(ApiResponse.error("Year and Month must not be null"));
        }
        if (req.getMonth() < 1 || req.getMonth() > 12) {
            return Uni.createFrom().item(ApiResponse.error("Month must be between 1 and 12"));
        }

        String cacheKey = String.format("transaction:stats:amount:failed:monthly:%d:%d", req.getYear(),
                req.getMonth());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        ApiResponse<List<TransactionMonthlyAmountFailedResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<TransactionMonthlyAmountFailedResponse>>>() {
                                });
                        return Uni.createFrom().item(response);
                    }

                    return tracingMetrics.traceAndMeasure("findMonthlyAmountFailed", "find_monthly_amount_failed",
                            Attributes.builder()
                                    .put("year", req.getYear().toString())
                                    .put("month", req.getMonth().toString())
                                    .build(),
                            () -> repository.findMonthlyTransactionFailed(req.getYear(), req.getMonth())
                                    .chain(rawData -> {
                                        List<TransactionMonthlyAmountFailedResponse> responseList = rawData.stream()
                                                .map(TransactionMonthlyAmountFailedResponse::from)
                                                .collect(Collectors.toList());

                                        ApiResponse<List<TransactionMonthlyAmountFailedResponse>> response = ApiResponse
                                                .success("Monthly amount failed retrieved successfully", responseList);

                                        return redisService
                                                .setWithExpirationReactive(cacheKey, toJson(response),
                                                        STATS_CACHE_TTL_SECONDS)
                                                .map(v -> response);
                                    }));
                });
    }

    @Override
    public Uni<ApiResponse<List<TransactionYearlyAmountFailedResponse>>> findYearlyAmountFailed(Integer year) {
        if (year == null) {
            return Uni.createFrom().item(ApiResponse.error("Year must not be null"));
        }

        String cacheKey = "transaction:stats:amount:failed:yearly:" + year;

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        ApiResponse<List<TransactionYearlyAmountFailedResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<TransactionYearlyAmountFailedResponse>>>() {
                                });
                        return Uni.createFrom().item(response);
                    }

                    return tracingMetrics.traceAndMeasure("findYearlyAmountFailed", "find_yearly_amount_failed",
                            Attributes.builder().put("year", year.toString()).build(),
                            () -> repository.findYearlyTransactionFailed(year)
                                    .chain(rawData -> {
                                        List<TransactionYearlyAmountFailedResponse> responseList = rawData.stream()
                                                .map(TransactionYearlyAmountFailedResponse::from)
                                                .collect(Collectors.toList());

                                        ApiResponse<List<TransactionYearlyAmountFailedResponse>> response = ApiResponse
                                                .success("Yearly amount failed retrieved successfully", responseList);

                                        return redisService
                                                .setWithExpirationReactive(cacheKey, toJson(response),
                                                        STATS_CACHE_TTL_SECONDS)
                                                .map(v -> response);
                                    }));
                });
    }
}
