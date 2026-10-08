package com.sanedge.order.service.impl;

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
import com.sanedge.order.domain.requests.MonthTotalRevenueMerchantRequest;
import com.sanedge.order.domain.requests.YearTotalRevenueMerchantRequest;
import com.sanedge.order.domain.response.OrderMonthlyTotalRevenueResponse;
import com.sanedge.order.domain.response.OrderYearlyTotalRevenueResponse;
import com.sanedge.order.repository.statsbymerchant.OrderTotalRevenueByMerchantRepository;
import com.sanedge.order.service.OrderTotalRevenueByMerchantService;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OrderTotalRevenueByMerchantServiceImpl implements OrderTotalRevenueByMerchantService {
    private static final Logger logger = LoggerFactory.getLogger(OrderTotalRevenueByMerchantServiceImpl.class);

    private final OrderTotalRevenueByMerchantRepository repository;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    private final TracingMetrics tracingMetrics;

    private static final long STATS_CACHE_TTL_SECONDS = 600;

    @Inject
    public OrderTotalRevenueByMerchantServiceImpl(OrderTotalRevenueByMerchantRepository repository,
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
    public Uni<ApiResponse<List<OrderMonthlyTotalRevenueResponse>>> findMonthlyStatsByMerchant(
            MonthTotalRevenueMerchantRequest req) {
        if (req.getMerchantId() == null || req.getYear() == null || req.getMonth() == null) {
            logger.error("MerchantId, Year, or Month is null | req: {}", req);
            return Uni.createFrom().item(ApiResponse.error("MerchantId, Year, and Month must not be null"));
        }

        if (req.getMonth() < 1 || req.getMonth() > 12) {
            return Uni.createFrom().item(ApiResponse.error("Month must be between 1 and 12"));
        }

        String cacheKey = String.format("order:revenue:merchant:monthly:%d:%d:%d", req.getMerchantId(), req.getYear(),
                req.getMonth());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponse<List<OrderMonthlyTotalRevenueResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<OrderMonthlyTotalRevenueResponse>>>() {
                                });
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);

                    return tracingMetrics.traceAndMeasure("findMonthlyStatsByMerchant",
                            "find_monthly_revenue_stats_by_merchant",
                            Attributes.builder()
                                    .put("merchant.id", req.getMerchantId().toString())
                                    .put("year", req.getYear().toString())
                                    .put("month", req.getMonth().toString())
                                    .build(),
                            () -> repository.findMonthlyTotalRevenueByMerchant(req)
                                    .chain(rawData -> {
                                        List<OrderMonthlyTotalRevenueResponse> responseList = rawData.stream()
                                                .map(OrderMonthlyTotalRevenueResponse::from)
                                                .collect(Collectors.toList());

                                        ApiResponse<List<OrderMonthlyTotalRevenueResponse>> response = ApiResponse
                                                .success(
                                                        "Monthly order stats for merchant retrieved successfully",
                                                        responseList);

                                        return redisService
                                                .setWithExpirationReactive(cacheKey, toJson(response),
                                                        STATS_CACHE_TTL_SECONDS)
                                                .map(v -> {
                                                    logger.info("Cached monthly revenue for merchant key: {}",
                                                            cacheKey);
                                                    return response;
                                                });
                                    })
                                    .onFailure()
                                    .invoke(e -> logger.error("Error fetching monthly order stats for merchantId={}",
                                            req.getMerchantId(), e)));
                });
    }

    @Override
    public Uni<ApiResponse<List<OrderYearlyTotalRevenueResponse>>> findYearlyStatsByMerchant(
            YearTotalRevenueMerchantRequest req) {
        if (req.getMerchantId() == null || req.getYear() == null) {
            logger.error("MerchantId or Year is null | req: {}", req);
            return Uni.createFrom().item(ApiResponse.error("MerchantId and Year must not be null"));
        }

        String cacheKey = String.format("order:revenue:merchant:yearly:%d:%d", req.getMerchantId(), req.getYear());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponse<List<OrderYearlyTotalRevenueResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<OrderYearlyTotalRevenueResponse>>>() {
                                });
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);

                    return tracingMetrics.traceAndMeasure("findYearlyStatsByMerchant",
                            "find_yearly_revenue_stats_by_merchant",
                            Attributes.builder()
                                    .put("merchant.id", req.getMerchantId().toString())
                                    .put("year", req.getYear().toString())
                                    .build(),
                            () -> repository
                                    .findYearlyTotalRevenueByMerchant(req.getMerchantId().longValue(), req.getYear())
                                    .chain(rawData -> {
                                        List<OrderYearlyTotalRevenueResponse> responseList = rawData.stream()
                                                .map(OrderYearlyTotalRevenueResponse::from)
                                                .collect(Collectors.toList());

                                        ApiResponse<List<OrderYearlyTotalRevenueResponse>> response = ApiResponse
                                                .success(
                                                        "Yearly order stats for merchant retrieved successfully",
                                                        responseList);

                                        return redisService
                                                .setWithExpirationReactive(cacheKey, toJson(response),
                                                        STATS_CACHE_TTL_SECONDS)
                                                .map(v -> {
                                                    logger.info("Cached yearly revenue for merchant key: {}", cacheKey);
                                                    return response;
                                                });
                                    })
                                    .onFailure()
                                    .invoke(e -> logger.error("Error fetching yearly order stats for merchantId={}",
                                            req.getMerchantId(), e)));
                });
    }
}