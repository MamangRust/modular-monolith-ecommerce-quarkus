package com.sanedge.order.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.domain.response.ApiResponsePagination;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.order.domain.requests.FindAllOrderByMerchantRequest;
import com.sanedge.order.domain.requests.FindAllOrderRequest;
import com.sanedge.order.domain.response.OrderResponse;
import com.sanedge.order.domain.response.OrderResponseDeleteAt;
import com.sanedge.order.entity.Order;
import com.sanedge.order.repository.OrderQueryRepository;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceImplTest {

    @Mock
    private OrderQueryRepository orderQueryRepository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private OrderQueryServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new OrderQueryServiceImpl(orderQueryRepository, redisService, objectMapper, tracingMetrics);
        lenient()
                .when(tracingMetrics.traceAndMeasure(anyString(), anyString(), any(Attributes.class),
                        any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(3);
                    return supplier.get();
                });
        lenient()
                .when(tracingMetrics.traceAndMeasure(anyString(), anyString(), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    return supplier.get();
                });
    }

    private Order createMockOrder(Long id) {
        Order o = new Order();
        o.id = id;
        o.setMerchantId(100);
        o.setUserId(100);
        o.setTotalPrice(500);
        return o;
    }

    // ========== findAll Tests ==========

    @Test
    void findAll_cacheMiss_returnsFromDb() {
        FindAllOrderRequest req = new FindAllOrderRequest();
        req.setPage(1);
        req.setPageSize(10);
        PagedResult<Order> paged = new PagedResult<>(List.of(createMockOrder(1L)), 1);
        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(orderQueryRepository.findOrders(any())).thenReturn(Uni.createFrom().item(paged));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponsePagination<List<OrderResponse>> result = service.findAll(req).await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.data()).hasSize(1);
    }

    // ========== findById Tests ==========

    @Test
    void findById_cacheMiss_returnsFromDb() {
        Order o = createMockOrder(1L);
        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(orderQueryRepository.findOrderById(anyLong())).thenReturn(Uni.createFrom().item(Optional.of(o)));
        when(redisService.setReactive(anyString(), anyString())).thenReturn(Uni.createFrom().voidItem());

        ApiResponse<OrderResponse> result = service.findById(1L).await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.data().getId()).isEqualTo(1L);
    }

    @Test
    void findById_notFound_throwsResourceNotFoundException() {
        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(orderQueryRepository.findOrderById(anyLong())).thenReturn(Uni.createFrom().item(Optional.empty()));

        try {
            service.findById(999L).await().indefinitely();
            Assertions.fail("Expected ResourceNotFoundException");
        } catch (ResourceNotFoundException e) {
            assertThat(e.getMessage()).contains("Order not found");
        }
    }

    // ========== findByActive Tests ==========

    @Test
    void findByActive_cacheMiss_returnsFromDb() {
        FindAllOrderRequest req = new FindAllOrderRequest();
        req.setPage(1);
        req.setPageSize(10);
        PagedResult<Order> paged = new PagedResult<>(List.of(createMockOrder(1L)), 1);
        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(orderQueryRepository.findActiveOrders(any())).thenReturn(Uni.createFrom().item(paged));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponsePagination<List<OrderResponseDeleteAt>> result = service.findByActive(req).await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.data()).hasSize(1);
    }

    // ========== findByTrashed Tests ==========

    @Test
    void findByTrashed_cacheMiss_returnsFromDb() {
        FindAllOrderRequest req = new FindAllOrderRequest();
        req.setPage(1);
        req.setPageSize(10);
        PagedResult<Order> paged = new PagedResult<>(List.of(createMockOrder(1L)), 1);
        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(orderQueryRepository.findTrashedOrders(any())).thenReturn(Uni.createFrom().item(paged));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponsePagination<List<OrderResponseDeleteAt>> result = service.findByTrashed(req).await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
    }

    // ========== findByMerchantId Tests ==========

    @Test
    void findByMerchantId_cacheMiss_returnsFromDb() {
        FindAllOrderByMerchantRequest req = new FindAllOrderByMerchantRequest();
        req.setMerchantId(1);
        req.setPage(1);
        req.setPageSize(10);
        PagedResult<Order> paged = new PagedResult<>(List.of(createMockOrder(1L)), 1);
        when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(orderQueryRepository.findOrdersByMerchant(any())).thenReturn(Uni.createFrom().item(paged));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponsePagination<List<OrderResponse>> result = service.findByMerchantId(req).await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
    }
}
