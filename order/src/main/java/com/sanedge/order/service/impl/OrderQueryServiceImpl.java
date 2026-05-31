package com.sanedge.order.service.impl;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.domain.response.ApiResponsePagination;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.common.domain.response.PaginationMeta;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.order.domain.requests.FindAllOrderByMerchantRequest;
import com.sanedge.order.domain.requests.FindAllOrderRequest;
import com.sanedge.order.domain.response.OrderItemResponse;
import com.sanedge.order.domain.response.OrderRelationResponse;
import com.sanedge.order.domain.response.OrderResponse;
import com.sanedge.order.domain.response.OrderResponseDeleteAt;
import com.sanedge.order.entity.Order;
import com.sanedge.order.repository.OrderQueryRepository;
import com.sanedge.order.service.OrderQueryService;

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
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OrderQueryServiceImpl implements OrderQueryService {
    private static final Logger logger = LoggerFactory.getLogger(OrderQueryServiceImpl.class);

    OrderQueryRepository orderQueryRepository;
    OpenTelemetry openTelemetry;
    RedisService redisService;
    ObjectMapper objectMapper;

    @Inject
    @io.quarkus.grpc.GrpcClient("order_item")
    pb.order_item.OrderItemQueryService orderItemQueryService;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    private static final long LIST_CACHE_TTL_SECONDS = 300;

    @Inject
    public OrderQueryServiceImpl(OrderQueryRepository orderQueryRepository,
                                OpenTelemetry openTelemetry,
                                RedisService redisService,
                                ObjectMapper objectMapper) {
        this.orderQueryRepository = orderQueryRepository;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.objectMapper = objectMapper;
        this.tracer = openTelemetry.getTracer("order-query-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("order-query-service");

        this.requestsTotal = meter.counterBuilder("requests_total")
                .setDescription("Total number of requests")
                .build();
        this.requestDurationSeconds = meter.histogramBuilder("request_duration_seconds")
                .setDescription("Request duration in seconds")
                .setUnit("s")
                .build();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            logger.error("Error serializing object to JSON", e);
            throw new RuntimeException("Failed to serialize object", e);
        }
    }

    private <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            logger.error("Error deserializing JSON to object", e);
            throw new RuntimeException("Failed to deserialize JSON", e);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            logger.error("Error deserializing JSON to object with TypeReference", e);
            throw new RuntimeException("Failed to deserialize JSON", e);
        }
    }

    @Override
    public Uni<ApiResponsePagination<List<OrderResponse>>> findAll(FindAllOrderRequest req) {
        String cacheKey = String.format("order:all:%d:%d:%s", req.getPage(), req.getPageSize(),
                req.getSearch() != null ? req.getSearch() : "");

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponsePagination<List<OrderResponse>> response = fromJson(
                                cachedJson,
                                new TypeReference<ApiResponsePagination<List<OrderResponse>>>() {});
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findAllOrders")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "order-service")
                            .setAttribute("operation", "find_all_orders")
                            .startSpan();

                    int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
                    int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
                    String search = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

                    return orderQueryRepository.findOrders(search, page, size)
                            .chain(pagedResult -> {
                                span.setAttribute("order.count", pagedResult.getTotalRecords());
                                span.setAttribute("order.page", req.getPage());
                                span.setAttribute("order.size", req.getPageSize());

                                ApiResponsePagination<List<OrderResponse>> response = buildPaginatedResponse(
                                        pagedResult, req.getPage(), req.getPageSize(),
                                        "Orders retrieved successfully", OrderResponse::from);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), LIST_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            logger.info("Cached response for key: {}", cacheKey);
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_all_orders",
                                                    AttributeKey.stringKey("status"), "success"));
                                            return response;
                                        });
                            })
                            .onFailure().invoke(e -> {
                                logger.error("Error finding all orders", e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_all_orders",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_all_orders"));
                            });
                });
    }

    @Override
    public Uni<ApiResponsePagination<List<OrderResponseDeleteAt>>> findByActive(FindAllOrderRequest req) {
        String cacheKey = String.format("order:active:%d:%d:%s", req.getPage(), req.getPageSize(),
                req.getSearch() != null ? req.getSearch() : "");

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponsePagination<List<OrderResponseDeleteAt>> response = fromJson(
                                cachedJson,
                                new TypeReference<ApiResponsePagination<List<OrderResponseDeleteAt>>>() {});
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findActiveOrders")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "order-service")
                            .setAttribute("operation", "find_active_orders")
                            .startSpan();

                    int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
                    int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
                    String search = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

                    return orderQueryRepository.findActiveOrders(search, page, size)
                            .chain(pagedResult -> {
                                span.setAttribute("order.count", pagedResult.getTotalRecords());
                                span.setAttribute("order.page", req.getPage());
                                span.setAttribute("order.size", req.getPageSize());

                                ApiResponsePagination<List<OrderResponseDeleteAt>> response = buildPaginatedResponse(
                                        pagedResult, req.getPage(), req.getPageSize(),
                                        "Active orders retrieved successfully", OrderResponseDeleteAt::from);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), LIST_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            logger.info("Cached response for key: {}", cacheKey);
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_active_orders",
                                                    AttributeKey.stringKey("status"), "success"));
                                            return response;
                                        });
                            })
                            .onFailure().invoke(e -> {
                                logger.error("Error finding active orders", e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_active_orders",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_active_orders"));
                            });
                });
    }

    @Override
    public Uni<ApiResponsePagination<List<OrderResponseDeleteAt>>> findByTrashed(FindAllOrderRequest req) {
        String cacheKey = String.format("order:trashed:%d:%d:%s", req.getPage(), req.getPageSize(),
                req.getSearch() != null ? req.getSearch() : "");

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponsePagination<List<OrderResponseDeleteAt>> response = fromJson(
                                cachedJson,
                                new TypeReference<ApiResponsePagination<List<OrderResponseDeleteAt>>>() {});
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findTrashedOrders")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "order-service")
                            .setAttribute("operation", "find_trashed_orders")
                            .startSpan();

                    int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
                    int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
                    String search = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

                    return orderQueryRepository.findTrashedOrders(search, page, size)
                            .chain(pagedResult -> {
                                span.setAttribute("order.count", pagedResult.getTotalRecords());
                                span.setAttribute("order.page", req.getPage());
                                span.setAttribute("order.size", req.getPageSize());

                                ApiResponsePagination<List<OrderResponseDeleteAt>> response = buildPaginatedResponse(
                                        pagedResult, req.getPage(), req.getPageSize(),
                                        "Trashed orders retrieved successfully", OrderResponseDeleteAt::from);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), LIST_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            logger.info("Cached response for key: {}", cacheKey);
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_trashed_orders",
                                                    AttributeKey.stringKey("status"), "success"));
                                            return response;
                                        });
                            })
                            .onFailure().invoke(e -> {
                                logger.error("Error finding trashed orders", e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_trashed_orders",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_trashed_orders"));
                            });
                });
    }

    @Override
    public Uni<ApiResponsePagination<List<OrderResponse>>> findByMerchantId(FindAllOrderByMerchantRequest req) {
        String cacheKey = String.format("order:merchant:%d:%d:%d:%s", req.getMerchantId(), req.getPage(),
                req.getPageSize(), req.getSearch() != null ? req.getSearch() : "");

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponsePagination<List<OrderResponse>> response = fromJson(
                                cachedJson,
                                new TypeReference<ApiResponsePagination<List<OrderResponse>>>() {});
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findOrdersByMerchant")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "order-service")
                            .setAttribute("operation", "find_orders_by_merchant")
                            .setAttribute("merchant.id", req.getMerchantId() != null ? req.getMerchantId().toString() : "null")
                            .startSpan();

                    int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
                    int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
                    String search = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

                    return orderQueryRepository.findOrdersByMerchant(search, req.getMerchantId().longValue(), page, size)
                            .chain(pagedResult -> {
                                span.setAttribute("order.count", pagedResult.getTotalRecords());
                                span.setAttribute("order.page", req.getPage());
                                span.setAttribute("order.size", req.getPageSize());

                                ApiResponsePagination<List<OrderResponse>> response = buildPaginatedResponse(
                                        pagedResult, req.getPage(), req.getPageSize(),
                                        "Orders retrieved successfully", OrderResponse::from);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), LIST_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            logger.info("Cached response for key: {}", cacheKey);
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_orders_by_merchant",
                                                    AttributeKey.stringKey("status"), "success"));
                                            return response;
                                        });
                            })
                            .onFailure().invoke(e -> {
                                logger.error("Error finding merchant orders", e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_orders_by_merchant",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_orders_by_merchant"));
                            });
                });
    }

    @Override
    public Uni<ApiResponse<OrderResponse>> findById(Long id) {
        String cacheKey = "order:id:" + id;

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        OrderResponse cachedOrder = fromJson(cachedJson, OrderResponse.class);
                        return Uni.createFrom().item(ApiResponse.success("Order retrieved successfully", cachedOrder));
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findOrderById")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "order-service")
                            .setAttribute("operation", "find_order_by_id")
                            .setAttribute("order.id", id.toString())
                            .startSpan();

                    return orderQueryRepository.findOrderById(id)
                            .chain(optionalOrder -> {
                                if (optionalOrder.isEmpty()) {
                                    logger.warn("Order not found with ID: {}", id);
                                    span.setStatus(StatusCode.ERROR, "Order not found");
                                    span.setAttribute("order.found", false);

                                    requestsTotal.add(1, Attributes.of(
                                            AttributeKey.stringKey("operation"), "find_order_by_id",
                                            AttributeKey.stringKey("status"), "failed",
                                            AttributeKey.stringKey("error_type"), "not_found"));

                                    throw new ResourceNotFoundException("Order not found with ID: " + id);
                                }

                                Order order = optionalOrder.get();
                                span.setAttribute("order.found", true);
                                span.setAttribute("order.totalPrice", order.getTotalPrice());

                                OrderResponse orderResponse = OrderResponse.from(order);

                                return redisService.setReactive(cacheKey, toJson(orderResponse))
                                        .map(v -> {
                                            logger.info("Cached order for key: {}", cacheKey);
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_order_by_id",
                                                    AttributeKey.stringKey("status"), "success"));

                                            return ApiResponse.success("Order retrieved successfully", orderResponse);
                                        });
                            })
                            .onFailure().invoke(e -> {
                                logger.error("Error finding order by ID: {}", id, e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_order_by_id",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_order_by_id"));
                            });
                });
    }

    @Override
    public Uni<ApiResponse<OrderRelationResponse>> findOrderRelation(Long id) {
        String cacheKey = "order:relation:" + id;

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        OrderRelationResponse cachedRelation = fromJson(cachedJson, OrderRelationResponse.class);
                        return Uni.createFrom().item(ApiResponse.success("Order relation retrieved successfully", cachedRelation));
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB and gRPC.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findOrderRelation")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "order-service")
                            .setAttribute("operation", "find_order_relation")
                            .setAttribute("order.id", id.toString())
                            .startSpan();

                    return orderQueryRepository.findOrderById(id)
                            .chain(optionalOrder -> {
                                if (optionalOrder.isEmpty()) {
                                    logger.warn("Order relation not found with ID: {}", id);
                                    span.setStatus(StatusCode.ERROR, "Order relation not found");
                                    span.setAttribute("relation.found", false);

                                    requestsTotal.add(1, Attributes.of(
                                            AttributeKey.stringKey("operation"), "find_order_relation",
                                            AttributeKey.stringKey("status"), "failed",
                                            AttributeKey.stringKey("error_type"), "not_found"));

                                    throw new ResourceNotFoundException("Order relation not found with ID: " + id);
                                }

                                Order order = optionalOrder.get();
                                span.setAttribute("relation.found", true);

                                pb.order_item.OrderItemCommon.FindByIdOrderItemRequest grpcReq = pb.order_item.OrderItemCommon.FindByIdOrderItemRequest.newBuilder()
                                        .setId(id.intValue())
                                        .build();

                                return orderItemQueryService.findOrderItemByOrder(grpcReq)
                                        .chain(grpcResp -> {
                                            List<OrderItemResponse> items = grpcResp.getDataList().stream()
                                                    .map(OrderItemResponse::from)
                                                    .toList();

                                            OrderRelationResponse relationResponse = OrderRelationResponse.from(order, items);

                                            return redisService.setReactive(cacheKey, toJson(relationResponse))
                                                    .map(v -> {
                                                        logger.info("Cached order relation for key: {}", cacheKey);
                                                        span.setStatus(StatusCode.OK);

                                                        requestsTotal.add(1, Attributes.of(
                                                                AttributeKey.stringKey("operation"), "find_order_relation",
                                                                AttributeKey.stringKey("status"), "success"));

                                                        return ApiResponse.success("Order relation retrieved successfully", relationResponse);
                                                    });
                                        });
                            })
                            .onFailure().invoke(e -> {
                                logger.error("Error finding order relation by ID: {}", id, e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_order_relation",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_order_relation"));
                            });
                });
    }

    private <T, R> ApiResponsePagination<List<R>> buildPaginatedResponse(
            PagedResult<T> pagedResult,
            int pageParam,
            int sizeParam,
            String successMessage,
            Function<T, R> mapper) {

        List<R> data = pagedResult.getData().stream()
                .map(mapper)
                .collect(Collectors.toList());

        int totalRecords = pagedResult.getTotalRecords();
        int size = sizeParam > 0 ? sizeParam : 1;
        int totalPages = (int) Math.ceil((double) totalRecords / size);

        PaginationMeta pagination = new PaginationMeta(pageParam, size, totalPages, totalRecords);

        return new ApiResponsePagination<>("success", successMessage, data, pagination);
    }
}
