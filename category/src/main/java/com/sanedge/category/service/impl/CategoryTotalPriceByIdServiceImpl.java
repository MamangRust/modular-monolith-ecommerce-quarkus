package com.sanedge.category.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.common.config.RedisService;
import com.sanedge.category.domain.requests.FindMonthlyCategoryTotalPriceByIdRequest;
import com.sanedge.category.domain.requests.YearTotalPriceIdRequest;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.category.domain.response.CategoriesMonthlyTotalPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearlyTotalPriceResponse;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.category.repository.statsbyid.CategoryTotalPriceByIdRepository;
import com.sanedge.category.service.stats.CategoryTotalPriceByIdService;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CategoryTotalPriceByIdServiceImpl implements CategoryTotalPriceByIdService {
        private static final Logger logger = LoggerFactory.getLogger(CategoryTotalPriceByIdServiceImpl.class);

        private final CategoryTotalPriceByIdRepository categoryTotalPriceByIdRepository;
        private final RedisService redisService;
        private final ObjectMapper objectMapper;
        private final TracingMetrics tracingMetrics;

        private static final long STATS_CACHE_TTL_SECONDS = 300;

        @Inject
        public CategoryTotalPriceByIdServiceImpl(CategoryTotalPriceByIdRepository categoryTotalPriceByIdRepository,
                        RedisService redisService,
                        ObjectMapper objectMapper,
                        TracingMetrics tracingMetrics) {
                this.categoryTotalPriceByIdRepository = categoryTotalPriceByIdRepository;
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
        public Uni<ApiResponse<List<CategoriesMonthlyTotalPriceResponse>>> findMonthlyTotalPriceById(
                        FindMonthlyCategoryTotalPriceByIdRequest req) {
                if (req.getCategoryId() == null || req.getYear() == null || req.getMonth() == null) {
                        logger.error("CategoryId, Year or Month is null | req: {}", req);
                        throw new ResourceNotFoundException("CategoryId, Year and Month must not be null");
                }

                String cacheKey = String.format("categories:stats:monthly-total-price-by-id:%d:%d:%d",
                                req.getCategoryId(), req.getYear(), req.getMonth());

                Attributes attrs = Attributes.builder()
                                .put("stats.category.id", req.getCategoryId())
                                .put("stats.year", req.getYear())
                                .put("stats.month", req.getMonth())
                                .build();

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponse<List<CategoriesMonthlyTotalPriceResponse>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponse<List<CategoriesMonthlyTotalPriceResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        return tracingMetrics.traceAndMeasure("findMonthlyTotalPriceById",
                                                        "find_monthly_total_price_by_id", attrs,
                                                        () -> categoryTotalPriceByIdRepository
                                                                        .findMonthlyTotalPriceByCategory(req)
                                                                        .chain(results -> {
                                                                                List<CategoriesMonthlyTotalPriceResponse> responseList = results
                                                                                                .stream()
                                                                                                .map(CategoriesMonthlyTotalPriceResponse::from)
                                                                                                .collect(Collectors
                                                                                                                .toList());

                                                                                ApiResponse<List<CategoriesMonthlyTotalPriceResponse>> response = ApiResponse
                                                                                                .success(
                                                                                                                "Monthly total price by category retrieved successfully",
                                                                                                                responseList);

                                                                                return redisService
                                                                                                .setWithExpirationReactive(
                                                                                                                cacheKey,
                                                                                                                toJson(response),
                                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                                .map(v -> {
                                                                                                        logger.info("Cached monthly total price stats by id for key: {}",
                                                                                                                        cacheKey);
                                                                                                        logger.info("Successfully fetched {} monthly total price stats for categoryId {}",
                                                                                                                        responseList.size(),
                                                                                                                        req.getCategoryId());
                                                                                                        return response;
                                                                                                });
                                                                        }));
                                });
        }

        @Override
        public Uni<ApiResponse<List<CategoriesYearlyTotalPriceResponse>>> findYearlyTotalPriceById(
                        YearTotalPriceIdRequest req) {
                if (req.getCategoryId() == null || req.getYear() == null) {
                        logger.error("CategoryId or Year is null | req: {}", req);
                        throw new ResourceNotFoundException("CategoryId and Year must not be null");
                }

                String cacheKey = String.format("categories:stats:yearly-total-price-by-id:%d:%d", req.getCategoryId(),
                                req.getYear());

                Attributes attrs = Attributes.builder()
                                .put("stats.category.id", req.getCategoryId())
                                .put("stats.year", req.getYear())
                                .build();

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponse<List<CategoriesYearlyTotalPriceResponse>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponse<List<CategoriesYearlyTotalPriceResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        return tracingMetrics.traceAndMeasure("findYearlyTotalPriceById",
                                                        "find_yearly_total_price_by_id", attrs,
                                                        () -> categoryTotalPriceByIdRepository
                                                                        .findYearlyTotalPriceByCategory(
                                                                                        req.getCategoryId(),
                                                                                        req.getYear())
                                                                        .chain(results -> {
                                                                                List<CategoriesYearlyTotalPriceResponse> responseList = results
                                                                                                .stream()
                                                                                                .map(CategoriesYearlyTotalPriceResponse::from)
                                                                                                .collect(Collectors
                                                                                                                .toList());

                                                                                ApiResponse<List<CategoriesYearlyTotalPriceResponse>> response = ApiResponse
                                                                                                .success(
                                                                                                                "Yearly total price by category retrieved successfully",
                                                                                                                responseList);

                                                                                return redisService
                                                                                                .setWithExpirationReactive(
                                                                                                                cacheKey,
                                                                                                                toJson(response),
                                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                                .map(v -> {
                                                                                                        logger.info("Cached yearly total price stats by id for key: {}",
                                                                                                                        cacheKey);
                                                                                                        logger.info("Successfully fetched {} yearly total price stats for categoryId {}",
                                                                                                                        responseList.size(),
                                                                                                                        req.getCategoryId());
                                                                                                        return response;
                                                                                                });
                                                                        }));
                                });
        }
}