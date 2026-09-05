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
import com.sanedge.transaction.service.statsbymerchant.TransactionAmountByMerchantService;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.smallrye.mutiny.Uni;
import pb.transaction.TransactionCommon;
import pb.transaction.stats.TransactionAmount;

@ExtendWith(MockitoExtension.class)
class TransactionAmountByMerchantGrpcHandlerTest {

    @Mock
    private TransactionAmountByMerchantService transactionAmountByMerchantService;

    private TransactionAmountByMerchantGrpcHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TransactionAmountByMerchantGrpcHandler();
        handler.transactionAmountByMerchantService = transactionAmountByMerchantService;
    }

    // ========== getMonthlyAmountSuccessByMerchant Tests ==========

    @Test
    @DisplayName("getMonthlyAmountSuccessByMerchant - should return response on success")
    void getMonthlyAmountSuccessByMerchant_Success() {
        TransactionAmount.MonthAmountTransactionMerchantRequest request = TransactionAmount.MonthAmountTransactionMerchantRequest
                .newBuilder().setMerchantId(200).setYear(2024).setMonth(1).build();

        TransactionMonthlyAmountSuccessResponse data = TransactionMonthlyAmountSuccessResponse.builder()
                .year("2024").month("Jan").totalSuccess(10L).totalAmount(100000L).build();
        ApiResponse<java.util.List<TransactionMonthlyAmountSuccessResponse>> apiResp = ApiResponse
                .success("Monthly amount success for merchant retrieved successfully", java.util.List.of(data));

        when(transactionAmountByMerchantService.findMonthlyAmountSuccessByMerchant(any()))
                .thenReturn(Uni.createFrom().item(apiResp));

        TransactionCommon.ApiResponseTransactionMonthAmountSuccess response = handler
                .getMonthlyAmountSuccessByMerchant(request).await().indefinitely();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("getMonthlyAmountSuccessByMerchant - should return INTERNAL on failure")
    void getMonthlyAmountSuccessByMerchant_InternalError() {
        TransactionAmount.MonthAmountTransactionMerchantRequest request = TransactionAmount.MonthAmountTransactionMerchantRequest
                .newBuilder().setMerchantId(200).setYear(2024).setMonth(1).build();

        when(transactionAmountByMerchantService.findMonthlyAmountSuccessByMerchant(any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

        StatusRuntimeException ex = null;
        try {
            handler.getMonthlyAmountSuccessByMerchant(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INTERNAL.getCode());
    }

    // ========== getYearlyAmountSuccessByMerchant Tests ==========

    @Test
    @DisplayName("getYearlyAmountSuccessByMerchant - should return response on success")
    void getYearlyAmountSuccessByMerchant_Success() {
        TransactionAmount.YearAmountTransactionMerchantRequest request = TransactionAmount.YearAmountTransactionMerchantRequest
                .newBuilder().setMerchantId(200).setYear(2024).build();

        TransactionYearlyAmountSuccessResponse data = TransactionYearlyAmountSuccessResponse.builder()
                .year("2024").totalSuccess(100L).totalAmount(1000000L).build();
        ApiResponse<java.util.List<TransactionYearlyAmountSuccessResponse>> apiResp = ApiResponse
                .success("Yearly amount success for merchant retrieved successfully", java.util.List.of(data));

        when(transactionAmountByMerchantService.findYearlyAmountSuccessByMerchant(any()))
                .thenReturn(Uni.createFrom().item(apiResp));

        TransactionCommon.ApiResponseTransactionYearAmountSuccess response = handler
                .getYearlyAmountSuccessByMerchant(request).await().indefinitely();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }

    // ========== getMonthlyAmountFailedByMerchant Tests ==========

    @Test
    @DisplayName("getMonthlyAmountFailedByMerchant - should return response on success")
    void getMonthlyAmountFailedByMerchant_Success() {
        TransactionAmount.MonthAmountTransactionMerchantRequest request = TransactionAmount.MonthAmountTransactionMerchantRequest
                .newBuilder().setMerchantId(200).setYear(2024).setMonth(1).build();

        TransactionMonthlyAmountFailedResponse data = TransactionMonthlyAmountFailedResponse.builder()
                .year("2024").month("Jan").totalFailed(5L).totalAmount(50000L).build();
        ApiResponse<java.util.List<TransactionMonthlyAmountFailedResponse>> apiResp = ApiResponse
                .success("Monthly amount failed for merchant retrieved successfully", java.util.List.of(data));

        when(transactionAmountByMerchantService.findMonthlyAmountFailedByMerchant(any()))
                .thenReturn(Uni.createFrom().item(apiResp));

        TransactionCommon.ApiResponseTransactionMonthAmountFailed response = handler
                .getMonthlyAmountFailedByMerchant(request).await().indefinitely();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
    }

    // ========== getYearlyAmountFailedByMerchant Tests ==========

    @Test
    @DisplayName("getYearlyAmountFailedByMerchant - should return response on success")
    void getYearlyAmountFailedByMerchant_Success() {
        TransactionAmount.YearAmountTransactionMerchantRequest request = TransactionAmount.YearAmountTransactionMerchantRequest
                .newBuilder().setMerchantId(200).setYear(2024).build();

        TransactionYearlyAmountFailedResponse data = TransactionYearlyAmountFailedResponse.builder()
                .year("2024").totalFailed(50L).totalAmount(500000L).build();
        ApiResponse<java.util.List<TransactionYearlyAmountFailedResponse>> apiResp = ApiResponse
                .success("Yearly amount failed for merchant retrieved successfully", java.util.List.of(data));

        when(transactionAmountByMerchantService.findYearlyAmountFailedByMerchant(any()))
                .thenReturn(Uni.createFrom().item(apiResp));

        TransactionCommon.ApiResponseTransactionYearAmountFailed response = handler
                .getYearlyAmountFailedByMerchant(request).await().indefinitely();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
    }
}
