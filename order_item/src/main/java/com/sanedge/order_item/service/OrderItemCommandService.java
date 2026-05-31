package com.sanedge.order_item.service;

import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.order_item.domain.requests.CreateOrderItemRequest;
import com.sanedge.order_item.domain.requests.UpdateOrderItemRequest;
import com.sanedge.order_item.domain.response.OrderItemResponse;
import com.sanedge.order_item.domain.response.OrderItemResponseDeleteAt;
import io.smallrye.mutiny.Uni;

public interface OrderItemCommandService {
    Uni<ApiResponse<OrderItemResponse>> create(CreateOrderItemRequest request);
    Uni<ApiResponse<OrderItemResponse>> update(UpdateOrderItemRequest request);
    Uni<ApiResponse<OrderItemResponseDeleteAt>> trash(Long id);
    Uni<ApiResponse<OrderItemResponseDeleteAt>> restore(Long id);
    Uni<ApiResponse<Boolean>> deletePermanent(Long id);
    Uni<ApiResponse<Boolean>> restoreAll();
    Uni<ApiResponse<Boolean>> deleteAll();
    Uni<ApiResponse<Boolean>> deleteByOrderPermanent(Long orderId);
    Uni<ApiResponse<Integer>> calculateTotalPrice(Long orderId);
}
