package com.sanedge.transaction.service.impl;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.transaction.domain.requests.CreateTransactionRequest;
import com.sanedge.transaction.domain.requests.UpdateTransactionRequest;
import com.sanedge.transaction.domain.response.TransactionResponse;
import com.sanedge.transaction.domain.response.TransactionResponseDeleteAt;
import com.sanedge.transaction.repository.TransactionCommandRepository;
import com.sanedge.transaction.repository.TransactionQueryRepository;
import com.sanedge.transaction.service.TransactionCommandService;
import com.sanedge.transaction.entity.Transaction;
import com.sanedge.transaction.enums.PaymentStatus;
import com.sanedge.transaction.service.KafkaService;
import io.quarkus.grpc.GrpcClient;
import io.vertx.core.json.JsonObject;
import pb.user.UserQueryService;

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
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TransactionCommandServiceImpl implements TransactionCommandService {
    private static final Logger logger = LoggerFactory.getLogger(TransactionCommandServiceImpl.class);

    private final TransactionQueryRepository transactionQueryRepository;
    private final TransactionCommandRepository transactionCommandRepository;
    private final OpenTelemetry openTelemetry;
    private final RedisService redisService;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    @Inject
    @GrpcClient("merchant")
    pb.merchant.MerchantQueryService merchantQueryService;

    @Inject
    @GrpcClient("order")
    pb.order.OrderQueryService orderQueryService;

    @Inject
    @GrpcClient("order_item")
    pb.order_item.OrderItemQueryService orderItemQueryService;

    @Inject
    @GrpcClient("shipping_address")
    pb.shipping_address.ShippingQueryService shippingQueryService;

    @Inject
    @GrpcClient("user")
    UserQueryService userQueryService;

    @Inject
    KafkaService kafkaService;

    @Inject
    public TransactionCommandServiceImpl(TransactionQueryRepository transactionQueryRepository,
            TransactionCommandRepository transactionCommandRepository,
            OpenTelemetry openTelemetry,
            RedisService redisService) {
        this.transactionQueryRepository = transactionQueryRepository;
        this.transactionCommandRepository = transactionCommandRepository;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.tracer = openTelemetry.getTracer("transaction-command-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("transaction-command-service");

        this.requestsTotal = meter.counterBuilder("requests_total")
                .setDescription("Total number of requests")
                .build();
        this.requestDurationSeconds = meter.histogramBuilder("request_duration_seconds")
                .setDescription("Request duration in seconds")
                .setUnit("s")
                .build();
    }

    private Uni<Void> invalidateCache(Long txId, Integer orderId) {
        Uni<Void> deleteTx = txId != null ? redisService.deleteReactive("transaction:id:" + txId).replaceWithVoid()
                : Uni.createFrom().voidItem();
        Uni<Void> deleteOrderTx = orderId != null
                ? redisService.deleteReactive("transaction:order:" + orderId).replaceWithVoid()
                : Uni.createFrom().voidItem();
        return Uni.combine().all().unis(deleteTx, deleteOrderTx).discardItems()
                .chain(v -> redisService.deleteReactive("transaction:all:*").replaceWithVoid())
                .chain(v -> redisService.deleteReactive("transaction:active:*").replaceWithVoid())
                .chain(v -> redisService.deleteReactive("transaction:trashed:*").replaceWithVoid())
                .chain(v -> redisService.deleteReactive("transaction:merchant:*").replaceWithVoid())
                .onFailure().recoverWithItem((Void) null);
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<TransactionResponse>> create(CreateTransactionRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("createTransaction")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "transaction-service")
                .setAttribute("operation", "create_transaction")
                .setAttribute("order.id", req.getOrderID() != null ? req.getOrderID().toString() : "null")
                .setAttribute("merchant.id", req.getMerchantID() != null ? req.getMerchantID().toString() : "null")
                .startSpan();

        logger.info("💳 Creating new transaction | orderId={}, merchantId={}", req.getOrderID(), req.getMerchantID());

        return merchantQueryService.findById(pb.merchant.MerchantCommon.FindByIdMerchantRequest.newBuilder().setId(req.getMerchantID()).build())
                .chain(merchantResponse -> {
                    if (merchantResponse == null || !merchantResponse.hasData() || merchantResponse.getData().getId() == 0) {
                        logger.error("❌ Merchant not found | merchantId={}", req.getMerchantID());
                        throw new ResourceNotFoundException("Merchant not found");
                    }

                    return orderQueryService.findById(pb.order.OrderCommon.FindByIdOrderRequest.newBuilder().setId(req.getOrderID()).build());
                })
                .chain(orderResponse -> {
                    if (orderResponse == null || !orderResponse.hasData() || orderResponse.getData().getId() == 0) {
                        logger.error("❌ Order not found | orderId={}", req.getOrderID());
                        throw new ResourceNotFoundException("Order not found");
                    }
                    pb.order.OrderCommon.OrderResponse order = orderResponse.getData();

                    return Uni.combine().all().unis(
                            orderItemQueryService.findOrderItemByOrder(pb.order_item.OrderItemCommon.FindByIdOrderItemRequest.newBuilder().setId(order.getId()).build()),
                            shippingQueryService.findByOrder(pb.shipping_address.ShippingAddressCommon.FindByIdShippingRequest.newBuilder().setId(order.getId()).build())
                    ).asTuple().chain(tuple -> {
                        List<pb.order_item.OrderItemCommon.OrderItemResponse> orderItems = tuple.getItem1().getDataList();
                        pb.shipping_address.ShippingAddressCommon.ShippingResponse shipping = tuple.getItem2().getData();

                        if (orderItems.isEmpty()) {
                            logger.error("❌ No order items found | orderId={}", req.getOrderID());
                            throw new IllegalArgumentException("No order items found");
                        }

                        if (shipping == null || shipping.getId() == 0) {
                            logger.error("❌ Shipping address not found | orderId={}", req.getOrderID());
                            throw new ResourceNotFoundException("Shipping address not found");
                        }

                        int totalAmount = 0;
                        for (pb.order_item.OrderItemCommon.OrderItemResponse item : orderItems) {
                            if (item.getQuantity() <= 0) {
                                throw new IllegalArgumentException("Invalid order item quantity");
                            }
                            totalAmount += item.getPrice() * item.getQuantity();
                        }
                        totalAmount += shipping.getShippingCost();
                        int ppn = totalAmount * 11 / 100;
                        int totalAmountWithTax = totalAmount + ppn;

                        String paymentStatus = req.getAmount() >= totalAmountWithTax ? "success" : "failed";
                        if (paymentStatus.equals("failed")) {
                            logger.error("❌ Insufficient payment amount | amount={}, required={}",
                                    req.getAmount(), totalAmountWithTax);
                            throw new IllegalArgumentException("Insufficient payment amount");
                        }

                        req.setAmount(totalAmountWithTax);
                        req.setPaymentStatus(paymentStatus);

                        Transaction transaction = new Transaction();
                        transaction.setOrderId(req.getOrderID());
                        transaction.setMerchantId(req.getMerchantID());
                        transaction.setPaymentMethod(req.getPaymentMethod());
                        transaction.setAmount(req.getAmount());
                        transaction.setStatus(PaymentStatus.fromValue(req.getPaymentStatus()));

                        return transactionCommandRepository.persist(transaction)
                                .chain(saved -> sendTransactionEmail(saved, order).replaceWith(saved));
                    });
                })
                .chain(saved -> {
                    TransactionResponse response = TransactionResponse.from(saved);
                    span.setAttribute("transaction.id", saved.id);

                    return invalidateCache(saved.id, saved.getOrderId())
                            .map(v -> {
                                logger.info("✅ Transaction created successfully | transactionId={}", saved.id);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "create_transaction",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Transaction created successfully", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to create transaction", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_transaction",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_transaction"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<TransactionResponse>> update(UpdateTransactionRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("updateTransaction")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "transaction-service")
                .setAttribute("operation", "update_transaction")
                .setAttribute("transaction.id",
                        req.getTransactionID() != null ? req.getTransactionID().toString() : "null")
                .startSpan();

        logger.info("✏️ Updating transaction | transactionId={}", req.getTransactionID());

        return transactionQueryRepository.findTransactionById(req.getTransactionID().longValue())
                .chain(existingTx -> {
                    if (existingTx.isEmpty()) {
                        logger.error("❌ Transaction not found | transactionId={}", req.getTransactionID());
                        throw new ResourceNotFoundException("Transaction not found");
                    }
                    Transaction tx = existingTx.get();

                    if (PaymentStatus.SUCCESS.equals(tx.getStatus()) ||
                            PaymentStatus.REFUNDED.equals(tx.getStatus())) {
                        logger.error("❌ Transaction cannot be modified | transactionId={}", req.getTransactionID());
                        throw new IllegalArgumentException("Transaction cannot be modified");
                    }

                    return merchantQueryService.findById(pb.merchant.MerchantCommon.FindByIdMerchantRequest.newBuilder().setId(req.getMerchantID()).build())
                            .chain(merchantResponse -> {
                                if (merchantResponse == null || !merchantResponse.hasData() || merchantResponse.getData().getId() == 0) {
                                    logger.error("❌ Merchant not found | merchantId={}", req.getMerchantID());
                                    throw new ResourceNotFoundException("Merchant not found");
                                }

                                return orderQueryService.findById(pb.order.OrderCommon.FindByIdOrderRequest.newBuilder().setId(req.getOrderID()).build());
                            })
                            .chain(orderResponse -> {
                                if (orderResponse == null || !orderResponse.hasData() || orderResponse.getData().getId() == 0) {
                                    logger.error("❌ Order not found | orderId={}", req.getOrderID());
                                    throw new ResourceNotFoundException("Order not found");
                                }
                                pb.order.OrderCommon.OrderResponse order = orderResponse.getData();

                                return Uni.combine().all().unis(
                                        orderItemQueryService.findOrderItemByOrder(pb.order_item.OrderItemCommon.FindByIdOrderItemRequest.newBuilder().setId(order.getId()).build()),
                                        shippingQueryService.findByOrder(pb.shipping_address.ShippingAddressCommon.FindByIdShippingRequest.newBuilder().setId(order.getId()).build())
                                ).asTuple().chain(tuple -> {
                                    List<pb.order_item.OrderItemCommon.OrderItemResponse> orderItems = tuple.getItem1().getDataList();
                                    pb.shipping_address.ShippingAddressCommon.ShippingResponse shipping = tuple.getItem2().getData();

                                    if (orderItems.isEmpty()) {
                                        logger.error("❌ No order items found | orderId={}", req.getOrderID());
                                        throw new IllegalArgumentException("No order items found");
                                    }

                                    if (shipping == null || shipping.getId() == 0) {
                                        logger.error("❌ Shipping address not found | orderId={}",
                                                req.getOrderID());
                                        throw new ResourceNotFoundException("Shipping address not found");
                                    }

                                    int totalAmount = 0;
                                    for (pb.order_item.OrderItemCommon.OrderItemResponse item : orderItems) {
                                        if (item.getQuantity() <= 0) {
                                            throw new IllegalArgumentException("Invalid order item quantity");
                                        }
                                        totalAmount += item.getPrice() * item.getQuantity();
                                    }
                                    totalAmount += shipping.getShippingCost();
                                    int ppn = totalAmount * 11 / 100;
                                    int totalAmountWithTax = totalAmount + ppn;

                                    String paymentStatus = req.getAmount() >= totalAmountWithTax ? "success"
                                            : "failed";
                                    if (paymentStatus.equals("failed")) {
                                        logger.error("❌ Insufficient payment amount | amount={}, required={}",
                                                req.getAmount(), totalAmountWithTax);
                                        throw new IllegalArgumentException("Insufficient payment amount");
                                    }

                                    req.setAmount(totalAmountWithTax);
                                    req.setPaymentStatus(paymentStatus);

                                    tx.setOrderId(req.getOrderID());
                                    tx.setMerchantId(req.getMerchantID());
                                    tx.setPaymentMethod(req.getPaymentMethod());
                                    tx.setAmount(req.getAmount());
                                    tx.setStatus(PaymentStatus.fromValue(req.getPaymentStatus()));

                                    return transactionCommandRepository.persist(tx);
                                });
                            });
                })
                .chain(updated -> {
                    TransactionResponse response = TransactionResponse.from(updated);

                    return invalidateCache(updated.id, updated.getOrderId())
                            .map(v -> {
                                logger.info("✅ Transaction updated successfully | transactionId={}", updated.id);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "update_transaction",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Transaction updated successfully", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to update transaction", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_transaction",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_transaction"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<TransactionResponseDeleteAt>> trash(Integer id) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("trashTransaction")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "transaction-service")
                .setAttribute("operation", "trash_transaction")
                .setAttribute("transaction.id", id.toString())
                .startSpan();

        logger.info("🗑️ Trashing transaction id={}", id);

        return transactionCommandRepository.trashed(id.longValue())
                .chain(transaction -> {
                    if (transaction == null) {
                        throw new ResourceNotFoundException("Transaction not found or already trashed");
                    }
                    TransactionResponseDeleteAt response = TransactionResponseDeleteAt.from(transaction);

                    return invalidateCache(id.longValue(), transaction.getOrderId())
                            .map(v -> {
                                logger.info("Successfully trashed transaction with ID: {}", id);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "trash_transaction",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("🗑️ Transaction trashed successfully!", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to trash transaction id={}", id, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_transaction",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_transaction"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<TransactionResponseDeleteAt>> restore(Integer id) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreTransaction")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "transaction-service")
                .setAttribute("operation", "restore_transaction")
                .setAttribute("transaction.id", id.toString())
                .startSpan();

        logger.info("♻️ Restoring transaction id={}", id);

        return transactionCommandRepository.restore(id.longValue())
                .chain(transaction -> {
                    if (transaction == null) {
                        throw new ResourceNotFoundException("Transaction not found or not trashed");
                    }
                    TransactionResponseDeleteAt response = TransactionResponseDeleteAt.from(transaction);

                    return invalidateCache(id.longValue(), transaction.getOrderId())
                            .map(v -> {
                                logger.info("Successfully restored transaction with ID: {}", id);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "restore_transaction",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("♻️ Transaction restored successfully!", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to restore transaction id={}", id, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_transaction",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_transaction"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> delete(Integer id) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteTransactionPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "transaction-service")
                .setAttribute("operation", "delete_transaction_permanent")
                .setAttribute("transaction.id", id.toString())
                .startSpan();

        logger.warn("🧨 Permanently deleting transaction id={}", id);

        return transactionCommandRepository.deletePermanent(id.longValue())
                .chain(deleted -> {
                    return invalidateCache(id.longValue(), null)
                            .map(v -> {
                                logger.info("Successfully permanently deleted transaction with ID: {}", id);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "delete_transaction_permanent",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("🧨 Transaction permanently deleted!", deleted);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to permanently delete transaction id={}", id, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_transaction_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_transaction_permanent"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> restoreAll() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreAllTransactions")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "transaction-service")
                .setAttribute("operation", "restore_all_transactions")
                .startSpan();

        logger.info("🔄 Restoring ALL trashed transactions");

        return transactionCommandRepository.restoreAllDeleted()
                .chain(restored -> {
                    return invalidateCache(null, null)
                            .map(v -> {
                                logger.info("Successfully restored all trashed transactions");
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "restore_all_transactions",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("🔄 All transactions restored successfully!", restored);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to restore all transactions", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_transactions",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_transactions"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteAll() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteAllTransactionsPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "transaction-service")
                .setAttribute("operation", "delete_all_transactions_permanent")
                .startSpan();

        logger.warn("💣 Permanently deleting ALL trashed transactions");

        return transactionCommandRepository.deleteAllDeleted()
                .chain(deleted -> {
                    return invalidateCache(null, null)
                            .map(v -> {
                                logger.info("Successfully permanently deleted all trashed transactions");
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "delete_all_transactions_permanent",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("💣 All transactions permanently deleted!", deleted);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to delete all transactions", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_transactions_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_transactions_permanent"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteByOrder(Integer orderId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteTransactionByOrderPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "transaction-service")
                .setAttribute("operation", "delete_transaction_by_order_permanent")
                .setAttribute("order.id", orderId.toString())
                .startSpan();

        logger.warn("🧨 Permanently deleting transaction by order id={}", orderId);

        return transactionCommandRepository.deleteByOrderPermanent(orderId.longValue())
                .chain(deleted -> {
                    return invalidateCache(null, orderId)
                            .map(v -> {
                                logger.info("Successfully permanently deleted transaction by order ID: {}", orderId);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "delete_transaction_by_order_permanent",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("🧨 Transaction by order permanently deleted!", deleted);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to permanently delete transaction by order id={}", orderId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_transaction_by_order_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_transaction_by_order_permanent"));
                });
    }

    private Uni<Void> sendTransactionEmail(Transaction tx, pb.order.OrderCommon.OrderResponse order) {
        return userQueryService
                .findById(pb.user.UserCommon.FindByIdUserRequest.newBuilder().setId(order.getUserId()).build())
                .chain(res -> {
                    if (!"success".equalsIgnoreCase(res.getStatus()) || !res.hasData()) {
                        logger.warn("⚠️ User not found for transaction email, userId={}", order.getUserId());
                        return Uni.createFrom().voidItem();
                    }
                    pb.user.UserCommon.UserResponse user = res.getData();

                    String subject = "Transaction Created Successfully";
                    String body = String.format(
                            "Hello %s %s,\n\nYour transaction has been created successfully!\n\nTransaction Details:\n- ID: %s\n- Amount: %s\n- Payment Method: %s\n- Status: %s\n\nThank you for shopping with us!\n\nRegards,\nSupport Team",
                            user.getFirstname(), user.getLastname(), tx.id, tx.getAmount(), tx.getPaymentMethod(),
                            tx.getStatus());

                    JsonObject payload = new JsonObject()
                            .put("email", user.getEmail())
                            .put("subject", subject)
                            .put("body", body);

                    return kafkaService.sendMessage("email-service-topic-transaction-create", user.getEmail(), payload);
                })
                .onFailure().invoke(err -> logger.error("❌ Failed to send transaction email", err))
                .replaceWithVoid()
                .onFailure().recoverWithItem((Void) null);
    }
}
