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
import com.sanedge.category.domain.requests.FindMonthlyCategoryTotalPriceByMerchantRequest;
import com.sanedge.category.domain.requests.YearTotalPriceMerchantRequest;
import com.sanedge.category.domain.response.CategoriesMonthlyTotalPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearlyTotalPriceResponse;
import com.sanedge.category.entity.CategoriesMonthlyTotalPrice;
import com.sanedge.category.entity.CategoriesYearlyTotalPrice;
import com.sanedge.category.repository.statsbymerchant.CategoryTotalPriceByMerchantRepository;
import com.sanedge.category.service.impl.CategoryTotalPriceByMerchantServiceImpl;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class CategoryTotalPriceByMerchantServiceTest {

    @Mock
    private CategoryTotalPriceByMerchantRepository categoryTotalPriceByMerchantRepository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private CategoryTotalPriceByMerchantServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new CategoryTotalPriceByMerchantServiceImpl(categoryTotalPriceByMerchantRepository, redisService,
                objectMapper, tracingMetrics);
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
    void findMonthlyTotalPriceByMerchant_nullMerchantId_throwsResourceNotFoundException() {
        FindMonthlyCategoryTotalPriceByMerchantRequest req = new FindMonthlyCategoryTotalPriceByMerchantRequest();
        req.setMerchantId(null);
        req.setYear(2024);
        req.setMonth(1);

        try {
            service.findMonthlyTotalPriceByMerchant(req).await().indefinitely();
            Assertions.fail("Expected ResourceNotFoundException");
        } catch (ResourceNotFoundException e) {
            assertThat(e.getMessage()).contains("MerchantId, Year and Month must not be null");
        }
    }

    @Test
    void findMonthlyTotalPriceByMerchant_cacheMiss_returnsFromDb() {
        FindMonthlyCategoryTotalPriceByMerchantRequest req = new FindMonthlyCategoryTotalPriceByMerchantRequest();
        req.setMerchantId(1L);
        req.setYear(2024);
        req.setMonth(1);

        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(categoryTotalPriceByMerchantRepository.findMonthlyTotalPriceByMerchant(any()))
                .thenReturn(Uni.createFrom().item(List.of(createMonthlyTotal())));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponse<List<CategoriesMonthlyTotalPriceResponse>> result = service.findMonthlyTotalPriceByMerchant(req)
                .await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.data()).hasSize(1);
    }

    @Test
    void findYearlyTotalPriceByMerchant_cacheMiss_returnsFromDb() {
        YearTotalPriceMerchantRequest req = new YearTotalPriceMerchantRequest();
        req.setMerchantId(1);
        req.setYear(2024);

        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(categoryTotalPriceByMerchantRepository.findYearlyTotalPriceByMerchant(anyInt(), anyInt()))
                .thenReturn(Uni.createFrom().item(List.of(createYearlyTotal())));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponse<List<CategoriesYearlyTotalPriceResponse>> result = service.findYearlyTotalPriceByMerchant(req)
                .await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.data()).hasSize(1);
    }
}
