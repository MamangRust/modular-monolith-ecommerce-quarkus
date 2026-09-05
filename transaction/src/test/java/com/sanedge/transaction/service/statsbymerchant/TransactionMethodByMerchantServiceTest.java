package com.sanedge.transaction.service.statsbymerchant;

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
import com.sanedge.transaction.domain.requests.FindMonthlyMethodsByMerchantRequest;
import com.sanedge.transaction.domain.requests.YearMethodTransactionMerchantRequest;
import com.sanedge.transaction.domain.response.TransactionMonthlyMethodResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyMethodResponse;
import com.sanedge.transaction.entity.TransactionMonthlyMethod;
import com.sanedge.transaction.entity.TransactionYearlyMethod;
import com.sanedge.transaction.repository.statsbymerchant.TransactionMethodByMerchantRepository;
import com.sanedge.transaction.service.impl.TransactionMethodByMerchantServiceImpl;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class TransactionMethodByMerchantServiceTest {

    @Mock
    private TransactionMethodByMerchantRepository repository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private TransactionMethodByMerchantServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new TransactionMethodByMerchantServiceImpl(repository, redisService, objectMapper, tracingMetrics);

        lenient().when(tracingMetrics.traceAndMeasure(anyString(), anyString(), any(Attributes.class), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<Uni<?>>) inv.getArgument(3)).get());
        lenient().when(tracingMetrics.traceAndMeasure(anyString(), anyString(), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<Uni<?>>) inv.getArgument(2)).get());
    }

    // ========== findMonthlyMethodByMerchantSuccess Tests ==========

    @Test
    @DisplayName("findMonthlyMethodByMerchantSuccess - null merchantId returns error")
    void findMonthlyMethodByMerchantSuccess_nullMerchantId_returnsError() {
        FindMonthlyMethodsByMerchantRequest req = new FindMonthlyMethodsByMerchantRequest();
        req.setYear(2024);
        req.setMonth(1);

        ApiResponse<List<TransactionMonthlyMethodResponse>> response = service
                .findMonthlyMethodByMerchantSuccess(req).await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
    }

    @Test
    @DisplayName("findMonthlyMethodByMerchantSuccess - invalid month returns error")
    void findMonthlyMethodByMerchantSuccess_invalidMonth_returnsError() {
        FindMonthlyMethodsByMerchantRequest req = new FindMonthlyMethodsByMerchantRequest();
        req.setMerchantId(200L);
        req.setYear(2024);
        req.setMonth(13);

        ApiResponse<List<TransactionMonthlyMethodResponse>> response = service
                .findMonthlyMethodByMerchantSuccess(req).await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
    }

    @Test
    @DisplayName("findMonthlyMethodByMerchantSuccess - cache miss returns from DB")
    void findMonthlyMethodByMerchantSuccess_cacheMiss_returnsFromDb() {
        FindMonthlyMethodsByMerchantRequest req = new FindMonthlyMethodsByMerchantRequest();
        req.setMerchantId(200L);
        req.setYear(2024);
        req.setMonth(1);

        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(repository.findMonthlyTransactionMethodsSuccessByMerchant(req))
                .thenReturn(Uni.createFrom().item(List.of(
                        new TransactionMonthlyMethod("Jan", "Credit Card", 10L, 100000L))));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponse<List<TransactionMonthlyMethodResponse>> response = service
                .findMonthlyMethodByMerchantSuccess(req).await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).hasSize(1);
    }

    // ========== findMonthlyMethodByMerchantFailed Tests ==========

    @Test
    @DisplayName("findMonthlyMethodByMerchantFailed - cache miss returns from DB")
    void findMonthlyMethodByMerchantFailed_cacheMiss_returnsFromDb() {
        FindMonthlyMethodsByMerchantRequest req = new FindMonthlyMethodsByMerchantRequest();
        req.setMerchantId(200L);
        req.setYear(2024);
        req.setMonth(1);

        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(repository.findMonthlyTransactionMethodsFailedByMerchant(req))
                .thenReturn(Uni.createFrom().item(List.of(
                        new TransactionMonthlyMethod("Jan", "Debit Card", 3L, 30000L))));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponse<List<TransactionMonthlyMethodResponse>> response = service
                .findMonthlyMethodByMerchantFailed(req).await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).hasSize(1);
    }

    // ========== findYearlyMethodByMerchantSuccess Tests ==========

    @Test
    @DisplayName("findYearlyMethodByMerchantSuccess - null merchantId returns error")
    void findYearlyMethodByMerchantSuccess_nullMerchantId_returnsError() {
        YearMethodTransactionMerchantRequest req = new YearMethodTransactionMerchantRequest();
        req.setYear(2024);

        ApiResponse<List<TransactionYearlyMethodResponse>> response = service
                .findYearlyMethodByMerchantSuccess(req).await().indefinitely();

        assertThat(response.status()).isEqualTo("error");
    }

    @Test
    @DisplayName("findYearlyMethodByMerchantSuccess - cache miss returns from DB")
    void findYearlyMethodByMerchantSuccess_cacheMiss_returnsFromDb() {
        YearMethodTransactionMerchantRequest req = new YearMethodTransactionMerchantRequest();
        req.setMerchantId(200);
        req.setYear(2024);

        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(repository.findYearlyTransactionMethodsSuccessByMerchant(200L, 2024))
                .thenReturn(Uni.createFrom().item(List.of(
                        new TransactionYearlyMethod("2024", "Credit Card", 100L, 1000000L))));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponse<List<TransactionYearlyMethodResponse>> response = service
                .findYearlyMethodByMerchantSuccess(req).await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).hasSize(1);
    }

    // ========== findYearlyMethodByMerchantFailed Tests ==========

    @Test
    @DisplayName("findYearlyMethodByMerchantFailed - cache miss returns from DB")
    void findYearlyMethodByMerchantFailed_cacheMiss_returnsFromDb() {
        YearMethodTransactionMerchantRequest req = new YearMethodTransactionMerchantRequest();
        req.setMerchantId(200);
        req.setYear(2024);

        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(repository.findYearlyTransactionMethodsFailedByMerchant(200L, 2024))
                .thenReturn(Uni.createFrom().item(List.of(
                        new TransactionYearlyMethod("2024", "Debit Card", 20L, 200000L))));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponse<List<TransactionYearlyMethodResponse>> response = service
                .findYearlyMethodByMerchantFailed(req).await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).hasSize(1);
    }
}
