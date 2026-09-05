package com.sanedge.order.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
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
import com.sanedge.order.service.OrderSoldoutService;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.smallrye.mutiny.Uni;
import pb.order.OrderCommon;
import pb.order.stats.OrderRevenue;

@ExtendWith(MockitoExtension.class)
class OrderRevenueGrpcHandlerTest {

    @Mock
    private OrderSoldoutService orderSoldoutService;

    private OrderRevenueGrpcHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OrderRevenueGrpcHandler();
        handler.orderSoldoutService = orderSoldoutService;
    }

    // ========== findMonthlyRevenue Tests ==========

    @Test
    void findMonthlyRevenue_success_returnsResponse() {
        OrderRevenue.FindYearOrder request = OrderRevenue.FindYearOrder.newBuilder()
                .setYear(2024)
                .setMonth(1)
                .build();

        OrderMonthlyResponse data = OrderMonthlyResponse.builder()
                .month("Jan")
                .orderCount(10)
                .totalRevenue(1000L)
                .totalItemsSold(50)
                .build();
        ApiResponse<List<OrderMonthlyResponse>> apiResp = new ApiResponse<>(
                "success", "Monthly order data retrieved successfully", List.of(data));

        when(orderSoldoutService.findMonthlyOrders(anyInt(), anyInt())).thenReturn(Uni.createFrom().item(apiResp));

        OrderCommon.ApiResponseOrderMonthly response = handler.findMonthlyRevenue(request).await().indefinitely();

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }

    @Test
    void findMonthlyRevenue_failure_returnsInternalError() {
        OrderRevenue.FindYearOrder request = OrderRevenue.FindYearOrder.newBuilder()
                .setYear(2024)
                .setMonth(1)
                .build();

        when(orderSoldoutService.findMonthlyOrders(anyInt(), anyInt()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

        StatusRuntimeException ex = null;
        try {
            handler.findMonthlyRevenue(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }
        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INTERNAL.getCode());
    }

    // ========== findYearlyRevenue Tests ==========

    @Test
    void findYearlyRevenue_success_returnsResponse() {
        OrderRevenue.FindYearOrder request = OrderRevenue.FindYearOrder.newBuilder()
                .setYear(2024)
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

        when(orderSoldoutService.findYearlyOrders(anyInt())).thenReturn(Uni.createFrom().item(apiResp));

        OrderCommon.ApiResponseOrderYearly response = handler.findYearlyRevenue(request).await().indefinitely();

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }
}
