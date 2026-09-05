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
import com.sanedge.category.service.stats.CategoryTotalPriceByIdService;
import com.sanedge.common.domain.response.ApiResponse;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.smallrye.mutiny.Uni;
import pb.category.CategoryCommon;

@ExtendWith(MockitoExtension.class)
class CategoryTotalPriceByIdGrpcHandlerTest {

    @Mock
    private CategoryTotalPriceByIdService categoryTotalPriceByIdService;

    private CategoryTotalPriceByIdGrpcHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        handler = new CategoryTotalPriceByIdGrpcHandler();
        Field field = CategoryTotalPriceByIdGrpcHandler.class.getDeclaredField("categoryTotalPriceByIdService");
        field.setAccessible(true);
        field.set(handler, categoryTotalPriceByIdService);
    }

    // ========== findMonthlyTotalPricesById Tests ==========

    @Test
    void findMonthlyTotalPricesById_success_returnsResponse() {
        CategoryCommon.FindYearMonthTotalPriceById request = CategoryCommon.FindYearMonthTotalPriceById.newBuilder()
                .setYear(2024)
                .setMonth(1)
                .setCategoryId(1)
                .build();

        CategoriesMonthlyTotalPriceResponse data = CategoriesMonthlyTotalPriceResponse.builder()
                .year("2024")
                .month("January")
                .totalRevenue(1000L)
                .build();
        ApiResponse<List<CategoriesMonthlyTotalPriceResponse>> apiResp = new ApiResponse<>(
                "success", "Monthly total price by category retrieved successfully", List.of(data));

        when(categoryTotalPriceByIdService.findMonthlyTotalPriceById(any()))
                .thenReturn(Uni.createFrom().item(apiResp));

        CategoryCommon.ApiResponseCategoryMonthlyTotalPrice response = handler.findMonthlyTotalPricesById(request)
                .await().indefinitely();

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }

    @Test
    void findMonthlyTotalPricesById_failure_returnsInternalError() {
        CategoryCommon.FindYearMonthTotalPriceById request = CategoryCommon.FindYearMonthTotalPriceById.newBuilder()
                .setYear(2024)
                .setMonth(1)
                .setCategoryId(1)
                .build();

        when(categoryTotalPriceByIdService.findMonthlyTotalPriceById(any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

        StatusRuntimeException ex = null;
        try {
            handler.findMonthlyTotalPricesById(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }
        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INTERNAL.getCode());
    }

    // ========== findYearlyTotalPricesById Tests ==========

    @Test
    void findYearlyTotalPricesById_success_returnsResponse() {
        CategoryCommon.FindYearTotalPriceById request = CategoryCommon.FindYearTotalPriceById.newBuilder()
                .setYear(2024)
                .setCategoryId(1)
                .build();

        CategoriesYearlyTotalPriceResponse data = CategoriesYearlyTotalPriceResponse.builder()
                .year("2024")
                .totalRevenue(12000L)
                .build();
        ApiResponse<List<CategoriesYearlyTotalPriceResponse>> apiResp = new ApiResponse<>(
                "success", "Yearly total price by category retrieved successfully", List.of(data));

        when(categoryTotalPriceByIdService.findYearlyTotalPriceById(any()))
                .thenReturn(Uni.createFrom().item(apiResp));

        CategoryCommon.ApiResponseCategoryYearlyTotalPrice response = handler.findYearlyTotalPricesById(request).await()
                .indefinitely();

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }
}
