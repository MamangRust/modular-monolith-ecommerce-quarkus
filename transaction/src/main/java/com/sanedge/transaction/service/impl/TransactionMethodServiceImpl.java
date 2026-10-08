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
import com.sanedge.transaction.domain.requests.MonthMethodTransactionRequest;
import com.sanedge.transaction.domain.response.TransactionMonthlyMethodResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyMethodResponse;
import com.sanedge.transaction.repository.stats.TransactionMethodRepository;
import com.sanedge.transaction.service.stats.TransactionMethodService;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TransactionMethodServiceImpl implements TransactionMethodService {
    private static final Logger logger = LoggerFactory.getLogger(TransactionMethodServiceImpl.class);

    private final TransactionMethodRepository repository;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    private final TracingMetrics tracingMetrics;

    private static final long STATS_CACHE_TTL_SECONDS = 600;

    @Inject
    public TransactionMethodServiceImpl(TransactionMethodRepository repository,
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
    public Uni<ApiResponse<List<TransactionMonthlyMethodResponse>>> findMonthlyMethodSuccess(
            MonthMethodTransactionRequest req) {
        if (req.getYear() == null || req.getMonth() == null) {
            return Uni.createFrom().item(ApiResponse.error("Year and Month must not be null"));
        }
        if (req.getMonth() < 1 || req.getMonth() > 12) {
            return Uni.createFrom().item(ApiResponse.error("Month must be between 1 and 12"));
        }

        String cacheKey = String.format("transaction:stats:method:success:monthly:%d:%d", req.getYear(),
                req.getMonth());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        ApiResponse<List<TransactionMonthlyMethodResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<TransactionMonthlyMethodResponse>>>() {
                                });
                        return Uni.createFrom().item(response);
                    }

                    return tracingMetrics.traceAndMeasure("findMonthlyMethodSuccess", "find_monthly_method_success",
                            Attributes.builder()
                                    .put("year", req.getYear().toString())
                                    .put("month", req.getMonth().toString())
                                    .build(),
                            () -> repository.findMonthlyMethodsSuccess(req.getYear(), req.getMonth())
                                    .chain(rawData -> {
                                        List<TransactionMonthlyMethodResponse> responseList = rawData.stream()
                                                .map(TransactionMonthlyMethodResponse::from)
                                                .collect(Collectors.toList());

                                        ApiResponse<List<TransactionMonthlyMethodResponse>> response = ApiResponse
                                                .success("Monthly method success retrieved successfully", responseList);

                                        return redisService
                                                .setWithExpirationReactive(cacheKey, toJson(response),
                                                        STATS_CACHE_TTL_SECONDS)
                                                .map(v -> response);
                                    }));
                });
    }

    @Override
    public Uni<ApiResponse<List<TransactionYearlyMethodResponse>>> findYearlyMethodSuccess(Integer year) {
        if (year == null) {
            return Uni.createFrom().item(ApiResponse.error("Year must not be null"));
        }

        String cacheKey = "transaction:stats:method:success:yearly:" + year;

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        ApiResponse<List<TransactionYearlyMethodResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<TransactionYearlyMethodResponse>>>() {
                                });
                        return Uni.createFrom().item(response);
                    }

                    return tracingMetrics.traceAndMeasure("findYearlyMethodSuccess", "find_yearly_method_success",
                            Attributes.builder().put("year", year.toString()).build(),
                            () -> repository.findYearlyMethodsSuccess(year)
                                    .chain(rawData -> {
                                        List<TransactionYearlyMethodResponse> responseList = rawData.stream()
                                                .map(TransactionYearlyMethodResponse::from)
                                                .collect(Collectors.toList());

                                        ApiResponse<List<TransactionYearlyMethodResponse>> response = ApiResponse
                                                .success("Yearly method success retrieved successfully", responseList);

                                        return redisService
                                                .setWithExpirationReactive(cacheKey, toJson(response),
                                                        STATS_CACHE_TTL_SECONDS)
                                                .map(v -> response);
                                    }));
                });
    }

    @Override
    public Uni<ApiResponse<List<TransactionMonthlyMethodResponse>>> findMonthlyMethodFailed(
            MonthMethodTransactionRequest req) {
        if (req.getYear() == null || req.getMonth() == null) {
            return Uni.createFrom().item(ApiResponse.error("Year and Month must not be null"));
        }
        if (req.getMonth() < 1 || req.getMonth() > 12) {
            return Uni.createFrom().item(ApiResponse.error("Month must be between 1 and 12"));
        }

        String cacheKey = String.format("transaction:stats:method:failed:monthly:%d:%d", req.getYear(),
                req.getMonth());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        ApiResponse<List<TransactionMonthlyMethodResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<TransactionMonthlyMethodResponse>>>() {
                                });
                        return Uni.createFrom().item(response);
                    }

                    return tracingMetrics.traceAndMeasure("findMonthlyMethodFailed", "find_monthly_method_failed",
                            Attributes.builder()
                                    .put("year", req.getYear().toString())
                                    .put("month", req.getMonth().toString())
                                    .build(),
                            () -> repository.findMonthlyMethodsFailed(req.getYear(), req.getMonth())
                                    .chain(rawData -> {
                                        List<TransactionMonthlyMethodResponse> responseList = rawData.stream()
                                                .map(TransactionMonthlyMethodResponse::from)
                                                .collect(Collectors.toList());

                                        ApiResponse<List<TransactionMonthlyMethodResponse>> response = ApiResponse
                                                .success("Monthly method failed retrieved successfully", responseList);

                                        return redisService
                                                .setWithExpirationReactive(cacheKey, toJson(response),
                                                        STATS_CACHE_TTL_SECONDS)
                                                .map(v -> response);
                                    }));
                });
    }

    @Override
    public Uni<ApiResponse<List<TransactionYearlyMethodResponse>>> findYearlyMethodFailed(Integer year) {
        if (year == null) {
            return Uni.createFrom().item(ApiResponse.error("Year must not be null"));
        }

        String cacheKey = "transaction:stats:method:failed:yearly:" + year;

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        ApiResponse<List<TransactionYearlyMethodResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<TransactionYearlyMethodResponse>>>() {
                                });
                        return Uni.createFrom().item(response);
                    }

                    return tracingMetrics.traceAndMeasure("findYearlyMethodFailed", "find_yearly_method_failed",
                            Attributes.builder().put("year", year.toString()).build(),
                            () -> repository.findYearlyMethodsFailed(year)
                                    .chain(rawData -> {
                                        List<TransactionYearlyMethodResponse> responseList = rawData.stream()
                                                .map(TransactionYearlyMethodResponse::from)
                                                .collect(Collectors.toList());

                                        ApiResponse<List<TransactionYearlyMethodResponse>> response = ApiResponse
                                                .success("Yearly method failed retrieved successfully", responseList);

                                        return redisService
                                                .setWithExpirationReactive(cacheKey, toJson(response),
                                                        STATS_CACHE_TTL_SECONDS)
                                                .map(v -> response);
                                    }));
                });
    }
}
