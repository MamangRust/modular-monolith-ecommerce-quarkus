package com.sanedge.order_item.handler;

import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.order_item.domain.requests.CreateOrderItemRequest;
import com.sanedge.order_item.domain.requests.UpdateOrderItemRequest;
import com.sanedge.order_item.domain.response.OrderItemResponse;
import com.sanedge.order_item.domain.response.OrderItemResponseDeleteAt;
import com.sanedge.order_item.service.OrderItemCommandService;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import pb.order_item.MutinyOrderItemCommandServiceGrpc;
import pb.order_item.OrderItemCommand;
import pb.order_item.OrderItemCommon.ApiResponseOrderItem;
import pb.order_item.OrderItemCommon.ApiResponseOrderItemAll;
import pb.order_item.OrderItemCommon.ApiResponseOrderItemDelete;
import pb.order_item.OrderItemCommon.FindByIdOrderItemRequest;

@GrpcService
@Singleton
public class OrderItemCommandGrpcHandler extends MutinyOrderItemCommandServiceGrpc.OrderItemCommandServiceImplBase {

    @Inject
    OrderItemCommandService orderItemCommandService;

    @Override
    public Uni<ApiResponseOrderItem> createOrderItem(OrderItemCommand.CreateOrderItemRecordRequest request) {
        CreateOrderItemRequest domainReq = new CreateOrderItemRequest();
        domainReq.setOrderId(request.getOrderId());
        domainReq.setProductId(request.getProductId());
        domainReq.setQuantity(request.getQuantity());
        domainReq.setPrice(request.getPrice());

        return orderItemCommandService.create(domainReq)
                .map(apiResp -> {
                    ApiResponseOrderItem.Builder builder = ApiResponseOrderItem.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.setData(toProto(apiResp.data()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> {
                    if (e instanceof ResourceNotFoundException) {
                        return Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException();
                    }
                    return Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException();
                });
    }

    @Override
    public Uni<ApiResponseOrderItem> updateOrderItem(OrderItemCommand.UpdateOrderItemRecordRequest request) {
        UpdateOrderItemRequest domainReq = new UpdateOrderItemRequest();
        domainReq.setOrderItemId(request.getOrderItemId());
        domainReq.setQuantity(request.getQuantity());
        domainReq.setPrice(request.getPrice());

        return orderItemCommandService.update(domainReq)
                .map(apiResp -> {
                    ApiResponseOrderItem.Builder builder = ApiResponseOrderItem.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.setData(toProto(apiResp.data()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> {
                    if (e instanceof ResourceNotFoundException) {
                        return Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException();
                    }
                    return Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException();
                });
    }

    @Override
    public Uni<ApiResponseOrderItem> trashOrderItem(FindByIdOrderItemRequest request) {
        return orderItemCommandService.trash((long) request.getId())
                .map(apiResp -> {
                    ApiResponseOrderItem.Builder builder = ApiResponseOrderItem.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.setData(toProto(apiResp.data()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> {
                    if (e instanceof ResourceNotFoundException) {
                        return Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException();
                    }
                    return Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException();
                });
    }

    @Override
    public Uni<ApiResponseOrderItem> restoreOrderItem(FindByIdOrderItemRequest request) {
        return orderItemCommandService.restore((long) request.getId())
                .map(apiResp -> {
                    ApiResponseOrderItem.Builder builder = ApiResponseOrderItem.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.setData(toProto(apiResp.data()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> {
                    if (e instanceof ResourceNotFoundException) {
                        return Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException();
                    }
                    return Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException();
                });
    }

    @Override
    public Uni<ApiResponseOrderItemDelete> deleteOrderItemPermanent(FindByIdOrderItemRequest request) {
        return orderItemCommandService.deletePermanent((long) request.getId())
                .map(apiResp -> ApiResponseOrderItemDelete.newBuilder()
                        .setStatus(apiResp.status())
                        .setMessage(apiResp.message())
                        .build())
                .onFailure().transform(e -> {
                    if (e instanceof ResourceNotFoundException) {
                        return Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException();
                    }
                    return Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException();
                });
    }

    @Override
    public Uni<ApiResponseOrderItemAll> restoreAllOrdersItem(com.google.protobuf.Empty request) {
        return orderItemCommandService.restoreAll()
                .map(apiResp -> ApiResponseOrderItemAll.newBuilder()
                        .setStatus(apiResp.status())
                        .setMessage(apiResp.message())
                        .build())
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseOrderItemAll> deleteAllPermanentOrdersItem(com.google.protobuf.Empty request) {
        return orderItemCommandService.deleteAll()
                .map(apiResp -> ApiResponseOrderItemAll.newBuilder()
                        .setStatus(apiResp.status())
                        .setMessage(apiResp.message())
                        .build())
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseOrderItemDelete> deleteOrderItemByOrderPermanent(FindByIdOrderItemRequest request) {
        return orderItemCommandService.deleteByOrderPermanent((long) request.getId())
                .map(apiResp -> ApiResponseOrderItemDelete.newBuilder()
                        .setStatus(apiResp.status())
                        .setMessage(apiResp.message())
                        .build())
                .onFailure().transform(e -> {
                    if (e instanceof ResourceNotFoundException) {
                        return Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException();
                    }
                    return Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException();
                });
    }

    @Override
    public Uni<OrderItemCommand.CalculateTotalPriceResponse> calculateTotalPrice(OrderItemCommand.CalculateTotalPriceRequest request) {
        return orderItemCommandService.calculateTotalPrice((long) request.getOrderId())
                .map(apiResp -> OrderItemCommand.CalculateTotalPriceResponse.newBuilder()
                        .setStatus(apiResp.status())
                        .setMessage(apiResp.message())
                        .setTotalPrice(apiResp.data() != null ? apiResp.data() : 0)
                        .build())
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    private pb.order_item.OrderItemCommon.OrderItemResponse toProto(OrderItemResponse r) {
        if (r == null) {
            return pb.order_item.OrderItemCommon.OrderItemResponse.getDefaultInstance();
        }
        pb.order_item.OrderItemCommon.OrderItemResponse.Builder builder = pb.order_item.OrderItemCommon.OrderItemResponse.newBuilder();
        if (r.getId() != null) {
            builder.setId(r.getId().intValue());
        }
        if (r.getOrderId() != null) {
            builder.setOrderId(r.getOrderId());
        }
        if (r.getProductId() != null) {
            builder.setProductId(r.getProductId());
        }
        if (r.getQuantity() != null) {
            builder.setQuantity(r.getQuantity());
        }
        if (r.getPrice() != null) {
            builder.setPrice(r.getPrice());
        }
        if (r.getCreatedAt() != null) {
            builder.setCreatedAt(r.getCreatedAt());
        }
        if (r.getUpdatedAt() != null) {
            builder.setUpdatedAt(r.getUpdatedAt());
        }
        return builder.build();
    }

    private pb.order_item.OrderItemCommon.OrderItemResponse toProto(OrderItemResponseDeleteAt r) {
        if (r == null) {
            return pb.order_item.OrderItemCommon.OrderItemResponse.getDefaultInstance();
        }
        pb.order_item.OrderItemCommon.OrderItemResponse.Builder builder = pb.order_item.OrderItemCommon.OrderItemResponse.newBuilder();
        if (r.getId() != null) {
            builder.setId(r.getId().intValue());
        }
        if (r.getOrderId() != null) {
            builder.setOrderId(r.getOrderId());
        }
        if (r.getProductId() != null) {
            builder.setProductId(r.getProductId());
        }
        if (r.getQuantity() != null) {
            builder.setQuantity(r.getQuantity());
        }
        if (r.getPrice() != null) {
            builder.setPrice(r.getPrice());
        }
        if (r.getCreatedAt() != null) {
            builder.setCreatedAt(r.getCreatedAt());
        }
        if (r.getUpdatedAt() != null) {
            builder.setUpdatedAt(r.getUpdatedAt());
        }
        return builder.build();
    }
}
