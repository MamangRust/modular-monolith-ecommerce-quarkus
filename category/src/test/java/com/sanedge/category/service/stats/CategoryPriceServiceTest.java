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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.category.domain.response.CategoriesMonthPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearPriceResponse;
import com.sanedge.category.entity.CategoriesMonthPrice;
import com.sanedge.category.entity.CategoriesYearPrice;
import com.sanedge.category.repository.stats.CategoryPriceRepository;
import com.sanedge.category.service.impl.CategoryPriceServiceImpl;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class CategoryPriceServiceTest {

    @Mock
    private CategoryPriceRepository categoryPriceRepository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private CategoryPriceServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new CategoryPriceServiceImpl(categoryPriceRepository, redisService, objectMapper, tracingMetrics);
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

    private CategoriesMonthPrice createMonthPrice() {
        return new CategoriesMonthPrice("Jan", 1L, "Cat-1", 10L, 50L, 1000L);
    }

    private CategoriesYearPrice createYearPrice() {
        return new CategoriesYearPrice("2024", 1L, "Cat-1", 100L, 500L, 100000L, 20L);
    }

    @Test
    void findMonthPrice_cacheMiss_returnsFromDb() {
        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(categoryPriceRepository.findMonthlyCategoryStats(anyInt()))
                .thenReturn(Uni.createFrom().item(List.of(createMonthPrice())));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponse<List<CategoriesMonthPriceResponse>> result = service.findMonthPrice(2024).await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.data()).hasSize(1);
    }

    @Test
    void findMonthPrice_cacheHit_returnsCached() {
        CategoriesMonthPriceResponse r = CategoriesMonthPriceResponse.from(createMonthPrice());
        ApiResponse<List<CategoriesMonthPriceResponse>> cached = new ApiResponse<>(
                "success", "Monthly category price stats retrieved successfully", List.of(r));
        String json = toJson(cached);
        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().item(json));

        ApiResponse<List<CategoriesMonthPriceResponse>> result = service.findMonthPrice(2024).await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.data()).hasSize(1);
    }

    @Test
    void findYearPrice_cacheMiss_returnsFromDb() {
        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(categoryPriceRepository.findYearlyCategoryStats(anyInt()))
                .thenReturn(Uni.createFrom().item(List.of(createYearPrice())));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponse<List<CategoriesYearPriceResponse>> result = service.findYearPrice(2024).await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.data()).hasSize(1);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize", e);
        }
    }
}
