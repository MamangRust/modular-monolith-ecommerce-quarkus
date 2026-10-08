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
import com.sanedge.transaction.domain.response.TransactionMonthlyMethodResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyMethodResponse;
import com.sanedge.transaction.service.stats.TransactionMethodService;

import io.grpc.StatusRuntimeException;
import io.smallrye.mutiny.Uni;
import pb.transaction.TransactionCommon;
import pb.transaction.stats.TransactionMethod;

@ExtendWith(MockitoExtension.class)
class TransactionMethodGrpcHandlerTest {

        @Mock
        private TransactionMethodService transactionMethodService;

        private TransactionMethodGrpcHandler handler;

        @BeforeEach
        void setUp() {
                handler = new TransactionMethodGrpcHandler();
                handler.transactionMethodService = transactionMethodService;
        }

        @Test
        @DisplayName("getMonthlyTransactionMethodSuccess - should return response on success")
        void getMonthlyTransactionMethodSuccess_Success() {
                TransactionMethod.MonthMethodTransactionRequest request = TransactionMethod.MonthMethodTransactionRequest
                                .newBuilder().setYear(2024).setMonth(1).build();

                TransactionMonthlyMethodResponse data = TransactionMonthlyMethodResponse.builder()
                                .month("Jan").paymentMethod("Credit Card").totalTransactions(10L).totalAmount(100000L)
                                .build();
                ApiResponse<java.util.List<TransactionMonthlyMethodResponse>> apiResp = ApiResponse
                                .success("Monthly method success retrieved successfully", java.util.List.of(data));

                when(transactionMethodService.findMonthlyMethodSuccess(any()))
                                .thenReturn(Uni.createFrom().item(apiResp));

                TransactionCommon.ApiResponseTransactionMonthPaymentMethod response = handler
                                .getMonthlyTransactionMethodSuccess(request).await().indefinitely();

                assertThat(response).isNotNull();
                assertThat(response.getStatus()).isEqualTo("success");
                assertThat(response.getDataCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("getMonthlyTransactionMethodSuccess - should return INTERNAL on failure")
        void getMonthlyTransactionMethodSuccess_InternalError() {
                TransactionMethod.MonthMethodTransactionRequest request = TransactionMethod.MonthMethodTransactionRequest
                                .newBuilder().setYear(2024).setMonth(1).build();

                when(transactionMethodService.findMonthlyMethodSuccess(any()))
                                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

                StatusRuntimeException ex = null;
                try {
                        handler.getMonthlyTransactionMethodSuccess(request).await().indefinitely();
                } catch (StatusRuntimeException e) {
                        ex = e;
                }

                assertThat(ex).isNotNull();
        }

        @Test
        @DisplayName("getYearlyTransactionMethodSuccess - should return response on success")
        void getYearlyTransactionMethodSuccess_Success() {
                TransactionMethod.YearMethodTransactionRequest request = TransactionMethod.YearMethodTransactionRequest
                                .newBuilder().setYear(2024).build();

                TransactionYearlyMethodResponse data = TransactionYearlyMethodResponse.builder()
                                .year("2024").paymentMethod("Credit Card").totalTransactions(100L).totalAmount(1000000L)
                                .build();
                ApiResponse<java.util.List<TransactionYearlyMethodResponse>> apiResp = ApiResponse
                                .success("Yearly method success retrieved successfully", java.util.List.of(data));

                when(transactionMethodService.findYearlyMethodSuccess(2024)).thenReturn(Uni.createFrom().item(apiResp));

                TransactionCommon.ApiResponseTransactionYearPaymentmethod response = handler
                                .getYearlyTransactionMethodSuccess(request).await().indefinitely();

                assertThat(response).isNotNull();
                assertThat(response.getStatus()).isEqualTo("success");
                assertThat(response.getDataCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("getMonthlyTransactionMethodFailed - should return response on success")
        void getMonthlyTransactionMethodFailed_Success() {
                TransactionMethod.MonthMethodTransactionRequest request = TransactionMethod.MonthMethodTransactionRequest
                                .newBuilder().setYear(2024).setMonth(1).build();

                TransactionMonthlyMethodResponse data = TransactionMonthlyMethodResponse.builder()
                                .month("Jan").paymentMethod("Debit Card").totalTransactions(3L).totalAmount(30000L)
                                .build();
                ApiResponse<java.util.List<TransactionMonthlyMethodResponse>> apiResp = ApiResponse
                                .success("Monthly method failed retrieved successfully", java.util.List.of(data));

                when(transactionMethodService.findMonthlyMethodFailed(any()))
                                .thenReturn(Uni.createFrom().item(apiResp));

                TransactionCommon.ApiResponseTransactionMonthPaymentMethod response = handler
                                .getMonthlyTransactionMethodFailed(request).await().indefinitely();

                assertThat(response).isNotNull();
                assertThat(response.getStatus()).isEqualTo("success");
        }

        @Test
        @DisplayName("getYearlyTransactionMethodFailed - should return response on success")
        void getYearlyTransactionMethodFailed_Success() {
                TransactionMethod.YearMethodTransactionRequest request = TransactionMethod.YearMethodTransactionRequest
                                .newBuilder().setYear(2024).build();

                TransactionYearlyMethodResponse data = TransactionYearlyMethodResponse.builder()
                                .year("2024").paymentMethod("Debit Card").totalTransactions(20L).totalAmount(200000L)
                                .build();
                ApiResponse<java.util.List<TransactionYearlyMethodResponse>> apiResp = ApiResponse
                                .success("Yearly method failed retrieved successfully", java.util.List.of(data));

                when(transactionMethodService.findYearlyMethodFailed(2024)).thenReturn(Uni.createFrom().item(apiResp));

                TransactionCommon.ApiResponseTransactionYearPaymentmethod response = handler
                                .getYearlyTransactionMethodFailed(request).await().indefinitely();

                assertThat(response).isNotNull();
                assertThat(response.getStatus()).isEqualTo("success");
        }
}
