package com.sanedge.transaction.service.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
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
import com.sanedge.transaction.domain.requests.MonthAmountTransactionRequest;
import com.sanedge.transaction.domain.response.TransactionMonthlyAmountFailedResponse;
import com.sanedge.transaction.domain.response.TransactionMonthlyAmountSuccessResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyAmountFailedResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyAmountSuccessResponse;
import com.sanedge.transaction.entity.TransactionMonthlyAmountFailed;
import com.sanedge.transaction.entity.TransactionMonthlyAmountSuccess;
import com.sanedge.transaction.entity.TransactionYearlyAmountFailed;
import com.sanedge.transaction.entity.TransactionYearlyAmountSuccess;
import com.sanedge.transaction.repository.stats.TransactionAmountStatusRepository;
import com.sanedge.transaction.service.impl.TransactionAmountServiceImpl;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class TransactionAmountServiceTest {

    @Mock
    private TransactionAmountStatusRepository repository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private TransactionAmountServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new TransactionAmountServiceImpl(repository, redisService, objectMapper, tracingMetrics);

        lenient().when(tracingMetrics.traceAndMeasure(anyString(), anyString(), any(Attributes.class), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<Uni<?>>) inv.getArgument(3)).get());
        lenient().when(tracingMetrics.traceAndMeasure(anyString(), anyString(), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<Uni<?>>) inv.getArgument(2)).get());
    }

    // ========== findMonthlyAmountSuccess Tests ==========

    @Test
    @DisplayName("findMonthlyAmountSuccess - null year returns error")
    void findMonthlyAmountSuccess_nullYear_returnsError() {
        MonthAmountTransactionRequest req = new MonthAmountTransactionRequest();
        req.setMonth(1);

        ApiResponse<List<TransactionMonthlyAmountSuccessResponse>> response = service.findMonthlyAmountSuccess(req)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
    }

    @Test
    @DisplayName("findMonthlyAmountSuccess - null month returns error")
    void findMonthlyAmountSuccess_nullMonth_returnsError() {
        MonthAmountTransactionRequest req = new MonthAmountTransactionRequest();
        req.setYear(2024);

        ApiResponse<List<TransactionMonthlyAmountSuccessResponse>> response = service.findMonthlyAmountSuccess(req)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
    }

    @Test
    @DisplayName("findMonthlyAmountSuccess - invalid month returns error")
    void findMonthlyAmountSuccess_invalidMonth_returnsError() {
        MonthAmountTransactionRequest req = new MonthAmountTransactionRequest();
        req.setYear(2024);
        req.setMonth(13);

        ApiResponse<List<TransactionMonthlyAmountSuccessResponse>> response = service.findMonthlyAmountSuccess(req)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
    }

    @Test
    @DisplayName("findMonthlyAmountSuccess - cache miss returns from DB")
    void findMonthlyAmountSuccess_cacheMiss_returnsFromDb() {
        MonthAmountTransactionRequest req = new MonthAmountTransactionRequest();
        req.setYear(2024);
        req.setMonth(1);

        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(repository.findMonthlyTransactionSuccess(2024, 1))
                .thenReturn(Uni.createFrom().item(List.of(
                        new TransactionMonthlyAmountSuccess("2024", "Jan", 10L, 100000L))));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponse<List<TransactionMonthlyAmountSuccessResponse>> response = service.findMonthlyAmountSuccess(req)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).getMonth()).isEqualTo("Jan");
    }

    // ========== findYearlyAmountSuccess Tests ==========

    @Test
    @DisplayName("findYearlyAmountSuccess - null year returns error")
    void findYearlyAmountSuccess_nullYear_returnsError() {
        ApiResponse<List<TransactionYearlyAmountSuccessResponse>> response = service.findYearlyAmountSuccess(null)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
    }

    @Test
    @DisplayName("findYearlyAmountSuccess - cache miss returns from DB")
    void findYearlyAmountSuccess_cacheMiss_returnsFromDb() {
        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(repository.findYearlyTransactionSuccess(2024))
                .thenReturn(Uni.createFrom().item(List.of(
                        new TransactionYearlyAmountSuccess("2024", 100L, 1000000L))));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponse<List<TransactionYearlyAmountSuccessResponse>> response = service.findYearlyAmountSuccess(2024)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).hasSize(1);
    }

    // ========== findMonthlyAmountFailed Tests ==========

    @Test
    @DisplayName("findMonthlyAmountFailed - null year returns error")
    void findMonthlyAmountFailed_nullYear_returnsError() {
        MonthAmountTransactionRequest req = new MonthAmountTransactionRequest();
        req.setMonth(1);

        ApiResponse<List<TransactionMonthlyAmountFailedResponse>> response = service.findMonthlyAmountFailed(req)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
    }

    @Test
    @DisplayName("findMonthlyAmountFailed - cache miss returns from DB")
    void findMonthlyAmountFailed_cacheMiss_returnsFromDb() {
        MonthAmountTransactionRequest req = new MonthAmountTransactionRequest();
        req.setYear(2024);
        req.setMonth(1);

        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(repository.findMonthlyTransactionFailed(2024, 1))
                .thenReturn(Uni.createFrom().item(List.of(
                        new TransactionMonthlyAmountFailed("2024", "Jan", 5L, 50000L))));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponse<List<TransactionMonthlyAmountFailedResponse>> response = service.findMonthlyAmountFailed(req)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).hasSize(1);
    }

    // ========== findYearlyAmountFailed Tests ==========

    @Test
    @DisplayName("findYearlyAmountFailed - null year returns error")
    void findYearlyAmountFailed_nullYear_returnsError() {
        ApiResponse<List<TransactionYearlyAmountFailedResponse>> response = service.findYearlyAmountFailed(null)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
    }

    @Test
    @DisplayName("findYearlyAmountFailed - cache miss returns from DB")
    void findYearlyAmountFailed_cacheMiss_returnsFromDb() {
        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(repository.findYearlyTransactionFailed(2024))
                .thenReturn(Uni.createFrom().item(List.of(
                        new TransactionYearlyAmountFailed("2024", 50L, 500000L))));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponse<List<TransactionYearlyAmountFailedResponse>> response = service.findYearlyAmountFailed(2024)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).hasSize(1);
    }
}
