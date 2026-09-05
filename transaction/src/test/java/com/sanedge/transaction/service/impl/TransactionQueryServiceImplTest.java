package com.sanedge.transaction.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.domain.response.ApiResponsePagination;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.transaction.domain.requests.FindAllTransactionByMerchantRequest;
import com.sanedge.transaction.domain.requests.FindAllTransactionRequest;
import com.sanedge.transaction.domain.response.TransactionResponse;
import com.sanedge.transaction.domain.response.TransactionResponseDeleteAt;
import com.sanedge.transaction.entity.Transaction;
import com.sanedge.transaction.enums.PaymentStatus;
import com.sanedge.transaction.repository.TransactionQueryRepository;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class TransactionQueryServiceImplTest {

    @Mock
    private TransactionQueryRepository transactionQueryRepository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private TransactionQueryServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new TransactionQueryServiceImpl(
                transactionQueryRepository,
                redisService,
                tracingMetrics,
                objectMapper);

        lenient().when(tracingMetrics.traceAndMeasure(anyString(), anyString(), any(Attributes.class), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<Uni<?>>) inv.getArgument(3)).get());
        lenient().when(tracingMetrics.traceAndMeasure(anyString(), anyString(), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<Uni<?>>) inv.getArgument(2)).get());
    }

    // ========== Helper Methods ==========

    private Transaction createMockTransaction(Long id) {
        Transaction t = new Transaction();
        try {
            Field idField = t.getClass().getSuperclass().getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(t, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        t.setOrderId(100);
        t.setMerchantId(200);
        t.setPaymentMethod("Credit Card");
        t.setAmount(110000);
        t.setStatus(PaymentStatus.SUCCESS);
        t.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
        t.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
        return t;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize in test helper", e);
        }
    }

    // ========== findAllTransactions Tests ==========

    @Nested
    @DisplayName("findAllTransactions tests")
    class FindAllTests {

        @Test
        @DisplayName("cache miss - should fetch from DB and cache result")
        void cacheMiss_fetchesFromDb() {
            FindAllTransactionRequest req = new FindAllTransactionRequest();
            req.setPage(1);
            req.setPageSize(10);

            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(transactionQueryRepository.findTransactions(any(FindAllTransactionRequest.class)))
                    .thenReturn(Uni.createFrom().item(new PagedResult<>(List.of(createMockTransaction(1L)), 1)));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                    .thenReturn(Uni.createFrom().voidItem());

            ApiResponsePagination<List<TransactionResponse>> result = service.findAllTransactions(req).await()
                    .indefinitely();

            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }

        @Test
        @DisplayName("cache hit - should return cached response without DB call")
        void cacheHit_returnsCached() {
            FindAllTransactionRequest req = new FindAllTransactionRequest();
            req.setPage(1);
            req.setPageSize(10);

            TransactionResponse cachedData = TransactionResponse.from(createMockTransaction(1L));
            ApiResponsePagination<List<TransactionResponse>> cachedResponse = new ApiResponsePagination<>(
                    "success", "Transactions retrieved successfully", List.of(cachedData), null);

            when(redisService.getReactive("transaction:all:1:10:"))
                    .thenReturn(Uni.createFrom().item(toJson(cachedResponse)));

            ApiResponsePagination<List<TransactionResponse>> result = service.findAllTransactions(req).await()
                    .indefinitely();

            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }
    }

    // ========== findByActive Tests ==========

    @Nested
    @DisplayName("findByActive tests")
    class FindByActiveTests {

        @Test
        @DisplayName("cache miss - should fetch active transactions from DB")
        void cacheMiss_fetchesFromDb() {
            FindAllTransactionRequest req = new FindAllTransactionRequest();
            req.setPage(1);
            req.setPageSize(10);

            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(transactionQueryRepository.findActiveTransactions(any(FindAllTransactionRequest.class)))
                    .thenReturn(Uni.createFrom().item(new PagedResult<>(List.of(createMockTransaction(1L)), 1)));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                    .thenReturn(Uni.createFrom().voidItem());

            ApiResponsePagination<List<TransactionResponseDeleteAt>> result = service.findByActive(req).await()
                    .indefinitely();

            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }
    }

    // ========== findByTrashed Tests ==========

    @Nested
    @DisplayName("findByTrashed tests")
    class FindByTrashedTests {

        @Test
        @DisplayName("cache miss - should fetch trashed transactions from DB")
        void cacheMiss_fetchesFromDb() {
            FindAllTransactionRequest req = new FindAllTransactionRequest();
            req.setPage(1);
            req.setPageSize(10);

            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(transactionQueryRepository.findTrashedTransactions(any(FindAllTransactionRequest.class)))
                    .thenReturn(Uni.createFrom().item(new PagedResult<>(List.of(createMockTransaction(1L)), 1)));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                    .thenReturn(Uni.createFrom().voidItem());

            ApiResponsePagination<List<TransactionResponseDeleteAt>> result = service.findByTrashed(req).await()
                    .indefinitely();

            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }
    }

    // ========== findByMerchant Tests ==========

    @Nested
    @DisplayName("findByMerchant tests")
    class FindByMerchantTests {

        @Test
        @DisplayName("cache miss - should fetch merchant transactions from DB")
        void cacheMiss_fetchesFromDb() {
            FindAllTransactionByMerchantRequest req = new FindAllTransactionByMerchantRequest();
            req.setPage(1);
            req.setPageSize(10);
            req.setMerchantId(200);

            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(transactionQueryRepository.findTransactionsByMerchant(any(FindAllTransactionByMerchantRequest.class)))
                    .thenReturn(Uni.createFrom().item(new PagedResult<>(List.of(), 0)));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                    .thenReturn(Uni.createFrom().voidItem());

            ApiResponsePagination<List<TransactionResponse>> result = service.findByMerchant(req).await()
                    .indefinitely();

            assertThat(result.status()).isEqualTo("success");
        }
    }

    // ========== findById Tests ==========

    @Nested
    @DisplayName("findById tests")
    class FindByIdTests {

        @Test
        @DisplayName("cache miss - should fetch transaction by id from DB")
        void cacheMiss_fetchesFromDb() {
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(transactionQueryRepository.findTransactionById(anyLong()))
                    .thenReturn(Uni.createFrom().item(Optional.of(createMockTransaction(1L))));
            when(redisService.setReactive(anyString(), anyString()))
                    .thenReturn(Uni.createFrom().voidItem());

            ApiResponse<TransactionResponse> result = service.findById(Integer.valueOf(1)).await().indefinitely();

            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data().getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("cache hit - should return cached without DB call")
        void cacheHit_returnsCached() {
            TransactionResponse cached = TransactionResponse.from(createMockTransaction(1L));

            when(redisService.getReactive("transaction:id:1"))
                    .thenReturn(Uni.createFrom().item(toJson(cached)));

            ApiResponse<TransactionResponse> result = service.findById(Integer.valueOf(1)).await().indefinitely();

            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data().getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should fail when transaction not found")
        void notFound_throwsException() {
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(transactionQueryRepository.findTransactionById(anyLong()))
                    .thenReturn(Uni.createFrom().item(Optional.empty()));

            assertThatThrownBy(() -> service.findById(Integer.valueOf(999)).await().indefinitely())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Transaction not found with ID: 999");
        }
    }

    // ========== findByOrderId Tests ==========

    @Nested
    @DisplayName("findByOrderId tests")
    class FindByOrderIdTests {

        @Test
        @DisplayName("cache miss - should fetch transaction by order id from DB")
        void cacheMiss_fetchesFromDb() {
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(transactionQueryRepository.findByOrderId(anyInt()))
                    .thenReturn(Uni.createFrom().item(Optional.of(createMockTransaction(1L))));
            when(redisService.setReactive(anyString(), anyString()))
                    .thenReturn(Uni.createFrom().voidItem());

            ApiResponse<TransactionResponse> result = service.findByOrderId(Integer.valueOf(100)).await().indefinitely();

            assertThat(result.status()).isEqualTo("success");
        }

        @Test
        @DisplayName("should fail when transaction not found by order id")
        void notFound_throwsException() {
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(transactionQueryRepository.findByOrderId(anyInt()))
                    .thenReturn(Uni.createFrom().item(Optional.empty()));

            assertThatThrownBy(() -> service.findByOrderId(Integer.valueOf(999)).await().indefinitely())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Transaction not found for order ID: 999");
        }
    }
}
