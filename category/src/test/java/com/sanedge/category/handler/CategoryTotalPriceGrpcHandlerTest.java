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
import com.sanedge.category.service.stats.CategoryTotalPriceService;
import com.sanedge.common.domain.response.ApiResponse;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.smallrye.mutiny.Uni;
import pb.category.CategoryCommon;

@ExtendWith(MockitoExtension.class)
class CategoryTotalPriceGrpcHandlerTest {

    @Mock
    private CategoryTotalPriceService categoryTotalPriceService;

    private CategoryTotalPriceGrpcHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        handler = new CategoryTotalPriceGrpcHandler();
        Field field = CategoryTotalPriceGrpcHandler.class.getDeclaredField("categoryTotalPriceService");
        field.setAccessible(true);
        field.set(handler, categoryTotalPriceService);
    }

    @Test
    void findMonthlyTotalPrices_success_returnsResponse() {
        CategoryCommon.FindYearMonthTotalPrices request = CategoryCommon.FindYearMonthTotalPrices.newBuilder()
                .setYear(2024)
                .setMonth(1)
                .build();

        CategoriesMonthlyTotalPriceResponse data = CategoriesMonthlyTotalPriceResponse.builder()
                .year("2024")
                .month("January")
                .totalRevenue(1000L)
                .build();
        ApiResponse<List<CategoriesMonthlyTotalPriceResponse>> apiResp = new ApiResponse<>(
                "success", "Monthly total price stats retrieved successfully", List.of(data));

        when(categoryTotalPriceService.findMonthlyTotalPrice(any())).thenReturn(Uni.createFrom().item(apiResp));

        CategoryCommon.ApiResponseCategoryMonthlyTotalPrice response = handler.findMonthlyTotalPrices(request).await()
                .indefinitely();

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }

    @Test
    void findMonthlyTotalPrices_failure_returnsInternalError() {
        CategoryCommon.FindYearMonthTotalPrices request = CategoryCommon.FindYearMonthTotalPrices.newBuilder()
                .setYear(2024)
                .setMonth(1)
                .build();

        when(categoryTotalPriceService.findMonthlyTotalPrice(any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

        StatusRuntimeException ex = null;
        try {
            handler.findMonthlyTotalPrices(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }
        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INTERNAL.getCode());
    }

    @Test
    void findYearlyTotalPrices_success_returnsResponse() {
        CategoryCommon.FindYearTotalPrices request = CategoryCommon.FindYearTotalPrices.newBuilder()
                .setYear(2024)
                .build();

        CategoriesYearlyTotalPriceResponse data = CategoriesYearlyTotalPriceResponse.builder()
                .year("2024")
                .totalRevenue(12000L)
                .build();
        ApiResponse<List<CategoriesYearlyTotalPriceResponse>> apiResp = new ApiResponse<>(
                "success", "Yearly total price stats retrieved successfully", List.of(data));

        when(categoryTotalPriceService.findYearlyTotalPrice(2024)).thenReturn(Uni.createFrom().item(apiResp));

        CategoryCommon.ApiResponseCategoryYearlyTotalPrice response = handler.findYearlyTotalPrices(request).await()
                .indefinitely();

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }

}
