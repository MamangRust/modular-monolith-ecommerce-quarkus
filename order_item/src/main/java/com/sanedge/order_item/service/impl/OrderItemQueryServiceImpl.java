package com.sanedge.order_item.service.impl;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.domain.response.ApiResponsePagination;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.common.domain.response.PaginationMeta;
import com.sanedge.order_item.domain.requests.FindAllOrderItemRequest;
import com.sanedge.order_item.domain.response.OrderItemResponse;
import com.sanedge.order_item.domain.response.OrderItemResponseDeleteAt;
import com.sanedge.order_item.repository.OrderItemRepository;
import com.sanedge.order_item.service.OrderItemQueryService;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OrderItemQueryServiceImpl implements OrderItemQueryService {
    private static final Logger logger = LoggerFactory.getLogger(OrderItemQueryServiceImpl.class);

    private final OrderItemRepository orderItemRepository;
    private final Tracer tracer;

    @Inject
    public OrderItemQueryServiceImpl(OrderItemRepository orderItemRepository, OpenTelemetry openTelemetry) {
        this.orderItemRepository = orderItemRepository;
        this.tracer = openTelemetry.getTracer("order-item-query-service", "1.0.0");
    }

    @Override
    public Uni<ApiResponse<List<OrderItemResponse>>> findAll(FindAllOrderItemRequest request) {
        Span span = tracer.spanBuilder("findAllOrderItems").setSpanKind(SpanKind.SERVER).startSpan();
        return orderItemRepository.findOrderItems(request)
                .map(paged -> {
                    List<OrderItemResponse> responses = paged.getData().stream()
                            .map(OrderItemResponse::from)
                            .collect(Collectors.toList());
                    span.setStatus(StatusCode.OK);
                    return ApiResponse.success("Order items retrieved successfully", responses);
                })
                .onFailure().invoke(e -> {
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                })
                .eventually(() -> span.end());
    }

    @Override
    public Uni<ApiResponse<List<OrderItemResponseDeleteAt>>> findActive(FindAllOrderItemRequest request) {
        Span span = tracer.spanBuilder("findActiveOrderItems").setSpanKind(SpanKind.SERVER).startSpan();
        return orderItemRepository.findActiveOrderItems(request)
                .map(paged -> {
                    List<OrderItemResponseDeleteAt> responses = paged.getData().stream()
                            .map(OrderItemResponseDeleteAt::from)
                            .collect(Collectors.toList());
                    span.setStatus(StatusCode.OK);
                    return ApiResponse.success("Active order items retrieved successfully", responses);
                })
                .onFailure().invoke(e -> {
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                })
                .eventually(() -> span.end());
    }

    @Override
    public Uni<ApiResponse<List<OrderItemResponseDeleteAt>>> findTrashed(FindAllOrderItemRequest request) {
        Span span = tracer.spanBuilder("findTrashedOrderItems").setSpanKind(SpanKind.SERVER).startSpan();
        return orderItemRepository.findTrashedOrderItems(request)
                .map(paged -> {
                    List<OrderItemResponseDeleteAt> responses = paged.getData().stream()
                            .map(OrderItemResponseDeleteAt::from)
                            .collect(Collectors.toList());
                    span.setStatus(StatusCode.OK);
                    return ApiResponse.success("Trashed order items retrieved successfully", responses);
                })
                .onFailure().invoke(e -> {
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                })
                .eventually(() -> span.end());
    }

    @Override
    public Uni<ApiResponse<List<OrderItemResponse>>> findByOrder(Long orderId) {
        Span span = tracer.spanBuilder("findOrderItemByOrder").setSpanKind(SpanKind.SERVER).startSpan();
        return orderItemRepository.findOrderItemByOrder(orderId)
                .map(items -> {
                    List<OrderItemResponse> responses = items.stream()
                            .map(OrderItemResponse::from)
                            .collect(Collectors.toList());
                    span.setStatus(StatusCode.OK);
                    return ApiResponse.success("Order items for order retrieved successfully", responses);
                })
                .onFailure().invoke(e -> {
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                })
                .eventually(() -> span.end());
    }
}
