package com.sanedge.category.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.category.domain.response.CategoriesMonthPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearPriceResponse;
import com.sanedge.category.service.stats.CategoryPriceByMerchantService;
import com.sanedge.common.domain.response.ApiResponse;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.smallrye.mutiny.Uni;
import pb.category.CategoryCommon;

@ExtendWith(MockitoExtension.class)
class CategoryPriceByMerchantGrpcHandlerTest {

    @Mock
    private CategoryPriceByMerchantService categoryPriceByMerchantService;

    private CategoryPriceByMerchantGrpcHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        handler = new CategoryPriceByMerchantGrpcHandler();
        Field field = CategoryPriceByMerchantGrpcHandler.class.getDeclaredField("categoryPriceByMerchantService");
        field.setAccessible(true);
        field.set(handler, categoryPriceByMerchantService);
    }

    // ========== findMonthPriceByMerchant Tests ==========

    @Test
    void findMonthPriceByMerchant_success_returnsResponse() {
        CategoryCommon.FindYearCategoryByMerchant request = CategoryCommon.FindYearCategoryByMerchant.newBuilder()
                .setYear(2024)
                .setMerchantId(1)
                .build();

        CategoriesMonthPriceResponse data = CategoriesMonthPriceResponse.builder()
                .month("Jan")
                .categoryId(1L)
                .categoryName("Cat-1")
                .orderCount(10L)
                .itemsSold(50L)
                .totalRevenue(1000L)
                .build();
        ApiResponse<List<CategoriesMonthPriceResponse>> apiResp = new ApiResponse<>(
                "success", "Monthly price by merchant retrieved successfully", List.of(data));

        when(categoryPriceByMerchantService.findMonthPriceByMerchant(any()))
                .thenReturn(Uni.createFrom().item(apiResp));

        CategoryCommon.ApiResponseCategoryMonthPrice response = handler.findMonthPriceByMerchant(request).await()
                .indefinitely();

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }

    @Test
    void findMonthPriceByMerchant_failure_returnsInternalError() {
        CategoryCommon.FindYearCategoryByMerchant request = CategoryCommon.FindYearCategoryByMerchant.newBuilder()
                .setYear(2024)
                .setMerchantId(1)
                .build();

        when(categoryPriceByMerchantService.findMonthPriceByMerchant(any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

        StatusRuntimeException ex = null;
        try {
            handler.findMonthPriceByMerchant(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }
        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INTERNAL.getCode());
    }

    // ========== findYearPriceByMerchant Tests ==========

    @Test
    void findYearPriceByMerchant_success_returnsResponse() {
        CategoryCommon.FindYearCategoryByMerchant request = CategoryCommon.FindYearCategoryByMerchant.newBuilder()
                .setYear(2024)
                .setMerchantId(1)
                .build();

        CategoriesYearPriceResponse data = CategoriesYearPriceResponse.builder()
                .year("2024")
                .categoryId(1L)
                .categoryName("Cat-1")
                .orderCount(100L)
                .itemsSold(500L)
                .totalRevenue(100000L)
                .uniqueProductsSold(20L)
                .build();
        ApiResponse<List<CategoriesYearPriceResponse>> apiResp = new ApiResponse<>(
                "success", "Yearly price by merchant retrieved successfully", List.of(data));

        when(categoryPriceByMerchantService.findYearPriceByMerchant(any()))
                .thenReturn(Uni.createFrom().item(apiResp));

        CategoryCommon.ApiResponseCategoryYearPrice response = handler.findYearPriceByMerchant(request).await()
                .indefinitely();

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }
}
