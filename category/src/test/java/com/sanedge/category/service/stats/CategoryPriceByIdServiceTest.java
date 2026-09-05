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
import com.sanedge.category.domain.requests.MonthPriceIdRequest;
import com.sanedge.category.domain.requests.YearPriceIdRequest;
import com.sanedge.category.domain.response.CategoriesMonthPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearPriceResponse;
import com.sanedge.category.entity.CategoriesMonthPrice;
import com.sanedge.category.entity.CategoriesYearPrice;
import com.sanedge.category.repository.statsbyid.CategoryPriceByIdRepository;
import com.sanedge.category.service.impl.CategoryPriceByIdServiceImpl;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class CategoryPriceByIdServiceTest {

    @Mock
    private CategoryPriceByIdRepository categoryPriceByIdRepository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private CategoryPriceByIdServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new CategoryPriceByIdServiceImpl(categoryPriceByIdRepository, redisService, objectMapper,
                tracingMetrics);
        lenient()
                .when(tracingMetrics.traceAndMeasure(anyString(), anyString(), any(Attributes.class),
                        any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(3);
                    return supplier.get();
                });
    }

    private CategoriesMonthPrice createMonthPrice() {
        return new CategoriesMonthPrice("Jan", 1L, "Cat-1", 10L, 50L, 1000L);
    }

    private CategoriesYearPrice createYearPrice() {
        return new CategoriesYearPrice("2024", 1L, "Cat-1", 100L, 500L, 100000L, 20L);
    }

    // ========== findMonthPriceById Tests ==========

    @Test
    void findMonthPriceById_nullCategoryId_throwsResourceNotFoundException() {
        MonthPriceIdRequest req = new MonthPriceIdRequest();
        req.setCategoryId(null);
        req.setYear(2024);

        try {
            service.findMonthPriceById(req).await().indefinitely();
            Assertions.fail("Expected ResourceNotFoundException");
        } catch (ResourceNotFoundException e) {
            assertThat(e.getMessage()).contains("CategoryId and Year must not be null");
        }
    }

    @Test
    void findMonthPriceById_cacheMiss_returnsFromDb() {
        MonthPriceIdRequest req = new MonthPriceIdRequest();
        req.setCategoryId(1);
        req.setYear(2024);

        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(categoryPriceByIdRepository.findMonthlyCategoryStatsById(anyInt(), anyInt()))
                .thenReturn(Uni.createFrom().item(List.of(createMonthPrice())));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponse<List<CategoriesMonthPriceResponse>> result = service.findMonthPriceById(req).await()
                .indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.data()).hasSize(1);
    }

    // ========== findYearPriceById Tests ==========

    @Test
    void findYearPriceById_cacheMiss_returnsFromDb() {
        YearPriceIdRequest req = new YearPriceIdRequest();
        req.setCategoryId(1);
        req.setYear(2024);

        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(categoryPriceByIdRepository.findYearlyCategoryStatsById(anyInt(), anyInt()))
                .thenReturn(Uni.createFrom().item(List.of(createYearPrice())));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponse<List<CategoriesYearPriceResponse>> result = service.findYearPriceById(req).await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.data()).hasSize(1);
    }
}
