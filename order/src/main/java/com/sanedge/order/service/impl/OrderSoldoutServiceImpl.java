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
import com.sanedge.order.domain.response.OrderMonthlyResponse;
import com.sanedge.order.domain.response.OrderYearlyResponse;
import com.sanedge.order.repository.stats.OrderSoldOutRepository;
import com.sanedge.order.service.OrderSoldoutService;

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
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OrderSoldoutServiceImpl implements OrderSoldoutService {
    private static final Logger logger = LoggerFactory.getLogger(OrderSoldoutServiceImpl.class);

    OrderSoldOutRepository orderSoldOutRepository;
    OpenTelemetry openTelemetry;
    RedisService redisService;
    ObjectMapper objectMapper;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    private static final long STATS_CACHE_TTL_SECONDS = 600;

    @Inject
    public OrderSoldoutServiceImpl(OrderSoldOutRepository orderSoldOutRepository,
                                  OpenTelemetry openTelemetry,
                                  RedisService redisService,
                                  ObjectMapper objectMapper) {
        this.orderSoldOutRepository = orderSoldOutRepository;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.objectMapper = objectMapper;
        this.tracer = openTelemetry.getTracer("order-soldout-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("order-soldout-service");

        this.requestsTotal = meter.counterBuilder("requests_total")
                .setDescription("Total number of requests")
                .build();
        this.requestDurationSeconds = meter.histogramBuilder("request_duration_seconds")
                .setDescription("Request duration in seconds")
                .setUnit("s")
                .build();
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
    public Uni<ApiResponse<List<OrderMonthlyResponse>>> findMonthlyOrders(Integer year, Integer month) {
        if (year == null || month == null) {
            return Uni.createFrom().item(ApiResponse.error("Year and Month must not be null"));
        }
        if (month < 1 || month > 12) {
            return Uni.createFrom().item(ApiResponse.error("Month must be between 1 and 12"));
        }

        String cacheKey = String.format("order:soldout:monthly:%d:%d", year, month);

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponse<List<OrderMonthlyResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<OrderMonthlyResponse>>>() {});
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findMonthlyOrders")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "order-soldout-service")
                            .setAttribute("operation", "find_monthly_orders")
                            .setAttribute("year", year.toString())
                            .setAttribute("month", month.toString())
                            .startSpan();

                    return orderSoldOutRepository.findMonthlyOrders(year, month)
                            .chain(rawData -> {
                                List<OrderMonthlyResponse> responseList = rawData.stream()
                                        .map(OrderMonthlyResponse::from)
                                        .collect(Collectors.toList());

                                ApiResponse<List<OrderMonthlyResponse>> response = ApiResponse.success(
                                        "Monthly order data retrieved successfully", responseList);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), STATS_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            logger.info("Cached monthly stats for key: {}", cacheKey);
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_monthly_orders",
                                                    AttributeKey.stringKey("status"), "success"));
                                            return response;
                                        });
                            })
                            .onFailure().invoke(e -> {
                                logger.error("Error fetching monthly orders for year={}, month={}", year, month, e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_monthly_orders",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_monthly_orders"));
                            });
                });
    }

    @Override
    public Uni<ApiResponse<List<OrderYearlyResponse>>> findYearlyOrders(Integer year) {
        if (year == null) {
            return Uni.createFrom().item(ApiResponse.error("Year must not be null"));
        }

        String cacheKey = "order:soldout:yearly:" + year;

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponse<List<OrderYearlyResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<OrderYearlyResponse>>>() {});
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findYearlyOrders")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "order-soldout-service")
                            .setAttribute("operation", "find_yearly_orders")
                            .setAttribute("year", year.toString())
                            .startSpan();

                    return orderSoldOutRepository.findYearlyOrders(year)
                            .chain(rawData -> {
                                List<OrderYearlyResponse> responseList = rawData.stream()
                                        .map(OrderYearlyResponse::from)
                                        .collect(Collectors.toList());

                                ApiResponse<List<OrderYearlyResponse>> response = ApiResponse.success(
                                        "Yearly order data retrieved successfully", responseList);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), STATS_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            logger.info("Cached yearly stats for key: {}", cacheKey);
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_yearly_orders",
                                                    AttributeKey.stringKey("status"), "success"));
                                            return response;
                                        });
                            })
                            .onFailure().invoke(e -> {
                                logger.error("Error fetching yearly orders for year={}", year, e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_yearly_orders",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_yearly_orders"));
                            });
                });
    }
}
