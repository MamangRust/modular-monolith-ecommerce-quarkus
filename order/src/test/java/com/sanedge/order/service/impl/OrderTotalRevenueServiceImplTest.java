package com.sanedge.order.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.order.domain.requests.MonthTotalRevenue;
import com.sanedge.order.domain.response.OrderMonthlyTotalRevenueResponse;
import com.sanedge.order.domain.response.OrderYearlyTotalRevenueResponse;
import com.sanedge.order.entity.OrderMonthlyTotalRevenue;
import com.sanedge.order.entity.OrderYearlyTotalRevenue;
import com.sanedge.order.repository.stats.OrderTotalRevenueRepository;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class OrderTotalRevenueServiceImplTest {

    @Mock
    private OrderTotalRevenueRepository orderTotalRevenueRepository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private OrderTotalRevenueServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new OrderTotalRevenueServiceImpl(orderTotalRevenueRepository, redisService, objectMapper,
                tracingMetrics);
        lenient()
                .when(tracingMetrics.traceAndMeasure(anyString(), anyString(), any(Attributes.class),
                        any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(3);
                    return supplier.get();
                });
    }

    private OrderMonthlyTotalRevenue createMonthly() {
        return new OrderMonthlyTotalRevenue("2024", "January", 1000L);
    }

    private OrderYearlyTotalRevenue createYearly() {
        return new OrderYearlyTotalRevenue("2024", 12000L);
    }

    // ========== findMonthlyStats Tests ==========

    @Test
    void findMonthlyStats_nullYear_returnsError() {
        MonthTotalRevenue req = new MonthTotalRevenue();
        req.setYear(null);
        req.setMonth(1);

        ApiResponse<List<OrderMonthlyTotalRevenueResponse>> result = service.findMonthlyStats(req).await().indefinitely();

        assertThat(result.status()).isEqualTo("error");
    }

    @Test
    void findMonthlyStats_invalidMonth_returnsError() {
        MonthTotalRevenue req = new MonthTotalRevenue();
        req.setYear(2024);
        req.setMonth(13);

        ApiResponse<List<OrderMonthlyTotalRevenueResponse>> result = service.findMonthlyStats(req).await().indefinitely();

        assertThat(result.status()).isEqualTo("error");
    }

    @Test
    void findMonthlyStats_cacheMiss_returnsFromDb() {
        MonthTotalRevenue req = new MonthTotalRevenue();
        req.setYear(2024);
        req.setMonth(1);

        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(orderTotalRevenueRepository.findMonthlyTotalRevenue(anyInt(), anyInt()))
                .thenReturn(Uni.createFrom().item(List.of(createMonthly())));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponse<List<OrderMonthlyTotalRevenueResponse>> result = service.findMonthlyStats(req).await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.data()).hasSize(1);
    }

    // ========== findYearlyStats Tests ==========

    @Test
    void findYearlyStats_nullYear_returnsError() {
        ApiResponse<List<OrderYearlyTotalRevenueResponse>> result = service.findYearlyStats(null).await().indefinitely();

        assertThat(result.status()).isEqualTo("error");
    }

    @Test
    void findYearlyStats_cacheMiss_returnsFromDb() {
        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(orderTotalRevenueRepository.findYearlyTotalRevenue(anyInt()))
                .thenReturn(Uni.createFrom().item(List.of(createYearly())));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponse<List<OrderYearlyTotalRevenueResponse>> result = service.findYearlyStats(2024).await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.data()).hasSize(1);
    }
}
