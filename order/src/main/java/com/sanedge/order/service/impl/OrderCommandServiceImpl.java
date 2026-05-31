package com.sanedge.order.service.impl;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.order.domain.requests.CreateOrderItemRequest;
import com.sanedge.order.domain.requests.CreateOrderRequest;
import com.sanedge.order.domain.requests.UpdateOrderItemRequest;
import com.sanedge.order.domain.requests.UpdateOrderRequest;
import com.sanedge.order.domain.response.OrderResponse;
import com.sanedge.order.domain.response.OrderResponseDeleteAt;
import com.sanedge.order.entity.Order;
import com.sanedge.order.entity.ShippingAddress;
import com.sanedge.order.repository.OrderCommandRepository;
import com.sanedge.order.repository.OrderQueryRepository;
import com.sanedge.order.repository.ShippingAddressCommandRepository;
import com.sanedge.order.repository.ShippingAddressQueryRepository;
import com.sanedge.order.service.OrderCommandService;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

@ApplicationScoped
public class OrderCommandServiceImpl implements OrderCommandService {
    private static final Logger logger = LoggerFactory.getLogger(OrderCommandServiceImpl.class);

    @Inject
    @GrpcClient("merchant")
    pb.merchant.MerchantQueryService merchantQueryService;

    @Inject
    @GrpcClient("user")
    pb.user.UserQueryService userQueryService;

    @Inject
    @GrpcClient("product")
    pb.product.ProductQueryService productQueryService;

    @Inject
    @GrpcClient("product")
    pb.product.ProductCommandService productCommandService;

    @Inject
    @GrpcClient("order_item")
    pb.order_item.OrderItemCommandService orderItemCommandServiceGrpc;

    OrderQueryRepository orderQueryRepository;
    OrderCommandRepository orderCommandRepository;
    ShippingAddressQueryRepository shippingAddressQueryRepository;
    ShippingAddressCommandRepository shippingAddressCommandRepository;
    Validator validator;
    OpenTelemetry openTelemetry;
    RedisService redisService;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    @Inject
    public OrderCommandServiceImpl(OrderQueryRepository orderQueryRepository,
            OrderCommandRepository orderCommandRepository,
            ShippingAddressQueryRepository shippingAddressQueryRepository,
            ShippingAddressCommandRepository shippingAddressCommandRepository,
            Validator validator,
            OpenTelemetry openTelemetry,
            RedisService redisService) {
        this.orderQueryRepository = orderQueryRepository;
        this.orderCommandRepository = orderCommandRepository;
        this.shippingAddressQueryRepository = shippingAddressQueryRepository;
        this.shippingAddressCommandRepository = shippingAddressCommandRepository;
        this.validator = validator;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.tracer = openTelemetry.getTracer("order-command-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("order-command-service");

        this.requestsTotal = meter.counterBuilder("requests_total")
                .setDescription("Total number of requests")
                .build();
        this.requestDurationSeconds = meter.histogramBuilder("request_duration_seconds")
                .setDescription("Request duration in seconds")
                .setUnit("s")
                .build();
    }

    private <T> void validateRequest(T req) {
        Set<ConstraintViolation<T>> violations = validator.validate(req);
        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (ConstraintViolation<T> violation : violations) {
                sb.append(violation.getPropertyPath()).append(": ").append(violation.getMessage()).append("; ");
            }
            logger.error("Validation failed: {}", sb);
            throw new ConstraintViolationException("Validation failed: " + sb, violations);
        }
    }

    private Uni<Void> invalidateCache(Long orderId) {
        if (orderId != null) {
            return Uni.combine().all().unis(
                    redisService.deleteReactive("order:id:" + orderId),
                    redisService.deleteReactive("order:relation:" + orderId)).discardItems();
        }
        return Uni.createFrom().voidItem();
    }

