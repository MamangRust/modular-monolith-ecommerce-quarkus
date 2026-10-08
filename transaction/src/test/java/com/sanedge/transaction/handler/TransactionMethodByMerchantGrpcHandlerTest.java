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
import com.sanedge.transaction.service.statsbymerchant.TransactionMethodByMerchantService;

import io.grpc.StatusRuntimeException;
import io.smallrye.mutiny.Uni;
import pb.transaction.TransactionCommon;
import pb.transaction.stats.TransactionMethod;

@ExtendWith(MockitoExtension.class)
class TransactionMethodByMerchantGrpcHandlerTest {

        @Mock
        private TransactionMethodByMerchantService transactionMethodByMerchantService;

        private TransactionMethodByMerchantGrpcHandler handler;

        @BeforeEach
        void setUp() {
                handler = new TransactionMethodByMerchantGrpcHandler();
                handler.transactionMethodByMerchantService = transactionMethodByMerchantService;
        }

        @Test
        @DisplayName("getMonthlyTransactionMethodByMerchantSuccess - should return response on success")
        void getMonthlyTransactionMethodByMerchantSuccess_Success() {
                TransactionMethod.MonthMethodTransactionMerchantRequest request = TransactionMethod.MonthMethodTransactionMerchantRequest
                                .newBuilder().setMerchantId(200).setYear(2024).setMonth(1).build();

                TransactionMonthlyMethodResponse data = TransactionMonthlyMethodResponse.builder()
                                .month("Jan").paymentMethod("Credit Card").totalTransactions(10L).totalAmount(100000L)
                                .build();
                ApiResponse<java.util.List<TransactionMonthlyMethodResponse>> apiResp = ApiResponse
                                .success("Monthly method success for merchant retrieved successfully",
                                                java.util.List.of(data));

                when(transactionMethodByMerchantService.findMonthlyMethodByMerchantSuccess(any()))
                                .thenReturn(Uni.createFrom().item(apiResp));

                TransactionCommon.ApiResponseTransactionMonthPaymentMethod response = handler
                                .getMonthlyTransactionMethodByMerchantSuccess(request).await().indefinitely();

                assertThat(response).isNotNull();
                assertThat(response.getStatus()).isEqualTo("success");
                assertThat(response.getDataCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("getMonthlyTransactionMethodByMerchantSuccess - should return INTERNAL on failure")
        void getMonthlyTransactionMethodByMerchantSuccess_InternalError() {
                TransactionMethod.MonthMethodTransactionMerchantRequest request = TransactionMethod.MonthMethodTransactionMerchantRequest
                                .newBuilder().setMerchantId(200).setYear(2024).setMonth(1).build();

                when(transactionMethodByMerchantService.findMonthlyMethodByMerchantSuccess(any()))
                                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

                StatusRuntimeException ex = null;
                try {
                        handler.getMonthlyTransactionMethodByMerchantSuccess(request).await().indefinitely();
                } catch (StatusRuntimeException e) {
                        ex = e;
                }

                assertThat(ex).isNotNull();
        }

        @Test
        @DisplayName("getYearlyTransactionMethodByMerchantSuccess - should return response on success")
        void getYearlyTransactionMethodByMerchantSuccess_Success() {
                TransactionMethod.YearMethodTransactionMerchantRequest request = TransactionMethod.YearMethodTransactionMerchantRequest
                                .newBuilder().setMerchantId(200).setYear(2024).build();

                TransactionYearlyMethodResponse data = TransactionYearlyMethodResponse.builder()
                                .year("2024").paymentMethod("Credit Card").totalTransactions(100L).totalAmount(1000000L)
                                .build();
                ApiResponse<java.util.List<TransactionYearlyMethodResponse>> apiResp = ApiResponse
                                .success("Yearly method success for merchant retrieved successfully",
                                                java.util.List.of(data));

                when(transactionMethodByMerchantService.findYearlyMethodByMerchantSuccess(any()))
                                .thenReturn(Uni.createFrom().item(apiResp));

                TransactionCommon.ApiResponseTransactionYearPaymentmethod response = handler
                                .getYearlyTransactionMethodByMerchantSuccess(request).await().indefinitely();

                assertThat(response).isNotNull();
                assertThat(response.getStatus()).isEqualTo("success");
                assertThat(response.getDataCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("getMonthlyTransactionMethodByMerchantFailed - should return response on success")
        void getMonthlyTransactionMethodByMerchantFailed_Success() {
                TransactionMethod.MonthMethodTransactionMerchantRequest request = TransactionMethod.MonthMethodTransactionMerchantRequest
                                .newBuilder().setMerchantId(200).setYear(2024).setMonth(1).build();

                TransactionMonthlyMethodResponse data = TransactionMonthlyMethodResponse.builder()
                                .month("Jan").paymentMethod("Debit Card").totalTransactions(3L).totalAmount(30000L)
                                .build();
                ApiResponse<java.util.List<TransactionMonthlyMethodResponse>> apiResp = ApiResponse
                                .success("Monthly method failed for merchant retrieved successfully",
                                                java.util.List.of(data));

                when(transactionMethodByMerchantService.findMonthlyMethodByMerchantFailed(any()))
                                .thenReturn(Uni.createFrom().item(apiResp));

                TransactionCommon.ApiResponseTransactionMonthPaymentMethod response = handler
                                .getMonthlyTransactionMethodByMerchantFailed(request).await().indefinitely();

                assertThat(response).isNotNull();
                assertThat(response.getStatus()).isEqualTo("success");
        }

        @Test
        @DisplayName("getYearlyTransactionMethodByMerchantFailed - should return response on success")
        void getYearlyTransactionMethodByMerchantFailed_Success() {
                TransactionMethod.YearMethodTransactionMerchantRequest request = TransactionMethod.YearMethodTransactionMerchantRequest
                                .newBuilder().setMerchantId(200).setYear(2024).build();

                TransactionYearlyMethodResponse data = TransactionYearlyMethodResponse.builder()
                                .year("2024").paymentMethod("Debit Card").totalTransactions(20L).totalAmount(200000L)
                                .build();
                ApiResponse<java.util.List<TransactionYearlyMethodResponse>> apiResp = ApiResponse
                                .success("Yearly method failed for merchant retrieved successfully",
                                                java.util.List.of(data));

                when(transactionMethodByMerchantService.findYearlyMethodByMerchantFailed(any()))
                                .thenReturn(Uni.createFrom().item(apiResp));

                TransactionCommon.ApiResponseTransactionYearPaymentmethod response = handler
                                .getYearlyTransactionMethodByMerchantFailed(request).await().indefinitely();

                assertThat(response).isNotNull();
                assertThat(response.getStatus()).isEqualTo("success");
        }
}
