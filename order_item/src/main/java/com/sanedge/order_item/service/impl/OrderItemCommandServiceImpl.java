package com.sanedge.order_item.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.order_item.domain.requests.CreateOrderItemRequest;
import com.sanedge.order_item.domain.requests.UpdateOrderItemRequest;
import com.sanedge.order_item.domain.response.OrderItemResponse;
import com.sanedge.order_item.domain.response.OrderItemResponseDeleteAt;
import com.sanedge.order_item.entity.OrderItem;
import com.sanedge.order_item.repository.OrderItemRepository;
import com.sanedge.order_item.service.OrderItemCommandService;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OrderItemCommandServiceImpl implements OrderItemCommandService {
    private static final Logger logger = LoggerFactory.getLogger(OrderItemCommandServiceImpl.class);

    private final OrderItemRepository orderItemRepository;
    private final Tracer tracer;

    @Inject
    public OrderItemCommandServiceImpl(OrderItemRepository orderItemRepository, OpenTelemetry openTelemetry) {
        this.orderItemRepository = orderItemRepository;
        this.tracer = openTelemetry.getTracer("order-item-command-service", "1.0.0");
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<OrderItemResponse>> create(CreateOrderItemRequest request) {
        Span span = tracer.spanBuilder("createOrderItem").setSpanKind(SpanKind.SERVER).startSpan();
        logger.info("Creating order item: {}", request);

        OrderItem item = new OrderItem();
        item.setOrderId(request.getOrderId());
        item.setProductId(request.getProductId());
        item.setQuantity(request.getQuantity());
        item.setPrice(request.getPrice());

        return orderItemRepository.persist(item)
                .map(saved -> {
                    span.setStatus(StatusCode.OK);
                    return ApiResponse.success("Order item created successfully", OrderItemResponse.from(saved));
                })
                .onFailure().invoke(e -> {
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                })
                .eventually(() -> span.end());
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<OrderItemResponse>> update(UpdateOrderItemRequest request) {
        Span span = tracer.spanBuilder("updateOrderItem").setSpanKind(SpanKind.SERVER).startSpan();
        logger.info("Updating order item: {}", request);

        return orderItemRepository.findById(request.getOrderItemId().longValue())
                .chain(item -> {
                    if (item == null) {
                        throw new ResourceNotFoundException("Order item not found");
                    }
                    item.setQuantity(request.getQuantity());
                    item.setPrice(request.getPrice());
                    return orderItemRepository.persist(item);
                })
                .map(saved -> {
                    span.setStatus(StatusCode.OK);
                    return ApiResponse.success("Order item updated successfully", OrderItemResponse.from(saved));
                })
                .onFailure().invoke(e -> {
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                })
                .eventually(() -> span.end());
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<OrderItemResponseDeleteAt>> trash(Long id) {
        Span span = tracer.spanBuilder("trashOrderItem").setSpanKind(SpanKind.SERVER).startSpan();
        logger.info("Trashing order item: {}", id);

        return orderItemRepository.trashed(id)
                .map(item -> {
                    if (item == null) {
                        throw new ResourceNotFoundException("Order item not found");
                    }
                    span.setStatus(StatusCode.OK);
                    return ApiResponse.success("Order item trashed successfully", OrderItemResponseDeleteAt.from(item));
                })
                .onFailure().invoke(e -> {
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                })
                .eventually(() -> span.end());
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<OrderItemResponseDeleteAt>> restore(Long id) {
        Span span = tracer.spanBuilder("restoreOrderItem").setSpanKind(SpanKind.SERVER).startSpan();
        logger.info("Restoring order item: {}", id);

        return orderItemRepository.restore(id)
                .map(item -> {
                    if (item == null) {
                        throw new ResourceNotFoundException("Order item not found in trash");
                    }
                    span.setStatus(StatusCode.OK);
                    return ApiResponse.success("Order item restored successfully", OrderItemResponseDeleteAt.from(item));
                })
                .onFailure().invoke(e -> {
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                })
                .eventually(() -> span.end());
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deletePermanent(Long id) {
        Span span = tracer.spanBuilder("deleteOrderItemPermanent").setSpanKind(SpanKind.SERVER).startSpan();
        logger.info("Deleting permanently order item: {}", id);

        return orderItemRepository.deletePermanent(id)
                .map(res -> {
                    span.setStatus(StatusCode.OK);
                    return ApiResponse.success("Order item permanently deleted", res);
                })
                .onFailure().invoke(e -> {
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                })
                .eventually(() -> span.end());
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> restoreAll() {
        Span span = tracer.spanBuilder("restoreAllOrderItems").setSpanKind(SpanKind.SERVER).startSpan();
        return orderItemRepository.restoreAllDeleted()
                .map(res -> {
                    span.setStatus(StatusCode.OK);
                    return ApiResponse.success("All order items restored", res);
                })
                .onFailure().invoke(e -> {
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                })
                .eventually(() -> span.end());
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteAll() {
        Span span = tracer.spanBuilder("deleteAllOrderItemsPermanent").setSpanKind(SpanKind.SERVER).startSpan();
        return orderItemRepository.deleteAllDeleted()
                .map(res -> {
                    span.setStatus(StatusCode.OK);
                    return ApiResponse.success("All trashed order items permanently deleted", res);
                })
                .onFailure().invoke(e -> {
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                })
                .eventually(() -> span.end());
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteByOrderPermanent(Long orderId) {
        Span span = tracer.spanBuilder("deleteByOrderPermanent").setSpanKind(SpanKind.SERVER).startSpan();
        return orderItemRepository.deleteByOrderPermanent(orderId)
                .map(res -> {
                    span.setStatus(StatusCode.OK);
                    return ApiResponse.success("Order items permanently deleted for order id: " + orderId, res);
                })
                .onFailure().invoke(e -> {
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                })
                .eventually(() -> span.end());
    }

    @Override
    public Uni<ApiResponse<Integer>> calculateTotalPrice(Long orderId) {
        Span span = tracer.spanBuilder("calculateTotalPrice").setSpanKind(SpanKind.SERVER).startSpan();
        return orderItemRepository.findOrderItemByOrder(orderId)
                .map(items -> {
                    int total = items.stream()
                            .mapToInt(item -> item.getPrice() * item.getQuantity())
                            .sum();
                    span.setStatus(StatusCode.OK);
                    return ApiResponse.success("Total price calculated", total);
                })
                .onFailure().invoke(e -> {
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                })
                .eventually(() -> span.end());
    }
}
