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

import com.sanedge.category.domain.response.CategoriesMonthlyTotalPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearlyTotalPriceResponse;
import com.sanedge.category.service.stats.CategoryTotalPriceByMerchantService;
import com.sanedge.common.domain.response.ApiResponse;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.smallrye.mutiny.Uni;
import pb.category.CategoryCommon;

@ExtendWith(MockitoExtension.class)
class CategoryTotalPriceByMerchantGrpcHandlerTest {

    @Mock
    private CategoryTotalPriceByMerchantService categoryTotalPriceByMerchantService;

    private CategoryTotalPriceByMerchantGrpcHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        handler = new CategoryTotalPriceByMerchantGrpcHandler();
        Field field = CategoryTotalPriceByMerchantGrpcHandler.class
                .getDeclaredField("categoryTotalPriceByMerchantService");
        field.setAccessible(true);
        field.set(handler, categoryTotalPriceByMerchantService);
    }

    // ========== findMonthlyTotalPricesByMerchant Tests ==========

    @Test
    void findMonthlyTotalPricesByMerchant_success_returnsResponse() {
        CategoryCommon.FindYearMonthTotalPriceByMerchant request = CategoryCommon.FindYearMonthTotalPriceByMerchant
                .newBuilder()
                .setYear(2024)
                .setMonth(1)
                .setMerchantId(1)
                .build();

        CategoriesMonthlyTotalPriceResponse data = CategoriesMonthlyTotalPriceResponse.builder()
                .year("2024")
                .month("January")
                .totalRevenue(1000L)
                .build();
        ApiResponse<List<CategoriesMonthlyTotalPriceResponse>> apiResp = new ApiResponse<>(
                "success", "Monthly total price by merchant retrieved successfully", List.of(data));

        when(categoryTotalPriceByMerchantService.findMonthlyTotalPriceByMerchant(any()))
                .thenReturn(Uni.createFrom().item(apiResp));

        CategoryCommon.ApiResponseCategoryMonthlyTotalPrice response = handler
                .findMonthlyTotalPricesByMerchant(request)
                .await().indefinitely();

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }

    @Test
    void findMonthlyTotalPricesByMerchant_failure_returnsInternalError() {
        CategoryCommon.FindYearMonthTotalPriceByMerchant request = CategoryCommon.FindYearMonthTotalPriceByMerchant
                .newBuilder()
                .setYear(2024)
                .setMonth(1)
                .setMerchantId(1)
                .build();

        when(categoryTotalPriceByMerchantService.findMonthlyTotalPriceByMerchant(any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

        StatusRuntimeException ex = null;
        try {
            handler.findMonthlyTotalPricesByMerchant(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }
        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INTERNAL.getCode());
    }

    // ========== findYearlyTotalPricesByMerchant Tests ==========

    @Test
    void findYearlyTotalPricesByMerchant_success_returnsResponse() {
        CategoryCommon.FindYearTotalPriceByMerchant request = CategoryCommon.FindYearTotalPriceByMerchant.newBuilder()
                .setYear(2024)
                .setMerchantId(1)
                .build();

        CategoriesYearlyTotalPriceResponse data = CategoriesYearlyTotalPriceResponse.builder()
                .year("2024")
                .totalRevenue(12000L)
                .build();
        ApiResponse<List<CategoriesYearlyTotalPriceResponse>> apiResp = new ApiResponse<>(
                "success", "Yearly total price by merchant retrieved successfully", List.of(data));

        when(categoryTotalPriceByMerchantService.findYearlyTotalPriceByMerchant(any()))
                .thenReturn(Uni.createFrom().item(apiResp));

        CategoryCommon.ApiResponseCategoryYearlyTotalPrice response = handler.findYearlyTotalPricesByMerchant(request)
                .await().indefinitely();

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }
}
