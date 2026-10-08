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
import com.sanedge.order.domain.requests.MonthTotalRevenueMerchantRequest;
import com.sanedge.order.domain.requests.YearTotalRevenueMerchantRequest;
import com.sanedge.order.domain.response.OrderMonthlyTotalRevenueResponse;
import com.sanedge.order.domain.response.OrderYearlyTotalRevenueResponse;
import com.sanedge.order.entity.OrderMonthlyTotalRevenue;
import com.sanedge.order.entity.OrderYearlyTotalRevenue;
import com.sanedge.order.repository.statsbymerchant.OrderTotalRevenueByMerchantRepository;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class OrderTotalRevenueByMerchantServiceImplTest {

        @Mock
        private OrderTotalRevenueByMerchantRepository repository;

        @Mock
        private RedisService redisService;

        @Mock
        private TracingMetrics tracingMetrics;

        private OrderTotalRevenueByMerchantServiceImpl service;
        private final ObjectMapper objectMapper = new ObjectMapper();

        @BeforeEach
        void setUp() {
                service = new OrderTotalRevenueByMerchantServiceImpl(repository, redisService, objectMapper,
                                tracingMetrics);
                lenient().doAnswer(invocation -> {
                        Supplier<Uni<?>> supplier = invocation.getArgument(3);
                        return supplier.get();
                }).when(tracingMetrics)
                                .traceAndMeasure(
                                                anyString(),
                                                anyString(),
                                                any(Attributes.class),
                                                any());
        }

        private OrderMonthlyTotalRevenue createMonthly() {
                return new OrderMonthlyTotalRevenue("2024", "January", 1000L);
        }

        private OrderYearlyTotalRevenue createYearly() {
                return new OrderYearlyTotalRevenue("2024", 12000L);
        }

        @Test
        void findMonthlyStatsByMerchant_nullMerchantId_returnsError() {
                MonthTotalRevenueMerchantRequest req = new MonthTotalRevenueMerchantRequest();
                req.setMerchantId(null);
                req.setYear(2024);
                req.setMonth(1);

                ApiResponse<List<OrderMonthlyTotalRevenueResponse>> result = service.findMonthlyStatsByMerchant(req)
                                .await()
                                .indefinitely();

                assertThat(result.status()).isEqualTo("error");
        }

        @Test
        void findMonthlyStatsByMerchant_invalidMonth_returnsError() {
                MonthTotalRevenueMerchantRequest req = new MonthTotalRevenueMerchantRequest();
                req.setMerchantId(1);
                req.setYear(2024);
                req.setMonth(13);

                ApiResponse<List<OrderMonthlyTotalRevenueResponse>> result = service.findMonthlyStatsByMerchant(req)
                                .await()
                                .indefinitely();

                assertThat(result.status()).isEqualTo("error");
        }

        @Test
        void findMonthlyStatsByMerchant_cacheMiss_returnsFromDb() {
                MonthTotalRevenueMerchantRequest req = new MonthTotalRevenueMerchantRequest();
                req.setMerchantId(1);
                req.setYear(2024);
                req.setMonth(1);

                when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
                when(repository.findMonthlyTotalRevenueByMerchant(any()))
                                .thenReturn(Uni.createFrom().item(List.of(createMonthly())));
                when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                                .thenReturn(Uni.createFrom().voidItem());

                ApiResponse<List<OrderMonthlyTotalRevenueResponse>> result = service.findMonthlyStatsByMerchant(req)
                                .await()
                                .indefinitely();

                assertThat(result.status()).isEqualTo("success");
                assertThat(result.data()).hasSize(1);
        }

        @Test
        void findYearlyStatsByMerchant_nullMerchantId_returnsError() {
                YearTotalRevenueMerchantRequest req = new YearTotalRevenueMerchantRequest();
                req.setMerchantId(null);
                req.setYear(2024);

                ApiResponse<List<OrderYearlyTotalRevenueResponse>> result = service.findYearlyStatsByMerchant(req)
                                .await()
                                .indefinitely();

                assertThat(result.status()).isEqualTo("error");
        }

        @Test
        void findYearlyStatsByMerchant_cacheMiss_returnsFromDb() {
                YearTotalRevenueMerchantRequest req = new YearTotalRevenueMerchantRequest();
                req.setMerchantId(1);
                req.setYear(2024);

                when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
                when(repository.findYearlyTotalRevenueByMerchant(anyLong(), anyInt()))
                                .thenReturn(Uni.createFrom().item(List.of(createYearly())));
                when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                                .thenReturn(Uni.createFrom().voidItem());

                ApiResponse<List<OrderYearlyTotalRevenueResponse>> result = service.findYearlyStatsByMerchant(req)
                                .await()
                                .indefinitely();

                assertThat(result.status()).isEqualTo("success");
                assertThat(result.data()).hasSize(1);
        }
}
