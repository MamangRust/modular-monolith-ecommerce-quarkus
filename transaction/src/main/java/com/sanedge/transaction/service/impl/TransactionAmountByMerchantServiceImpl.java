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
import com.sanedge.transaction.domain.requests.FindMonthlyAmountByMerchantRequest;
import com.sanedge.transaction.domain.requests.YearAmountTransactionMerchant;
import com.sanedge.transaction.domain.response.TransactionMonthlyAmountFailedResponse;
import com.sanedge.transaction.domain.response.TransactionMonthlyAmountSuccessResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyAmountFailedResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyAmountSuccessResponse;
import com.sanedge.transaction.repository.statsbymerchant.TransactionAmountByMerchantRepository;
import com.sanedge.transaction.service.statsbymerchant.TransactionAmountByMerchantService;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TransactionAmountByMerchantServiceImpl implements TransactionAmountByMerchantService {
    private static final Logger logger = LoggerFactory.getLogger(TransactionAmountByMerchantServiceImpl.class);

    private final TransactionAmountByMerchantRepository repository;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    private final TracingMetrics tracingMetrics;

    private static final long STATS_CACHE_TTL_SECONDS = 600;

    @Inject
    public TransactionAmountByMerchantServiceImpl(TransactionAmountByMerchantRepository repository,
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
    public Uni<ApiResponse<List<TransactionMonthlyAmountSuccessResponse>>> findMonthlyAmountSuccessByMerchant(
            FindMonthlyAmountByMerchantRequest req) {
        if (req.getMerchantId() == null || req.getYear() == null || req.getMonth() == null) {
            return Uni.createFrom().item(ApiResponse.error("MerchantId, Year, and Month must not be null"));
        }
        if (req.getMonth() < 1 || req.getMonth() > 12) {
            return Uni.createFrom().item(ApiResponse.error("Month must be between 1 and 12"));
        }

        String cacheKey = String.format("transaction:stats:amount:merchant:success:monthly:%d:%d:%d",
                req.getMerchantId(), req.getYear(), req.getMonth());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        ApiResponse<List<TransactionMonthlyAmountSuccessResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<TransactionMonthlyAmountSuccessResponse>>>() {
                                });
                        return Uni.createFrom().item(response);
                    }

                    return tracingMetrics.traceAndMeasure("findMonthlyAmountSuccessByMerchant",
                            "find_monthly_amount_success_by_merchant",
                            Attributes.builder()
                                    .put("merchant.id", req.getMerchantId().toString())
                                    .put("year", req.getYear().toString())
                                    .put("month", req.getMonth().toString())
                                    .build(),
                            () -> repository.findMonthlySuccessByMerchant(req)
                                    .chain(rawData -> {
                                        List<TransactionMonthlyAmountSuccessResponse> responseList = rawData.stream()
                                                .map(TransactionMonthlyAmountSuccessResponse::from)
                                                .collect(Collectors.toList());

                                        ApiResponse<List<TransactionMonthlyAmountSuccessResponse>> response = ApiResponse
                                                .success("Monthly amount success for merchant retrieved successfully",
                                                        responseList);

                                        return redisService
                                                .setWithExpirationReactive(cacheKey, toJson(response),
                                                        STATS_CACHE_TTL_SECONDS)
                                                .map(v -> response);
                                    }));
                });
    }

    @Override
    public Uni<ApiResponse<List<TransactionYearlyAmountSuccessResponse>>> findYearlyAmountSuccessByMerchant(
            YearAmountTransactionMerchant req) {
        if (req.getMerchantId() == null || req.getYear() == null) {
            return Uni.createFrom().item(ApiResponse.error("MerchantId and Year must not be null"));
        }

        String cacheKey = String.format("transaction:stats:amount:merchant:success:yearly:%d:%d",
                req.getMerchantId(), req.getYear());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        ApiResponse<List<TransactionYearlyAmountSuccessResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<TransactionYearlyAmountSuccessResponse>>>() {
                                });
                        return Uni.createFrom().item(response);
                    }

                    return tracingMetrics.traceAndMeasure("findYearlyAmountSuccessByMerchant",
                            "find_yearly_amount_success_by_merchant",
                            Attributes.builder()
                                    .put("merchant.id", req.getMerchantId().toString())
                                    .put("year", req.getYear().toString())
                                    .build(),
                            () -> repository.findYearlySuccessByMerchant(req.getMerchantId().longValue(),
                                    req.getYear())
                                    .chain(rawData -> {
                                        List<TransactionYearlyAmountSuccessResponse> responseList = rawData.stream()
                                                .map(TransactionYearlyAmountSuccessResponse::from)
                                                .collect(Collectors.toList());

                                        ApiResponse<List<TransactionYearlyAmountSuccessResponse>> response = ApiResponse
                                                .success("Yearly amount success for merchant retrieved successfully",
                                                        responseList);

                                        return redisService
                                                .setWithExpirationReactive(cacheKey, toJson(response),
                                                        STATS_CACHE_TTL_SECONDS)
                                                .map(v -> response);
                                    }));
                });
    }

    @Override
    public Uni<ApiResponse<List<TransactionMonthlyAmountFailedResponse>>> findMonthlyAmountFailedByMerchant(
            FindMonthlyAmountByMerchantRequest req) {
        if (req.getMerchantId() == null || req.getYear() == null || req.getMonth() == null) {
            return Uni.createFrom().item(ApiResponse.error("MerchantId, Year, and Month must not be null"));
        }
        if (req.getMonth() < 1 || req.getMonth() > 12) {
            return Uni.createFrom().item(ApiResponse.error("Month must be between 1 and 12"));
        }

        String cacheKey = String.format("transaction:stats:amount:merchant:failed:monthly:%d:%d:%d",
                req.getMerchantId(), req.getYear(), req.getMonth());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        ApiResponse<List<TransactionMonthlyAmountFailedResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<TransactionMonthlyAmountFailedResponse>>>() {
                                });
                        return Uni.createFrom().item(response);
                    }

                    return tracingMetrics.traceAndMeasure("findMonthlyAmountFailedByMerchant",
                            "find_monthly_amount_failed_by_merchant",
                            Attributes.builder()
                                    .put("merchant.id", req.getMerchantId().toString())
                                    .put("year", req.getYear().toString())
                                    .put("month", req.getMonth().toString())
                                    .build(),
                            () -> repository.findMonthlyFailedByMerchant(req)
                                    .chain(rawData -> {
                                        List<TransactionMonthlyAmountFailedResponse> responseList = rawData.stream()
                                                .map(TransactionMonthlyAmountFailedResponse::from)
                                                .collect(Collectors.toList());

                                        ApiResponse<List<TransactionMonthlyAmountFailedResponse>> response = ApiResponse
                                                .success("Monthly amount failed for merchant retrieved successfully",
                                                        responseList);

                                        return redisService
                                                .setWithExpirationReactive(cacheKey, toJson(response),
                                                        STATS_CACHE_TTL_SECONDS)
                                                .map(v -> response);
                                    }));
                });
    }

    @Override
    public Uni<ApiResponse<List<TransactionYearlyAmountFailedResponse>>> findYearlyAmountFailedByMerchant(
            YearAmountTransactionMerchant req) {
        if (req.getMerchantId() == null || req.getYear() == null) {
            return Uni.createFrom().item(ApiResponse.error("MerchantId and Year must not be null"));
        }

        String cacheKey = String.format("transaction:stats:amount:merchant:failed:yearly:%d:%d",
                req.getMerchantId(), req.getYear());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        ApiResponse<List<TransactionYearlyAmountFailedResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<TransactionYearlyAmountFailedResponse>>>() {
                                });
                        return Uni.createFrom().item(response);
                    }

                    return tracingMetrics.traceAndMeasure("findYearlyAmountFailedByMerchant",
                            "find_yearly_amount_failed_by_merchant",
                            Attributes.builder()
                                    .put("merchant.id", req.getMerchantId().toString())
                                    .put("year", req.getYear().toString())
                                    .build(),
                            () -> repository.findYearlyFailedByMerchant(req.getMerchantId().longValue(), req.getYear())
                                    .chain(rawData -> {
                                        List<TransactionYearlyAmountFailedResponse> responseList = rawData.stream()
                                                .map(TransactionYearlyAmountFailedResponse::from)
                                                .collect(Collectors.toList());

                                        ApiResponse<List<TransactionYearlyAmountFailedResponse>> response = ApiResponse
                                                .success("Yearly amount failed for merchant retrieved successfully",
                                                        responseList);

                                        return redisService
                                                .setWithExpirationReactive(cacheKey, toJson(response),
                                                        STATS_CACHE_TTL_SECONDS)
                                                .map(v -> response);
                                    }));
                });
    }
}