    private Uni<Integer> processCreateOrderItems(List<CreateOrderItemRequest> items, Order order, int index,
            int currentTotalPrice) {
        if (index >= items.size()) {
            return Uni.createFrom().item(currentTotalPrice);
        }

        CreateOrderItemRequest itemReq = items.get(index);
        return productQueryService
                .findById(pb.product.ProductCommon.FindByIdProductRequest.newBuilder().setId(itemReq.getProductId())
                        .build())
                .chain(prodResponse -> {
                    if (prodResponse == null || !prodResponse.hasData() || prodResponse.getData().getId() == 0) {
                        throw new ResourceNotFoundException("Product not found with id=" + itemReq.getProductId());
                    }
                    pb.product.ProductCommon.ProductResponse product = prodResponse.getData();
                    if (product.getCountInStock() < itemReq.getQuantity()) {
                        throw new IllegalArgumentException(
                                "Insufficient stock for product id=" + itemReq.getProductId());
                    }

                    var createReq = pb.order_item.OrderItemCommand.CreateOrderItemRecordRequest.newBuilder()
                            .setOrderId(order.id.intValue())
                            .setProductId(itemReq.getProductId())
                            .setQuantity(itemReq.getQuantity())
                            .setPrice(itemReq.getPrice())
                            .build();

                    int newStock = product.getCountInStock() - itemReq.getQuantity();

                    return Uni.combine().all().unis(
                            orderItemCommandServiceGrpc.createOrderItem(createReq),
                            productCommandService.updateProductCountStock(
                                    pb.product.ProductCommand.UpdateProductCountStockRequest.newBuilder()
                                            .setProductId(itemReq.getProductId())
                                            .setStock(newStock)
                                            .build()))
                            .discardItems().chain(() -> {
                                int addedPrice = itemReq.getQuantity() * itemReq.getPrice();
                                return processCreateOrderItems(items, order, index + 1, currentTotalPrice + addedPrice);
                            });
                });
    }

