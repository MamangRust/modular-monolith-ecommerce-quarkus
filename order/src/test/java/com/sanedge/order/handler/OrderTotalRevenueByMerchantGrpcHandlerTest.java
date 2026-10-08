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
import com.sanedge.order.domain.response.OrderMonthlyTotalRevenueResponse;
import com.sanedge.order.domain.response.OrderYearlyTotalRevenueResponse;
import com.sanedge.order.service.OrderTotalRevenueByMerchantService;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.smallrye.mutiny.Uni;
import pb.order.OrderCommon;
import pb.order.stats.OrderTotalRevenue;

@ExtendWith(MockitoExtension.class)
class OrderTotalRevenueByMerchantGrpcHandlerTest {

    @Mock
    private OrderTotalRevenueByMerchantService orderTotalRevenueByMerchantService;

    private OrderTotalRevenueByMerchantGrpcHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OrderTotalRevenueByMerchantGrpcHandler();
        handler.orderTotalRevenueByMerchantService = orderTotalRevenueByMerchantService;
    }

    @Test
    void findMonthlyTotalRevenueByMerchant_success_returnsResponse() {
        OrderTotalRevenue.FindYearMonthTotalRevenueByMerchant request = OrderTotalRevenue.FindYearMonthTotalRevenueByMerchant
                .newBuilder()
                .setYear(2024)
                .setMonth(1)
                .setMerchantId(1)
                .build();

        OrderMonthlyTotalRevenueResponse data = OrderMonthlyTotalRevenueResponse.builder()
                .year("2024")
                .month("January")
                .totalRevenue(1000L)
                .build();
        ApiResponse<List<OrderMonthlyTotalRevenueResponse>> apiResp = new ApiResponse<>(
                "success", "Monthly order stats for merchant retrieved successfully", List.of(data));

        when(orderTotalRevenueByMerchantService.findMonthlyStatsByMerchant(any()))
                .thenReturn(Uni.createFrom().item(apiResp));

        OrderCommon.ApiResponseOrderMonthlyTotalRevenue response = handler.findMonthlyTotalRevenueByMerchant(request)
                .await().indefinitely();

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }

    @Test
    void findMonthlyTotalRevenueByMerchant_failure_returnsInternalError() {
        OrderTotalRevenue.FindYearMonthTotalRevenueByMerchant request = OrderTotalRevenue.FindYearMonthTotalRevenueByMerchant
                .newBuilder()
                .setYear(2024)
                .setMonth(1)
                .setMerchantId(1)
                .build();

        when(orderTotalRevenueByMerchantService.findMonthlyStatsByMerchant(any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

        StatusRuntimeException ex = null;
        try {
            handler.findMonthlyTotalRevenueByMerchant(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }
        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INTERNAL.getCode());
    }

    @Test
    void findYearlyTotalRevenueByMerchant_success_returnsResponse() {
        OrderTotalRevenue.FindYearTotalRevenueByMerchant request = OrderTotalRevenue.FindYearTotalRevenueByMerchant
                .newBuilder()
                .setYear(2024)
                .setMerchantId(1)
                .build();

        OrderYearlyTotalRevenueResponse data = OrderYearlyTotalRevenueResponse.builder()
                .year("2024")
                .totalRevenue(12000L)
                .build();
        ApiResponse<List<OrderYearlyTotalRevenueResponse>> apiResp = new ApiResponse<>(
                "success", "Yearly order stats for merchant retrieved successfully", List.of(data));

        when(orderTotalRevenueByMerchantService.findYearlyStatsByMerchant(any()))
                .thenReturn(Uni.createFrom().item(apiResp));

        OrderCommon.ApiResponseOrderYearlyTotalRevenue response = handler.findYearlyTotalRevenueByMerchant(request)
                .await().indefinitely();

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }
}
