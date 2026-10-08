package com.sanedge.category.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.common.config.RedisService;
import com.sanedge.category.domain.requests.MonthPriceMerchantRequest;
import com.sanedge.category.domain.requests.YearPriceMerchantRequest;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.category.domain.response.CategoriesMonthPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearPriceResponse;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.category.repository.statsbymerchant.CategoryPriceByMerchantRepository;
import com.sanedge.category.service.stats.CategoryPriceByMerchantService;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CategoryPriceByMerchantServiceImpl implements CategoryPriceByMerchantService {
        private static final Logger logger = LoggerFactory.getLogger(CategoryPriceByMerchantServiceImpl.class);

        private final CategoryPriceByMerchantRepository categoryPriceByMerchantRepository;
        private final RedisService redisService;
        private final ObjectMapper objectMapper;
        private final TracingMetrics tracingMetrics;

        private static final long STATS_CACHE_TTL_SECONDS = 300;

        @Inject
        public CategoryPriceByMerchantServiceImpl(CategoryPriceByMerchantRepository categoryPriceByMerchantRepository,
                        RedisService redisService,
                        ObjectMapper objectMapper,
                        TracingMetrics tracingMetrics) {
                this.categoryPriceByMerchantRepository = categoryPriceByMerchantRepository;
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
                        logger.error("Error deserializing JSON to object with TypeReference", e);
                        throw new RuntimeException("Failed to deserialize JSON", e);
                }
        }

        @Override
        public Uni<ApiResponse<List<CategoriesMonthPriceResponse>>> findMonthPriceByMerchant(
                        MonthPriceMerchantRequest req) {
                if (req.getMerchantId() == null || req.getYear() == null) {
                        logger.error("MerchantId or Year is null | req: {}", req);
                        throw new ResourceNotFoundException("MerchantId and Year must not be null");
                }

                String cacheKey = String.format("categories:stats:month-price-by-merchant:%d:%d", req.getMerchantId(),
                                req.getYear());

                Attributes attrs = Attributes.builder()
                                .put("stats.merchant.id", req.getMerchantId())
                                .put("stats.year", req.getYear())
                                .build();

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponse<List<CategoriesMonthPriceResponse>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponse<List<CategoriesMonthPriceResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        return tracingMetrics.traceAndMeasure("findMonthPriceByMerchant",
                                                        "find_month_price_by_merchant", attrs,
                                                        () -> categoryPriceByMerchantRepository
                                                                        .findMonthlyCategoryStatsByMerchant(
                                                                                        req.getMerchantId(),
                                                                                        req.getYear())
                                                                        .chain(results -> {
                                                                                List<CategoriesMonthPriceResponse> responseList = results
                                                                                                .stream()
                                                                                                .map(CategoriesMonthPriceResponse::from)
                                                                                                .collect(Collectors
                                                                                                                .toList());

                                                                                ApiResponse<List<CategoriesMonthPriceResponse>> response = ApiResponse
                                                                                                .success(
                                                                                                                "Monthly price by merchant retrieved successfully",
                                                                                                                responseList);

                                                                                return redisService
                                                                                                .setWithExpirationReactive(
                                                                                                                cacheKey,
                                                                                                                toJson(response),
                                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                                .map(v -> {
                                                                                                        logger.info("Cached monthly stats by merchant for key: {}",
                                                                                                                        cacheKey);
                                                                                                        logger.info("Successfully fetched {} monthly stats for merchant {}",
                                                                                                                        responseList.size(),
                                                                                                                        req.getMerchantId());
                                                                                                        return response;
                                                                                                });
                                                                        }));
                                });
        }

        @Override
        public Uni<ApiResponse<List<CategoriesYearPriceResponse>>> findYearPriceByMerchant(
                        YearPriceMerchantRequest req) {
                if (req.getMerchantId() == null || req.getYear() == null) {
                        logger.error("MerchantId or Year is null | req: {}", req);
                        throw new ResourceNotFoundException("MerchantId and Year must not be null");
                }

                String cacheKey = String.format("categories:stats:year-price-by-merchant:%d:%d", req.getMerchantId(),
                                req.getYear());

                Attributes attrs = Attributes.builder()
                                .put("stats.merchant.id", req.getMerchantId())
                                .put("stats.year", req.getYear())
                                .build();

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponse<List<CategoriesYearPriceResponse>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponse<List<CategoriesYearPriceResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        return tracingMetrics.traceAndMeasure("findYearPriceByMerchant",
                                                        "find_year_price_by_merchant", attrs,
                                                        () -> categoryPriceByMerchantRepository
                                                                        .findYearlyCategoryStatsByMerchant(
                                                                                        req.getMerchantId(),
                                                                                        req.getYear())
                                                                        .chain(results -> {
                                                                                List<CategoriesYearPriceResponse> responseList = results
                                                                                                .stream()
                                                                                                .map(CategoriesYearPriceResponse::from)
                                                                                                .collect(Collectors
                                                                                                                .toList());

                                                                                ApiResponse<List<CategoriesYearPriceResponse>> response = ApiResponse
                                                                                                .success(
                                                                                                                "Yearly price by merchant retrieved successfully",
                                                                                                                responseList);

                                                                                return redisService
                                                                                                .setWithExpirationReactive(
                                                                                                                cacheKey,
                                                                                                                toJson(response),
                                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                                .map(v -> {
                                                                                                        logger.info("Cached yearly stats by merchant for key: {}",
                                                                                                                        cacheKey);
                                                                                                        logger.info("Successfully fetched {} yearly stats for merchant {}",
                                                                                                                        responseList.size(),
                                                                                                                        req.getMerchantId());
                                                                                                        return response;
                                                                                                });
                                                                        }));
                                });
        }
}