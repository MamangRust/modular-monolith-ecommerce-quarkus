package com.sanedge.transaction.service.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import com.sanedge.transaction.domain.requests.MonthMethodTransactionRequest;
import com.sanedge.transaction.domain.response.TransactionMonthlyMethodResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyMethodResponse;
import com.sanedge.transaction.entity.TransactionMonthlyMethod;
import com.sanedge.transaction.entity.TransactionYearlyMethod;
import com.sanedge.transaction.repository.stats.TransactionMethodRepository;
import com.sanedge.transaction.service.impl.TransactionMethodServiceImpl;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class TransactionMethodServiceTest {

    @Mock
    private TransactionMethodRepository repository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private TransactionMethodServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new TransactionMethodServiceImpl(repository, redisService, objectMapper, tracingMetrics);

        lenient().when(tracingMetrics.traceAndMeasure(anyString(), anyString(), any(Attributes.class), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<Uni<?>>) inv.getArgument(3)).get());
        lenient().when(tracingMetrics.traceAndMeasure(anyString(), anyString(), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<Uni<?>>) inv.getArgument(2)).get());
    }

    // ========== findMonthlyMethodSuccess Tests ==========

    @Test
    @DisplayName("findMonthlyMethodSuccess - null year returns error")
    void findMonthlyMethodSuccess_nullYear_returnsError() {
        MonthMethodTransactionRequest req = new MonthMethodTransactionRequest();
        req.setMonth(1);

        ApiResponse<List<TransactionMonthlyMethodResponse>> response = service.findMonthlyMethodSuccess(req)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
    }

    @Test
    @DisplayName("findMonthlyMethodSuccess - invalid month returns error")
    void findMonthlyMethodSuccess_invalidMonth_returnsError() {
        MonthMethodTransactionRequest req = new MonthMethodTransactionRequest();
        req.setYear(2024);
        req.setMonth(13);

        ApiResponse<List<TransactionMonthlyMethodResponse>> response = service.findMonthlyMethodSuccess(req)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
    }

    @Test
    @DisplayName("findMonthlyMethodSuccess - cache miss returns from DB")
    void findMonthlyMethodSuccess_cacheMiss_returnsFromDb() {
        MonthMethodTransactionRequest req = new MonthMethodTransactionRequest();
        req.setYear(2024);
        req.setMonth(1);

        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(repository.findMonthlyMethodsSuccess(2024, 1))
                .thenReturn(Uni.createFrom().item(List.of(
                        new TransactionMonthlyMethod("Jan", "Credit Card", 10L, 100000L))));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponse<List<TransactionMonthlyMethodResponse>> response = service.findMonthlyMethodSuccess(req)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).hasSize(1);
    }

    // ========== findYearlyMethodSuccess Tests ==========

    @Test
    @DisplayName("findYearlyMethodSuccess - null year returns error")
    void findYearlyMethodSuccess_nullYear_returnsError() {
        ApiResponse<List<TransactionYearlyMethodResponse>> response = service.findYearlyMethodSuccess(null)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
    }

    @Test
    @DisplayName("findYearlyMethodSuccess - cache miss returns from DB")
    void findYearlyMethodSuccess_cacheMiss_returnsFromDb() {
        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(repository.findYearlyMethodsSuccess(2024))
                .thenReturn(Uni.createFrom().item(List.of(
                        new TransactionYearlyMethod("2024", "Credit Card", 100L, 1000000L))));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponse<List<TransactionYearlyMethodResponse>> response = service.findYearlyMethodSuccess(2024)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).hasSize(1);
    }

    // ========== findMonthlyMethodFailed Tests ==========

    @Test
    @DisplayName("findMonthlyMethodFailed - cache miss returns from DB")
    void findMonthlyMethodFailed_cacheMiss_returnsFromDb() {
        MonthMethodTransactionRequest req = new MonthMethodTransactionRequest();
        req.setYear(2024);
        req.setMonth(1);

        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(repository.findMonthlyMethodsFailed(2024, 1))
                .thenReturn(Uni.createFrom().item(List.of(
                        new TransactionMonthlyMethod("Jan", "Debit Card", 3L, 30000L))));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponse<List<TransactionMonthlyMethodResponse>> response = service.findMonthlyMethodFailed(req)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).hasSize(1);
    }

    // ========== findYearlyMethodFailed Tests ==========

    @Test
    @DisplayName("findYearlyMethodFailed - cache miss returns from DB")
    void findYearlyMethodFailed_cacheMiss_returnsFromDb() {
        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(repository.findYearlyMethodsFailed(2024))
                .thenReturn(Uni.createFrom().item(List.of(
                        new TransactionYearlyMethod("2024", "Debit Card", 20L, 200000L))));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponse<List<TransactionYearlyMethodResponse>> response = service.findYearlyMethodFailed(2024)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).hasSize(1);
    }
}
