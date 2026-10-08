package com.sanedge.category.service.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.category.domain.requests.MonthTotalPriceRequest;
import com.sanedge.category.domain.response.CategoriesMonthlyTotalPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearlyTotalPriceResponse;
import com.sanedge.category.entity.CategoriesMonthlyTotalPrice;
import com.sanedge.category.entity.CategoriesYearlyTotalPrice;
import com.sanedge.category.repository.stats.CategoryTotalPriceRepository;
import com.sanedge.category.service.impl.CategoryTotalPriceServiceImpl;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class CategoryTotalPriceServiceTest {

    @Mock
    private CategoryTotalPriceRepository categoryTotalPriceRepository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private CategoryTotalPriceServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new CategoryTotalPriceServiceImpl(categoryTotalPriceRepository, redisService, objectMapper,
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

    private CategoriesMonthlyTotalPrice createMonthlyTotal() {
        return new CategoriesMonthlyTotalPrice("2024", "January", 1000L);
    }

    private CategoriesYearlyTotalPrice createYearlyTotal() {
        return new CategoriesYearlyTotalPrice("2024", 12000L);
    }

    @Test
    void findMonthlyTotalPrice_nullYear_throwsResourceNotFoundException() {
        MonthTotalPriceRequest req = new MonthTotalPriceRequest();
        req.setYear(null);
        req.setMonth(1);

        try {
            service.findMonthlyTotalPrice(req).await().indefinitely();
            Assertions.fail("Expected ResourceNotFoundException");
        } catch (ResourceNotFoundException e) {
            assertThat(e.getMessage()).contains("Year and Month must not be null");
        }
    }

    @Test
    void findMonthlyTotalPrice_nullMonth_throwsResourceNotFoundException() {
        MonthTotalPriceRequest req = new MonthTotalPriceRequest();
        req.setYear(2024);
        req.setMonth(null);

        try {
            service.findMonthlyTotalPrice(req).await().indefinitely();
            Assertions.fail("Expected ResourceNotFoundException");
        } catch (ResourceNotFoundException e) {
            assertThat(e.getMessage()).contains("Year and Month must not be null");
        }
    }

    @Test
    void findMonthlyTotalPrice_cacheMiss_returnsFromDb() {
        MonthTotalPriceRequest req = new MonthTotalPriceRequest();
        req.setYear(2024);
        req.setMonth(1);

        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(categoryTotalPriceRepository.findMonthlyTotalPrice(anyInt(), anyInt()))
                .thenReturn(Uni.createFrom().item(List.of(createMonthlyTotal())));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponse<List<CategoriesMonthlyTotalPriceResponse>> result = service.findMonthlyTotalPrice(req).await()
                .indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.data()).hasSize(1);
    }

    @Test
    void findYearlyTotalPrice_cacheMiss_returnsFromDb() {
        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(categoryTotalPriceRepository.findYearlyTotalPrice(anyInt()))
                .thenReturn(Uni.createFrom().item(List.of(createYearlyTotal())));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponse<List<CategoriesYearlyTotalPriceResponse>> result = service.findYearlyTotalPrice(2024).await()
                .indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.data()).hasSize(1);
    }
}
