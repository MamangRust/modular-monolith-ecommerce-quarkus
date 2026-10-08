package com.sanedge.order.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.order.domain.response.OrderMonthlyTotalRevenueResponse;
import com.sanedge.order.domain.response.OrderYearlyTotalRevenueResponse;
import com.sanedge.order.service.OrderTotalRevenueService;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.smallrye.mutiny.Uni;
import pb.order.OrderCommon;
import pb.order.stats.OrderTotalRevenue;

@ExtendWith(MockitoExtension.class)
class OrderTotalRevenueGrpcHandlerTest {

    @Mock
    private OrderTotalRevenueService orderTotalRevenueService;

    private OrderTotalRevenueGrpcHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OrderTotalRevenueGrpcHandler();
        handler.orderTotalRevenueService = orderTotalRevenueService;
    }

    @Test
    void findMonthlyTotalRevenue_success_returnsResponse() {
        OrderTotalRevenue.FindYearMonthTotalRevenue request = OrderTotalRevenue.FindYearMonthTotalRevenue.newBuilder()
                .setYear(2024)
                .setMonth(1)
                .build();

        OrderMonthlyTotalRevenueResponse data = OrderMonthlyTotalRevenueResponse.builder()
                .year("2024")
                .month("January")
                .totalRevenue(1000L)
                .build();
        ApiResponse<List<OrderMonthlyTotalRevenueResponse>> apiResp = new ApiResponse<>(
                "success", "Monthly order stats retrieved successfully", List.of(data));

        when(orderTotalRevenueService.findMonthlyStats(any())).thenReturn(Uni.createFrom().item(apiResp));

        OrderCommon.ApiResponseOrderMonthlyTotalRevenue response = handler.findMonthlyTotalRevenue(request).await()
                .indefinitely();

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }

    @Test
    void findMonthlyTotalRevenue_failure_returnsInternalError() {
        OrderTotalRevenue.FindYearMonthTotalRevenue request = OrderTotalRevenue.FindYearMonthTotalRevenue.newBuilder()
                .setYear(2024)
                .setMonth(1)
                .build();

        when(orderTotalRevenueService.findMonthlyStats(any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

        StatusRuntimeException ex = null;
        try {
            handler.findMonthlyTotalRevenue(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }
        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INTERNAL.getCode());
    }

    @Test
    void findYearlyTotalRevenue_success_returnsResponse() {
        OrderTotalRevenue.FindYearTotalRevenue request = OrderTotalRevenue.FindYearTotalRevenue.newBuilder()
                .setYear(2024)
                .build();

        OrderYearlyTotalRevenueResponse data = OrderYearlyTotalRevenueResponse.builder()
                .year("2024")
                .totalRevenue(12000L)
                .build();
        ApiResponse<List<OrderYearlyTotalRevenueResponse>> apiResp = new ApiResponse<>(
                "success", "Yearly order stats retrieved successfully", List.of(data));

        when(orderTotalRevenueService.findYearlyStats(anyInt())).thenReturn(Uni.createFrom().item(apiResp));

        OrderCommon.ApiResponseOrderYearlyTotalRevenue response = handler.findYearlyTotalRevenue(request).await()
                .indefinitely();

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }
}
