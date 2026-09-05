package com.sanedge.transaction.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.transaction.domain.response.TransactionMonthlyAmountFailedResponse;
import com.sanedge.transaction.domain.response.TransactionMonthlyAmountSuccessResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyAmountFailedResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyAmountSuccessResponse;
import com.sanedge.transaction.service.stats.TransactionAmountService;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.smallrye.mutiny.Uni;
import pb.transaction.TransactionCommon;
import pb.transaction.stats.TransactionAmount;

@ExtendWith(MockitoExtension.class)
class TransactionAmountGrpcHandlerTest {

    @Mock
    private TransactionAmountService transactionAmountService;

    private TransactionAmountGrpcHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TransactionAmountGrpcHandler();
        handler.transactionAmountService = transactionAmountService;
    }

    // ========== getMonthlyAmountSuccess Tests ==========

    @Test
    @DisplayName("getMonthlyAmountSuccess - should return ApiResponseTransactionMonthAmountSuccess on success")
    void getMonthlyAmountSuccess_Success() {
        TransactionAmount.MonthAmountTransactionRequest request = TransactionAmount.MonthAmountTransactionRequest
                .newBuilder().setYear(2024).setMonth(1).build();

        TransactionMonthlyAmountSuccessResponse data = TransactionMonthlyAmountSuccessResponse.builder()
                .year("2024").month("Jan").totalSuccess(10L).totalAmount(100000L).build();
        ApiResponse<java.util.List<TransactionMonthlyAmountSuccessResponse>> apiResp = ApiResponse
                .success("Monthly amount success retrieved successfully", java.util.List.of(data));

        when(transactionAmountService.findMonthlyAmountSuccess(any())).thenReturn(Uni.createFrom().item(apiResp));

        TransactionCommon.ApiResponseTransactionMonthAmountSuccess response = handler.getMonthlyAmountSuccess(request)
                .await().indefinitely();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("getMonthlyAmountSuccess - should return INTERNAL on failure")
    void getMonthlyAmountSuccess_InternalError() {
        TransactionAmount.MonthAmountTransactionRequest request = TransactionAmount.MonthAmountTransactionRequest
                .newBuilder().setYear(2024).setMonth(1).build();

        when(transactionAmountService.findMonthlyAmountSuccess(any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

        StatusRuntimeException ex = null;
        try {
            handler.getMonthlyAmountSuccess(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INTERNAL.getCode());
    }

    // ========== getYearlyAmountSuccess Tests ==========

    @Test
    @DisplayName("getYearlyAmountSuccess - should return ApiResponseTransactionYearAmountSuccess on success")
    void getYearlyAmountSuccess_Success() {
        TransactionAmount.YearAmountTransactionRequest request = TransactionAmount.YearAmountTransactionRequest
                .newBuilder().setYear(2024).build();

        TransactionYearlyAmountSuccessResponse data = TransactionYearlyAmountSuccessResponse.builder()
                .year("2024").totalSuccess(100L).totalAmount(1000000L).build();
        ApiResponse<java.util.List<TransactionYearlyAmountSuccessResponse>> apiResp = ApiResponse
                .success("Yearly amount success retrieved successfully", java.util.List.of(data));

        when(transactionAmountService.findYearlyAmountSuccess(2024)).thenReturn(Uni.createFrom().item(apiResp));

        TransactionCommon.ApiResponseTransactionYearAmountSuccess response = handler.getYearlyAmountSuccess(request)
                .await().indefinitely();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }

    // ========== getMonthlyAmountFailed Tests ==========

    @Test
    @DisplayName("getMonthlyAmountFailed - should return ApiResponseTransactionMonthAmountFailed on success")
    void getMonthlyAmountFailed_Success() {
        TransactionAmount.MonthAmountTransactionRequest request = TransactionAmount.MonthAmountTransactionRequest
                .newBuilder().setYear(2024).setMonth(1).build();

        TransactionMonthlyAmountFailedResponse data = TransactionMonthlyAmountFailedResponse.builder()
                .year("2024").month("Jan").totalFailed(5L).totalAmount(50000L).build();
        ApiResponse<java.util.List<TransactionMonthlyAmountFailedResponse>> apiResp = ApiResponse
                .success("Monthly amount failed retrieved successfully", java.util.List.of(data));

        when(transactionAmountService.findMonthlyAmountFailed(any())).thenReturn(Uni.createFrom().item(apiResp));

        TransactionCommon.ApiResponseTransactionMonthAmountFailed response = handler.getMonthlyAmountFailed(request)
                .await().indefinitely();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }

    // ========== getYearlyAmountFailed Tests ==========

    @Test
    @DisplayName("getYearlyAmountFailed - should return ApiResponseTransactionYearAmountFailed on success")
    void getYearlyAmountFailed_Success() {
        TransactionAmount.YearAmountTransactionRequest request = TransactionAmount.YearAmountTransactionRequest
                .newBuilder().setYear(2024).build();

        TransactionYearlyAmountFailedResponse data = TransactionYearlyAmountFailedResponse.builder()
                .year("2024").totalFailed(50L).totalAmount(500000L).build();
        ApiResponse<java.util.List<TransactionYearlyAmountFailedResponse>> apiResp = ApiResponse
                .success("Yearly amount failed retrieved successfully", java.util.List.of(data));

        when(transactionAmountService.findYearlyAmountFailed(2024)).thenReturn(Uni.createFrom().item(apiResp));

        TransactionCommon.ApiResponseTransactionYearAmountFailed response = handler.getYearlyAmountFailed(request)
                .await().indefinitely();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }
}