    private Uni<Integer> processUpdateOrderItems(List<UpdateOrderItemRequest> items, Order order, int index,
            int currentTotalPrice) {
        if (index >= items.size()) {
            return Uni.createFrom().item(currentTotalPrice);
        }

        UpdateOrderItemRequest itemReq = items.get(index);
        return productQueryService
                .findById(pb.product.ProductCommon.FindByIdProductRequest.newBuilder().setId(itemReq.getProductId())
                        .build())
                .chain(prodResponse -> {
                    if (prodResponse == null || !prodResponse.hasData() || prodResponse.getData().getId() == 0) {
                        throw new ResourceNotFoundException("Product not found with id=" + itemReq.getProductId());
                    }
                    pb.product.ProductCommon.ProductResponse product = prodResponse.getData();

                    Uni<Void> persistUnit;
                    if (itemReq.getOrderItemId() != null && itemReq.getOrderItemId() > 0) {
                        var updateReq = pb.order_item.OrderItemCommand.UpdateOrderItemRecordRequest.newBuilder()
                                .setOrderItemId(itemReq.getOrderItemId())
                                .setQuantity(itemReq.getQuantity())
                                .setPrice(itemReq.getPrice())
                                .build();
                        persistUnit = orderItemCommandServiceGrpc.updateOrderItem(updateReq).replaceWithVoid();
                    } else {
                        if (product.getCountInStock() < itemReq.getQuantity()) {
                            throw new IllegalArgumentException(
                                    "Insufficient stock for product id=" + itemReq.getProductId());
                        }
                        var createReq = pb.order_item.OrderItemCommand.CreateOrderItemRecordRequest.newBuilder()
                                .setOrderId(order.id.intValue())
                                .setProductId(itemReq.getProductId())
                                .setQuantity(itemReq.getQuantity())
                                .setPrice(itemReq.getPrice())
                                .build();

                        int newStock = product.getCountInStock() - itemReq.getQuantity();

                        persistUnit = Uni.combine().all().unis(
                                orderItemCommandServiceGrpc.createOrderItem(createReq),
                                productCommandService.updateProductCountStock(
                                        pb.product.ProductCommand.UpdateProductCountStockRequest.newBuilder()
                                                .setProductId(itemReq.getProductId())
                                                .setStock(newStock)
                                                .build()))
                                .discardItems();
                    }

                    return persistUnit.chain(() -> {
                        int addedPrice = itemReq.getQuantity() * itemReq.getPrice();
                        return processUpdateOrderItems(items, order, index + 1, currentTotalPrice + addedPrice);
                    });
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<OrderResponse>> create(CreateOrderRequest request) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("createOrder")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "order-service")
                .setAttribute("operation", "create_order")
                .setAttribute("merchant.id",
                        request.getMerchantId() != null ? request.getMerchantId().toString() : "null")
                .setAttribute("user.id", request.getUserId() != null ? request.getUserId().toString() : "null")
                .startSpan();

        logger.info("🆕 Creating new order for merchantId={} and userId={}", request.getMerchantId(),
                request.getUserId());

        try {
            validateRequest(request);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            return Uni.createFrom().failure(e);
        }

        return merchantQueryService
                .findById(pb.merchant.MerchantCommon.FindByIdMerchantRequest.newBuilder().setId(request.getMerchantId())
                        .build())
                .chain(merchantResponse -> {
                    if (merchantResponse == null || !merchantResponse.hasData()
                            || merchantResponse.getData().getId() == 0) {
                        throw new ResourceNotFoundException("Merchant not found");
                    }
                    return userQueryService
                            .findById(pb.user.UserCommon.FindByIdUserRequest.newBuilder().setId(request.getUserId())
                                    .build());
                })
                .chain(userResponse -> {
                    if (userResponse == null || !userResponse.hasData() || userResponse.getData().getId() == 0) {
                        throw new ResourceNotFoundException("User not found");
                    }

                    Order order = new Order();
                    order.setMerchantId(request.getMerchantId());
                    order.setUserId(request.getUserId());
                    order.setTotalPrice(0);

                    return orderCommandRepository.persist(order);
                })
                .chain(savedOrder -> {
                    return processCreateOrderItems(request.getItems(), savedOrder, 0, 0)
                            .chain(totalPrice -> {
                                ShippingAddress address = new ShippingAddress();
                                address.setOrderId(savedOrder.id.intValue());
                                address.setAlamat(request.getShippingAddress().getAlamat());
                                address.setProvinsi(request.getShippingAddress().getProvinsi());
                                address.setKota(request.getShippingAddress().getKota());
                                address.setCourier(request.getShippingAddress().getCourier());
                                address.setShippingMethod(request.getShippingAddress().getShippingMethod());
                                address.setShippingCost(request.getShippingAddress().getShippingCost());
                                address.setNegara(request.getShippingAddress().getNegara());

                                savedOrder.setTotalPrice(totalPrice);

                                return Uni.combine().all().unis(
                                        shippingAddressCommandRepository.persist(address),
                                        orderCommandRepository.persist(savedOrder)).asTuple().chain(tuple -> {
                                            OrderResponse response = OrderResponse.from(savedOrder);
                                            span.setAttribute("order.id", savedOrder.id);

                                            return invalidateCache(savedOrder.id)
                                                    .map(v -> {
                                                        logger.info("Successfully created order with ID: {}",
                                                                savedOrder.id);
                                                        span.setStatus(StatusCode.OK);

                                                        requestsTotal.add(1, Attributes.of(
                                                                AttributeKey.stringKey("operation"), "create_order",
                                                                AttributeKey.stringKey("status"), "success"));

                                                        return ApiResponse.success("Order created successfully",
                                                                response);
                                                    });
                                        });
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to create order for user: {}", request.getUserId(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_order",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_order"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<OrderResponse>> update(UpdateOrderRequest request) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("updateOrder")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "order-service")
                .setAttribute("operation", "update_order")
                .setAttribute("order.id", request.getOrderId() != null ? request.getOrderId().toString() : "null")
                .startSpan();

        logger.info("🔄 Updating order id={}", request.getOrderId());

        if (request.getOrderId() == null) {
            span.setStatus(StatusCode.ERROR, "OrderId is required");
            return Uni.createFrom().failure(new ResourceNotFoundException("OrderId is required"));
        }

        return orderQueryRepository.findOrderById(request.getOrderId().longValue())
                .<Order>chain(optOrder -> {
                    if (optOrder.isEmpty()) {
                        throw new ResourceNotFoundException("Order not found");
                    }
                    return userQueryService
                            .findById(pb.user.UserCommon.FindByIdUserRequest.newBuilder().setId(request.getUserId())
                                    .build())
                            .map(userResponse -> {
                                if (userResponse == null || !userResponse.hasData()
                                        || userResponse.getData().getId() == 0) {
                                    throw new ResourceNotFoundException("User not found");
                                }
                                return optOrder.get();
                            });
                })
                .<ApiResponse<OrderResponse>>chain(existingOrder -> {
                    return processUpdateOrderItems(request.getItems(), existingOrder, 0, 0)
                            .<ApiResponse<OrderResponse>>chain(totalPrice -> {
                                return shippingAddressQueryRepository
                                        .findById(request.getShippingAddress().getShippingId().longValue())
                                        .<ApiResponse<OrderResponse>>chain(shippingAddress -> {
                                            if (shippingAddress == null) {
                                                throw new ResourceNotFoundException("Shipping address not found");
                                            }
                                            ShippingAddress address = shippingAddress;
                                            address.setAlamat(request.getShippingAddress().getAlamat());
                                            address.setProvinsi(request.getShippingAddress().getProvinsi());
                                            address.setKota(request.getShippingAddress().getKota());
                                            address.setCourier(request.getShippingAddress().getCourier());
                                            address.setShippingMethod(request.getShippingAddress().getShippingMethod());
                                            address.setShippingCost(request.getShippingAddress().getShippingCost());
                                            address.setNegara(request.getShippingAddress().getNegara());

                                            existingOrder.setTotalPrice(totalPrice);

                                            return Uni.combine().all().unis(
                                                    shippingAddressCommandRepository.persist(address),
                                                    orderCommandRepository.persist(existingOrder)).asTuple()
                                                    .<ApiResponse<OrderResponse>>chain(tuple -> {
                                                        OrderResponse response = OrderResponse.from(existingOrder);

                                                        return invalidateCache(existingOrder.id)
                                                                .map(v -> {
                                                                    logger.info(
                                                                            "Successfully updated order with ID: {}",
                                                                            existingOrder.id);
                                                                    span.setStatus(StatusCode.OK);

                                                                    requestsTotal.add(1, Attributes.of(
                                                                            AttributeKey.stringKey("operation"),
                                                                            "update_order",
                                                                            AttributeKey.stringKey("status"),
                                                                            "success"));

                                                                    return ApiResponse.success(
                                                                            "Order updated successfully", response);
                                                                });
                                                    });
                                        });
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to update order ID: {}", request.getOrderId(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_order",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_order"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<OrderResponseDeleteAt>> trash(Long id) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("trashOrder")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "order-service")
                .setAttribute("operation", "trash_order")
                .setAttribute("order.id", id.toString())
                .startSpan();

        logger.info("🗑️ Trashing order id={}", id);

        return orderCommandRepository.trashed(id)
                .chain(order -> {
                    if (order == null) {
                        logger.warn("Failed to trash order - not found or already trashed with ID: {}", id);
                        throw new ResourceNotFoundException("Order not found or already trashed");
                    }

                    OrderResponseDeleteAt response = OrderResponseDeleteAt.from(order);

                    return invalidateCache(id)
                            .map(v -> {
                                logger.info("Successfully trashed order with ID: {}", id);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "trash_order",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Order trashed successfully!", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to trash order ID: {}", id, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_order",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_order"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<OrderResponseDeleteAt>> restore(Long id) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreOrder")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "order-service")
                .setAttribute("operation", "restore_order")
                .setAttribute("order.id", id.toString())
                .startSpan();

        logger.info("♻️ Restoring order id={}", id);

        return orderCommandRepository.restore(id)
                .chain(order -> {
                    if (order == null) {
                        logger.warn("Failed to restore order - not found or not trashed with ID: {}", id);
                        throw new ResourceNotFoundException("Order not found or not trashed");
                    }

                    OrderResponseDeleteAt response = OrderResponseDeleteAt.from(order);

                    return invalidateCache(id)
                            .map(v -> {
                                logger.info("Successfully restored order with ID: {}", id);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "restore_order",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Order restored successfully!", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to restore order ID: {}", id, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_order",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_order"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Void>> delete(Long id) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteOrderPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "order-service")
                .setAttribute("operation", "delete_order_permanent")
                .setAttribute("order.id", id.toString())
                .startSpan();

        logger.warn("🧨 Permanently deleting order id={}", id);

        return orderCommandRepository.findById(id)
                .chain(order -> {
                    if (order == null) {
                        logger.warn("Permanent delete failed - not found with ID: {}", id);
                        throw new ResourceNotFoundException("Order not found");
                    }

                    return orderCommandRepository.deletePermanent(id)
                            .chain(deleted -> {
                                return invalidateCache(id)
                                        .map(v -> {
                                            logger.info("Successfully permanently deleted order with ID: {}", id);
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "delete_order_permanent",
                                                    AttributeKey.stringKey("status"), "success"));

                                            return ApiResponse.<Void>success("Order permanently deleted!");
                                        });
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to permanently delete order ID: {}", id, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_order_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_order_permanent"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Void>> restoreAll() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreAllOrders")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "order-service")
                .setAttribute("operation", "restore_all_orders")
                .startSpan();

        logger.info("🔄 Restoring ALL trashed orders");

        return orderCommandRepository.restoreAllDeleted()
                .map(restored -> {
                    logger.info("Successfully restored all trashed orders");
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_orders",
                            AttributeKey.stringKey("status"), "success"));

                    return ApiResponse.<Void>success("All orders restored successfully!");
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to restore all trashed orders", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_orders",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_orders"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Void>> deleteAll() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteAllOrdersPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "order-service")
                .setAttribute("operation", "delete_all_orders_permanent")
                .startSpan();

        logger.warn("💣 Permanently deleting ALL trashed orders");

        return orderCommandRepository.deleteAllDeleted()
                .map(deleted -> {
                    logger.info("Successfully permanently deleted all trashed orders");
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_orders_permanent",
                            AttributeKey.stringKey("status"), "success"));

                    return ApiResponse.<Void>success("All orders permanently deleted!");
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to permanently delete all trashed orders", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_orders_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_orders_permanent"));
                });
    }
}
