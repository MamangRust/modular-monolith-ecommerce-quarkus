package com.sanedge.category.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.common.config.RedisService;
import com.sanedge.category.domain.requests.MonthPriceIdRequest;
import com.sanedge.category.domain.requests.YearPriceIdRequest;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.category.domain.response.CategoriesMonthPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearPriceResponse;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.category.repository.statsbyid.CategoryPriceByIdRepository;
import com.sanedge.category.service.stats.CategoryPriceByIdService;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CategoryPriceByIdServiceImpl implements CategoryPriceByIdService {
        private static final Logger logger = LoggerFactory.getLogger(CategoryPriceByIdServiceImpl.class);

        private final CategoryPriceByIdRepository categoryPriceByIdRepository;
        private final RedisService redisService;
        private final ObjectMapper objectMapper;
        private final TracingMetrics tracingMetrics;

        private static final long STATS_CACHE_TTL_SECONDS = 300;

        @Inject
        public CategoryPriceByIdServiceImpl(CategoryPriceByIdRepository categoryPriceByIdRepository,
                        RedisService redisService,
                        ObjectMapper objectMapper,
                        TracingMetrics tracingMetrics) {
                this.categoryPriceByIdRepository = categoryPriceByIdRepository;
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
        public Uni<ApiResponse<List<CategoriesMonthPriceResponse>>> findMonthPriceById(MonthPriceIdRequest req) {
                if (req.getCategoryId() == null || req.getYear() == null) {
                        logger.error("CategoryId or Year is null | req: {}", req);
                        throw new ResourceNotFoundException("CategoryId and Year must not be null");
                }

                String cacheKey = String.format("categories:stats:month-price-by-id:%d:%d", req.getCategoryId(),
                                req.getYear());

                Attributes attrs = Attributes.builder()
                                .put("stats.category.id", req.getCategoryId())
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
                                        return tracingMetrics.traceAndMeasure("findMonthPriceById",
                                                        "find_month_price_by_id", attrs,
                                                        () -> categoryPriceByIdRepository.findMonthlyCategoryStatsById(
                                                                        req.getCategoryId(), req.getYear())
                                                                        .chain(results -> {
                                                                                List<CategoriesMonthPriceResponse> responseList = results
                                                                                                .stream()
                                                                                                .map(CategoriesMonthPriceResponse::from)
                                                                                                .collect(Collectors
                                                                                                                .toList());

                                                                                ApiResponse<List<CategoriesMonthPriceResponse>> response = ApiResponse
                                                                                                .success(
                                                                                                                "Monthly category price stats retrieved successfully",
                                                                                                                responseList);

                                                                                return redisService
                                                                                                .setWithExpirationReactive(
                                                                                                                cacheKey,
                                                                                                                toJson(response),
                                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                                .map(v -> {
                                                                                                        logger.info("Cached monthly stats by category id for key: {}",
                                                                                                                        cacheKey);
                                                                                                        logger.info("Successfully fetched {} monthly stats for categoryId {}",
                                                                                                                        responseList.size(),
                                                                                                                        req.getCategoryId());
                                                                                                        return response;
                                                                                                });
                                                                        }));
                                });
        }

        @Override
        public Uni<ApiResponse<List<CategoriesYearPriceResponse>>> findYearPriceById(YearPriceIdRequest req) {
                if (req.getCategoryId() == null || req.getYear() == null) {
                        logger.error("CategoryId or Year is null | req: {}", req);
                        throw new ResourceNotFoundException("CategoryId and Year must not be null");
                }

                String cacheKey = String.format("categories:stats:year-price-by-id:%d:%d", req.getCategoryId(),
                                req.getYear());

                Attributes attrs = Attributes.builder()
                                .put("stats.category.id", req.getCategoryId())
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
                                        return tracingMetrics.traceAndMeasure("findYearPriceById",
                                                        "find_year_price_by_id", attrs,
                                                        () -> categoryPriceByIdRepository.findYearlyCategoryStatsById(
                                                                        req.getCategoryId(), req.getYear())
                                                                        .chain(results -> {
                                                                                List<CategoriesYearPriceResponse> responseList = results
                                                                                                .stream()
                                                                                                .map(CategoriesYearPriceResponse::from)
                                                                                                .collect(Collectors
                                                                                                                .toList());

                                                                                ApiResponse<List<CategoriesYearPriceResponse>> response = ApiResponse
                                                                                                .success(
                                                                                                                "Yearly category price stats retrieved successfully",
                                                                                                                responseList);

                                                                                return redisService
                                                                                                .setWithExpirationReactive(
                                                                                                                cacheKey,
                                                                                                                toJson(response),
                                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                                .map(v -> {
                                                                                                        logger.info("Cached yearly stats by category id for key: {}",
                                                                                                                        cacheKey);
                                                                                                        logger.info("Successfully fetched {} yearly stats for categoryId {}",
                                                                                                                        responseList.size(),
                                                                                                                        req.getCategoryId());
                                                                                                        return response;
                                                                                                });
                                                                        }));
                                });
        }
}