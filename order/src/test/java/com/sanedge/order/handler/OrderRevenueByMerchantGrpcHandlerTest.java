package com.sanedge.order.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.order.domain.response.OrderMonthlyResponse;
import com.sanedge.order.domain.response.OrderYearlyResponse;
import com.sanedge.order.service.OrderSoldOutByMerchantService;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.smallrye.mutiny.Uni;
import pb.order.OrderCommon;
import pb.order.stats.OrderRevenue;

@ExtendWith(MockitoExtension.class)
class OrderRevenueByMerchantGrpcHandlerTest {

    @Mock
    private OrderSoldOutByMerchantService orderSoldOutByMerchantService;

    private OrderRevenueByMerchantGrpcHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OrderRevenueByMerchantGrpcHandler();
        handler.orderSoldOutByMerchantService = orderSoldOutByMerchantService;
    }

    // ========== findMonthlyRevenueByMerchant Tests ==========

    @Test
    void findMonthlyRevenueByMerchant_success_returnsResponse() {
        OrderRevenue.FindYearOrderByMerchant request = OrderRevenue.FindYearOrderByMerchant.newBuilder()
                .setYear(2024)
                .setMonth(1)
                .setMerchantId(1)
                .build();

        OrderMonthlyResponse data = OrderMonthlyResponse.builder()
                .month("Jan")
                .orderCount(10)
                .totalRevenue(1000L)
                .totalItemsSold(50)
                .build();
        ApiResponse<List<OrderMonthlyResponse>> apiResp = new ApiResponse<>(
                "success", "Monthly order data retrieved successfully", List.of(data));

        when(orderSoldOutByMerchantService.findMonthlyOrdersByMerchant(any()))
                .thenReturn(Uni.createFrom().item(apiResp));

        OrderCommon.ApiResponseOrderMonthly response = handler.findMonthlyRevenueByMerchant(request).await()
                .indefinitely();

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }

    @Test
    void findMonthlyRevenueByMerchant_failure_returnsInternalError() {
        OrderRevenue.FindYearOrderByMerchant request = OrderRevenue.FindYearOrderByMerchant.newBuilder()
                .setYear(2024)
                .setMonth(1)
                .setMerchantId(1)
                .build();

        when(orderSoldOutByMerchantService.findMonthlyOrdersByMerchant(any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

        StatusRuntimeException ex = null;
        try {
            handler.findMonthlyRevenueByMerchant(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }
        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INTERNAL.getCode());
    }

    // ========== findYearlyRevenueByMerchant Tests ==========

    @Test
    void findYearlyRevenueByMerchant_success_returnsResponse() {
        OrderRevenue.FindYearOrderByMerchant request = OrderRevenue.FindYearOrderByMerchant.newBuilder()
                .setYear(2024)
                .setMerchantId(1)
                .build();

        OrderYearlyResponse data = OrderYearlyResponse.builder()
                .year("2024")
                .orderCount(100)
                .totalRevenue(100000L)
                .totalItemsSold(500)
                .activeCashiers(10)
                .uniqueProductsSold(20)
                .build();
        ApiResponse<List<OrderYearlyResponse>> apiResp = new ApiResponse<>(
                "success", "Yearly order data retrieved successfully", List.of(data));

        when(orderSoldOutByMerchantService.findYearlyOrdersByMerchant(any()))
                .thenReturn(Uni.createFrom().item(apiResp));

        OrderCommon.ApiResponseOrderYearly response = handler.findYearlyRevenueByMerchant(request).await().indefinitely();

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }
}
