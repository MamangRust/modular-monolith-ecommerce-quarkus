package com.sanedge.transaction.service.impl.statsbymerchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.transaction.domain.requests.FindMonthlyAmountByMerchantRequest;
import com.sanedge.transaction.domain.requests.YearAmountTransactionMerchant;
import com.sanedge.transaction.domain.response.TransactionMonthlyAmountFailedResponse;
import com.sanedge.transaction.domain.response.TransactionMonthlyAmountSuccessResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyAmountFailedResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyAmountSuccessResponse;
import com.sanedge.transaction.entity.TransactionMonthlyAmountFailed;
import com.sanedge.transaction.entity.TransactionMonthlyAmountSuccess;
import com.sanedge.transaction.entity.TransactionYearlyAmountFailed;
import com.sanedge.transaction.entity.TransactionYearlyAmountSuccess;
import com.sanedge.transaction.repository.statsbymerchant.TransactionAmountByMerchantRepository;
import com.sanedge.transaction.service.impl.TransactionAmountByMerchantServiceImpl;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class TransactionAmountByMerchantServiceTest {

        @Mock
        private TransactionAmountByMerchantRepository repository;

        @Mock
        private RedisService redisService;

        @Mock
        private TracingMetrics tracingMetrics;

        private TransactionAmountByMerchantServiceImpl service;
        private final ObjectMapper objectMapper = new ObjectMapper();

        @BeforeEach
        void setUp() {
                service = new TransactionAmountByMerchantServiceImpl(repository, redisService, objectMapper,
                                tracingMetrics);

                lenient().doAnswer(invocation -> {
                        Supplier<Uni<?>> supplier = invocation.getArgument(3);
                        return supplier.get();
                }).when(tracingMetrics)
                                .traceAndMeasure(
                                                anyString(),
                                                anyString(),
                                                any(Attributes.class),
                                                any());
        }

        @Test
        @DisplayName("findMonthlyAmountSuccessByMerchant - null merchantId returns error")
        void findMonthlyAmountSuccessByMerchant_nullMerchantId_returnsError() {
                FindMonthlyAmountByMerchantRequest req = new FindMonthlyAmountByMerchantRequest();
                req.setYear(2024);
                req.setMonth(1);

                ApiResponse<List<TransactionMonthlyAmountSuccessResponse>> response = service
                                .findMonthlyAmountSuccessByMerchant(req).await().indefinitely();

                assertThat(response.status()).isEqualTo("error");
        }

        @Test
        @DisplayName("findMonthlyAmountSuccessByMerchant - cache miss returns from DB")
        void findMonthlyAmountSuccessByMerchant_cacheMiss_returnsFromDb() {
                FindMonthlyAmountByMerchantRequest req = new FindMonthlyAmountByMerchantRequest();
                req.setMerchantId(200L);
                req.setYear(2024);
                req.setMonth(1);

                when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
                when(repository.findMonthlySuccessByMerchant(req))
                                .thenReturn(Uni.createFrom().item(List.of(
                                                new TransactionMonthlyAmountSuccess("2024", "Jan", 10L, 100000L))));
                when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                                .thenReturn(Uni.createFrom().voidItem());

                ApiResponse<List<TransactionMonthlyAmountSuccessResponse>> response = service
                                .findMonthlyAmountSuccessByMerchant(req).await().indefinitely();

                assertThat(response.status()).isEqualTo("success");
                assertThat(response.data()).hasSize(1);
        }

        @Test
        @DisplayName("findYearlyAmountSuccessByMerchant - null merchantId returns error")
        void findYearlyAmountSuccessByMerchant_nullMerchantId_returnsError() {
                YearAmountTransactionMerchant req = new YearAmountTransactionMerchant();
                req.setYear(2024);

                ApiResponse<List<TransactionYearlyAmountSuccessResponse>> response = service
                                .findYearlyAmountSuccessByMerchant(req).await().indefinitely();

                assertThat(response.status()).isEqualTo("error");
        }

        @Test
        @DisplayName("findYearlyAmountSuccessByMerchant - cache miss returns from DB")
        void findYearlyAmountSuccessByMerchant_cacheMiss_returnsFromDb() {
                YearAmountTransactionMerchant req = new YearAmountTransactionMerchant();
                req.setMerchantId(200);
                req.setYear(2024);

                when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
                when(repository.findYearlySuccessByMerchant(200L, 2024))
                                .thenReturn(Uni.createFrom().item(List.of(
                                                new TransactionYearlyAmountSuccess("2024", 100L, 1000000L))));
                when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                                .thenReturn(Uni.createFrom().voidItem());

                ApiResponse<List<TransactionYearlyAmountSuccessResponse>> response = service
                                .findYearlyAmountSuccessByMerchant(req).await().indefinitely();

                assertThat(response.status()).isEqualTo("success");
                assertThat(response.data()).hasSize(1);
        }

        @Test
        @DisplayName("findMonthlyAmountFailedByMerchant - cache miss returns from DB")
        void findMonthlyAmountFailedByMerchant_cacheMiss_returnsFromDb() {
                FindMonthlyAmountByMerchantRequest req = new FindMonthlyAmountByMerchantRequest();
                req.setMerchantId(200L);
                req.setYear(2024);
                req.setMonth(1);

                when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
                when(repository.findMonthlyFailedByMerchant(req))
                                .thenReturn(Uni.createFrom().item(List.of(
                                                new TransactionMonthlyAmountFailed("2024", "Jan", 5L, 50000L))));
                when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                                .thenReturn(Uni.createFrom().voidItem());

                ApiResponse<List<TransactionMonthlyAmountFailedResponse>> response = service
                                .findMonthlyAmountFailedByMerchant(req).await().indefinitely();

                assertThat(response.status()).isEqualTo("success");
                assertThat(response.data()).hasSize(1);
        }

        @Test
        @DisplayName("findYearlyAmountFailedByMerchant - cache miss returns from DB")
        void findYearlyAmountFailedByMerchant_cacheMiss_returnsFromDb() {
                YearAmountTransactionMerchant req = new YearAmountTransactionMerchant();
                req.setMerchantId(200);
                req.setYear(2024);

                when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
                when(repository.findYearlyFailedByMerchant(200L, 2024))
                                .thenReturn(Uni.createFrom().item(List.of(
                                                new TransactionYearlyAmountFailed("2024", 50L, 500000L))));
                when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                                .thenReturn(Uni.createFrom().voidItem());

                ApiResponse<List<TransactionYearlyAmountFailedResponse>> response = service
                                .findYearlyAmountFailedByMerchant(req).await().indefinitely();

                assertThat(response.status()).isEqualTo("success");
                assertThat(response.data()).hasSize(1);
        }
}
