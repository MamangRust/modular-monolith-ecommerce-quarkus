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
import com.sanedge.transaction.domain.requests.FindMonthlyMethodsByMerchantRequest;
import com.sanedge.transaction.domain.requests.YearMethodTransactionMerchantRequest;
import com.sanedge.transaction.domain.response.TransactionMonthlyMethodResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyMethodResponse;
import com.sanedge.transaction.repository.statsbymerchant.TransactionMethodByMerchantRepository;
import com.sanedge.transaction.service.statsbymerchant.TransactionMethodByMerchantService;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TransactionMethodByMerchantServiceImpl implements TransactionMethodByMerchantService {
    private static final Logger logger = LoggerFactory.getLogger(TransactionMethodByMerchantServiceImpl.class);

    private final TransactionMethodByMerchantRepository repository;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    private final TracingMetrics tracingMetrics;

    private static final long STATS_CACHE_TTL_SECONDS = 600;

    @Inject
    public TransactionMethodByMerchantServiceImpl(TransactionMethodByMerchantRepository repository,
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
    public Uni<ApiResponse<List<TransactionMonthlyMethodResponse>>> findMonthlyMethodByMerchantSuccess(
            FindMonthlyMethodsByMerchantRequest req) {
        if (req.getMerchantId() == null || req.getYear() == null || req.getMonth() == null) {
            return Uni.createFrom().item(ApiResponse.error("MerchantId, Year, and Month must not be null"));
        }
        if (req.getMonth() < 1 || req.getMonth() > 12) {
            return Uni.createFrom().item(ApiResponse.error("Month must be between 1 and 12"));
        }

        String cacheKey = String.format("transaction:stats:method:merchant:success:monthly:%d:%d:%d",
                req.getMerchantId(), req.getYear(), req.getMonth());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        ApiResponse<List<TransactionMonthlyMethodResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<TransactionMonthlyMethodResponse>>>() {
                                });
                        return Uni.createFrom().item(response);
                    }

                    return tracingMetrics.traceAndMeasure("findMonthlyMethodByMerchantSuccess",
                            "find_monthly_method_by_merchant_success",
                            Attributes.builder()
                                    .put("merchant.id", req.getMerchantId().toString())
                                    .put("year", req.getYear().toString())
                                    .put("month", req.getMonth().toString())
                                    .build(),
                            () -> repository.findMonthlyTransactionMethodsSuccessByMerchant(req)
                                    .chain(rawData -> {
                                        List<TransactionMonthlyMethodResponse> responseList = rawData.stream()
                                                .map(TransactionMonthlyMethodResponse::from)
                                                .collect(Collectors.toList());

                                        ApiResponse<List<TransactionMonthlyMethodResponse>> response = ApiResponse
                                                .success("Monthly method success for merchant retrieved successfully",
                                                        responseList);

                                        return redisService
                                                .setWithExpirationReactive(cacheKey, toJson(response),
                                                        STATS_CACHE_TTL_SECONDS)
                                                .map(v -> response);
                                    }));
                });
    }

    @Override
    public Uni<ApiResponse<List<TransactionMonthlyMethodResponse>>> findMonthlyMethodByMerchantFailed(
            FindMonthlyMethodsByMerchantRequest req) {
        if (req.getMerchantId() == null || req.getYear() == null || req.getMonth() == null) {
            return Uni.createFrom().item(ApiResponse.error("MerchantId, Year, and Month must not be null"));
        }
        if (req.getMonth() < 1 || req.getMonth() > 12) {
            return Uni.createFrom().item(ApiResponse.error("Month must be between 1 and 12"));
        }

        String cacheKey = String.format("transaction:stats:method:merchant:failed:monthly:%d:%d:%d",
                req.getMerchantId(), req.getYear(), req.getMonth());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        ApiResponse<List<TransactionMonthlyMethodResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<TransactionMonthlyMethodResponse>>>() {
                                });
                        return Uni.createFrom().item(response);
                    }

                    return tracingMetrics.traceAndMeasure("findMonthlyMethodByMerchantFailed",
                            "find_monthly_method_by_merchant_failed",
                            Attributes.builder()
                                    .put("merchant.id", req.getMerchantId().toString())
                                    .put("year", req.getYear().toString())
                                    .put("month", req.getMonth().toString())
                                    .build(),
                            () -> repository.findMonthlyTransactionMethodsFailedByMerchant(req)
                                    .chain(rawData -> {
                                        List<TransactionMonthlyMethodResponse> responseList = rawData.stream()
                                                .map(TransactionMonthlyMethodResponse::from)
                                                .collect(Collectors.toList());

                                        ApiResponse<List<TransactionMonthlyMethodResponse>> response = ApiResponse
                                                .success("Monthly method failed for merchant retrieved successfully",
                                                        responseList);

                                        return redisService
                                                .setWithExpirationReactive(cacheKey, toJson(response),
                                                        STATS_CACHE_TTL_SECONDS)
                                                .map(v -> response);
                                    }));
                });
    }

    @Override
    public Uni<ApiResponse<List<TransactionYearlyMethodResponse>>> findYearlyMethodByMerchantSuccess(
            YearMethodTransactionMerchantRequest req) {
        if (req.getMerchantId() == null || req.getYear() == null) {
            return Uni.createFrom().item(ApiResponse.error("MerchantId and Year must not be null"));
        }

        String cacheKey = String.format("transaction:stats:method:merchant:success:yearly:%d:%d",
                req.getMerchantId(), req.getYear());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        ApiResponse<List<TransactionYearlyMethodResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<TransactionYearlyMethodResponse>>>() {
                                });
                        return Uni.createFrom().item(response);
                    }

                    return tracingMetrics.traceAndMeasure("findYearlyMethodByMerchantSuccess",
                            "find_yearly_method_by_merchant_success",
                            Attributes.builder()
                                    .put("merchant.id", req.getMerchantId().toString())
                                    .put("year", req.getYear().toString())
                                    .build(),
                            () -> repository.findYearlyTransactionMethodsSuccessByMerchant(req.getMerchantId().longValue(),
                                    req.getYear())
                                    .chain(rawData -> {
                                        List<TransactionYearlyMethodResponse> responseList = rawData.stream()
                                                .map(TransactionYearlyMethodResponse::from)
                                                .collect(Collectors.toList());

                                        ApiResponse<List<TransactionYearlyMethodResponse>> response = ApiResponse
                                                .success("Yearly method success for merchant retrieved successfully",
                                                        responseList);

                                        return redisService
                                                .setWithExpirationReactive(cacheKey, toJson(response),
                                                        STATS_CACHE_TTL_SECONDS)
                                                .map(v -> response);
                                    }));
                });
    }

    @Override
    public Uni<ApiResponse<List<TransactionYearlyMethodResponse>>> findYearlyMethodByMerchantFailed(
            YearMethodTransactionMerchantRequest req) {
        if (req.getMerchantId() == null || req.getYear() == null) {
            return Uni.createFrom().item(ApiResponse.error("MerchantId and Year must not be null"));
        }

        String cacheKey = String.format("transaction:stats:method:merchant:failed:yearly:%d:%d",
                req.getMerchantId(), req.getYear());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        ApiResponse<List<TransactionYearlyMethodResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<TransactionYearlyMethodResponse>>>() {
                                });
                        return Uni.createFrom().item(response);
                    }

                    return tracingMetrics.traceAndMeasure("findYearlyMethodByMerchantFailed",
                            "find_yearly_method_by_merchant_failed",
                            Attributes.builder()
                                    .put("merchant.id", req.getMerchantId().toString())
                                    .put("year", req.getYear().toString())
                                    .build(),
                            () -> repository.findYearlyTransactionMethodsFailedByMerchant(req.getMerchantId().longValue(),
                                    req.getYear())
                                    .chain(rawData -> {
                                        List<TransactionYearlyMethodResponse> responseList = rawData.stream()
                                                .map(TransactionYearlyMethodResponse::from)
                                                .collect(Collectors.toList());

                                        ApiResponse<List<TransactionYearlyMethodResponse>> response = ApiResponse
                                                .success("Yearly method failed for merchant retrieved successfully",
                                                        responseList);

                                        return redisService
                                                .setWithExpirationReactive(cacheKey, toJson(response),
                                                        STATS_CACHE_TTL_SECONDS)
                                                .map(v -> response);
                                    }));
                });
    }
}
