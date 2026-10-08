package com.sanedge.order.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sanedge.common.cache.CacheKeys;
import com.sanedge.common.cache.ReactiveCache;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.order.domain.requests.MonthTotalRevenue;
import com.sanedge.order.domain.response.OrderMonthlyTotalRevenueResponse;
import com.sanedge.order.domain.response.OrderYearlyTotalRevenueResponse;
import com.sanedge.order.repository.stats.OrderTotalRevenueRepository;
import com.sanedge.order.service.OrderTotalRevenueService;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OrderTotalRevenueServiceImpl implements OrderTotalRevenueService {
    private static final Logger logger = LoggerFactory.getLogger(OrderTotalRevenueServiceImpl.class);

    private final OrderTotalRevenueRepository orderTotalRevenueRepository;
    private final ReactiveCache reactiveCache;
    private final TracingMetrics tracingMetrics;

    @Inject
    public OrderTotalRevenueServiceImpl(OrderTotalRevenueRepository orderTotalRevenueRepository,
            ReactiveCache reactiveCache,
            TracingMetrics tracingMetrics) {
        this.orderTotalRevenueRepository = orderTotalRevenueRepository;
        this.reactiveCache = reactiveCache;
        this.tracingMetrics = tracingMetrics;
    }

    private static final TypeReference<ApiResponse<List<OrderMonthlyTotalRevenueResponse>>> MONTHLY_TYPE =
            new TypeReference<>() {
            };

    private static final TypeReference<ApiResponse<List<OrderYearlyTotalRevenueResponse>>> YEARLY_TYPE =
            new TypeReference<>() {
            };

    @Override
    public Uni<ApiResponse<List<OrderMonthlyTotalRevenueResponse>>> findMonthlyStats(MonthTotalRevenue req) {
        if (req.getYear() == null || req.getMonth() == null) {
            logger.error("Year or Month is null | req: {}", req);
            return Uni.createFrom().item(ApiResponse.error("Year and Month must not be null"));
        }

        if (req.getMonth() < 1 || req.getMonth() > 12) {
            return Uni.createFrom().item(ApiResponse.error("Month must be between 1 and 12"));
        }

        String cacheKey = CacheKeys.forStats("order", "revenue", "monthly", req.getYear(), req.getMonth());

        return reactiveCache.getOrCache(cacheKey, CacheKeys.TTL_LONG_SECONDS, MONTHLY_TYPE,
                () -> tracingMetrics.traceAndMeasure("findMonthlyStats", "find_monthly_revenue_stats",
                        Attributes.builder()
                                .put("year", req.getYear().toString())
                                .put("month", req.getMonth().toString())
                                .build(),
                        () -> orderTotalRevenueRepository.findMonthlyTotalRevenue(req.getYear(), req.getMonth())
                                .chain(rawData -> {
                                    List<OrderMonthlyTotalRevenueResponse> responseList = rawData.stream()
                                            .map(OrderMonthlyTotalRevenueResponse::from)
                                            .collect(Collectors.toList());

                                    return Uni.createFrom().item(ApiResponse.success(
                                            "Monthly order stats retrieved successfully", responseList));
                                })
                                .onFailure()
                                .invoke(e -> logger.error(
                                        "Error fetching monthly order stats | Year: {}, Month: {}", req.getYear(),
                                        req.getMonth(), e))));
    }

    @Override
    public Uni<ApiResponse<List<OrderYearlyTotalRevenueResponse>>> findYearlyStats(Integer year) {
        if (year == null) {
            logger.error("Year is null");
            return Uni.createFrom().item(ApiResponse.error("Year must not be null"));
        }

        String cacheKey = CacheKeys.forStats("order", "revenue", "yearly", year);

        return reactiveCache.getOrCache(cacheKey, CacheKeys.TTL_LONG_SECONDS, YEARLY_TYPE,
                () -> tracingMetrics.traceAndMeasure("findYearlyStats", "find_yearly_revenue_stats",
                        Attributes.builder().put("year", year.toString()).build(),
                        () -> orderTotalRevenueRepository.findYearlyTotalRevenue(year)
                                .chain(rawData -> {
                                    List<OrderYearlyTotalRevenueResponse> responseList = rawData.stream()
                                            .map(OrderYearlyTotalRevenueResponse::from)
                                            .collect(Collectors.toList());

                                    return Uni.createFrom().item(ApiResponse.success(
                                            "Yearly order stats retrieved successfully", responseList));
                                })
                                .onFailure().invoke(e -> logger
                                        .error("Error fetching yearly order stats | Year: {}", year, e))));
    }
}
