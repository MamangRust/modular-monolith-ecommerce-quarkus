package com.sanedge.category.handler;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.sanedge.category.service.stats.CategoryPriceService;
import com.sanedge.common.domain.response.ApiResponse;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.smallrye.mutiny.Uni;
import pb.category.CategoryCommon;
import pb.category.stats.MutinyCategoryPriceServiceGrpc;

@ExtendWith(MockitoExtension.class)
class CategoryPriceGrpcHandlerTest {

    @Mock
    private CategoryPriceService categoryPriceService;

    private CategoryPriceGrpcHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        handler = new CategoryPriceGrpcHandler();
        Field field = CategoryPriceGrpcHandler.class.getDeclaredField("categoryPriceService");
        field.setAccessible(true);
        field.set(handler, categoryPriceService);
    }

    // ========== findMonthPrice Tests ==========

    @Test
    void findMonthPrice_success_returnsResponse() {
        CategoryCommon.FindYearCategory request = CategoryCommon.FindYearCategory.newBuilder().setYear(2024).build();

        CategoriesMonthPriceResponse data = CategoriesMonthPriceResponse.builder()
                .month("Jan")
                .categoryId(1L)
                .categoryName("Cat-1")
                .orderCount(10L)
                .itemsSold(50L)
                .totalRevenue(1000L)
                .build();
        ApiResponse<List<CategoriesMonthPriceResponse>> apiResp = new ApiResponse<>(
                "success", "Monthly category price stats retrieved successfully", List.of(data));

        when(categoryPriceService.findMonthPrice(2024)).thenReturn(Uni.createFrom().item(apiResp));

        CategoryCommon.ApiResponseCategoryMonthPrice response = handler.findMonthPrice(request).await().indefinitely();

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
        assertThat(response.getData(0).getMonth()).isEqualTo("Jan");
    }

    @Test
    void findMonthPrice_failure_returnsInternalError() {
        CategoryCommon.FindYearCategory request = CategoryCommon.FindYearCategory.newBuilder().setYear(2024).build();

        when(categoryPriceService.findMonthPrice(2024))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

        StatusRuntimeException ex = null;
        try {
            handler.findMonthPrice(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }
        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INTERNAL.getCode());
    }

    // ========== findYearPrice Tests ==========

    @Test
    void findYearPrice_success_returnsResponse() {
        CategoryCommon.FindYearCategory request = CategoryCommon.FindYearCategory.newBuilder().setYear(2024).build();

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
                "success", "Yearly category price stats retrieved successfully", List.of(data));

        when(categoryPriceService.findYearPrice(2024)).thenReturn(Uni.createFrom().item(apiResp));

        CategoryCommon.ApiResponseCategoryYearPrice response = handler.findYearPrice(request).await().indefinitely();

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }
}
