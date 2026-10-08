package com.sanedge.order.handler;

import java.util.stream.Collectors;

import com.sanedge.order.domain.response.OrderMonthlyResponse;
import com.sanedge.order.domain.response.OrderYearlyResponse;
import com.sanedge.order.service.OrderSoldoutService;

import io.grpc.Status;
import com.sanedge.common.grpc.GrpcErrorMapper;
import io.quarkus.grpc.GrpcService;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import pb.order.stats.MutinyOrderRevenueServiceGrpc;
import pb.order.OrderCommon.ApiResponseOrderMonthly;
import pb.order.OrderCommon.ApiResponseOrderYearly;
import pb.order.stats.OrderRevenue.FindYearOrder;

@GrpcService
@Singleton
public class OrderRevenueGrpcHandler extends MutinyOrderRevenueServiceGrpc.OrderRevenueServiceImplBase {

    @Inject
    OrderSoldoutService orderSoldoutService;

    @Override
    @WithSession
    public Uni<ApiResponseOrderMonthly> findMonthlyRevenue(FindYearOrder request) {
        return orderSoldoutService.findMonthlyOrders(request.getYear(), request.getMonth())
                .map(apiResp -> {
                    ApiResponseOrderMonthly.Builder builder = ApiResponseOrderMonthly.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.addAllData(apiResp.data().stream().map(this::toProto).collect(Collectors.toList()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> GrpcErrorMapper.toStatusRuntimeException(e));
    }

    @Override
    @WithSession
    public Uni<ApiResponseOrderYearly> findYearlyRevenue(FindYearOrder request) {
        return orderSoldoutService.findYearlyOrders(request.getYear())
                .map(apiResp -> {
                    ApiResponseOrderYearly.Builder builder = ApiResponseOrderYearly.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.addAllData(apiResp.data().stream().map(this::toProto).collect(Collectors.toList()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> GrpcErrorMapper.toStatusRuntimeException(e));
    }

    private pb.order.OrderCommon.OrderMonthlyResponse toProto(OrderMonthlyResponse r) {
        if (r == null) {
            return pb.order.OrderCommon.OrderMonthlyResponse.getDefaultInstance();
        }
        pb.order.OrderCommon.OrderMonthlyResponse.Builder builder = pb.order.OrderCommon.OrderMonthlyResponse
                .newBuilder();
        if (r.getMonth() != null) {
            builder.setMonth(r.getMonth());
        }
        if (r.getOrderCount() != null) {
            builder.setOrderCount(r.getOrderCount());
        }
        if (r.getTotalRevenue() != null) {
            builder.setTotalRevenue(r.getTotalRevenue());
        }
        if (r.getTotalItemsSold() != null) {
            builder.setTotalItemsSold(r.getTotalItemsSold());
        }
        return builder.build();
    }

    private pb.order.OrderCommon.OrderYearlyResponse toProto(OrderYearlyResponse r) {
        if (r == null) {
            return pb.order.OrderCommon.OrderYearlyResponse.getDefaultInstance();
        }
        pb.order.OrderCommon.OrderYearlyResponse.Builder builder = pb.order.OrderCommon.OrderYearlyResponse
                .newBuilder();
        if (r.getYear() != null) {
            builder.setYear(r.getYear());
        }
        if (r.getOrderCount() != null) {
            builder.setOrderCount(r.getOrderCount());
        }
        if (r.getTotalRevenue() != null) {
            builder.setTotalRevenue(r.getTotalRevenue());
        }
        if (r.getTotalItemsSold() != null) {
            builder.setTotalItemsSold(r.getTotalItemsSold());
        }
        if (r.getActiveCashiers() != null) {
            builder.setActiveCashiers(r.getActiveCashiers());
        }
        if (r.getUniqueProductsSold() != null) {
            builder.setUniqueProductsSold(r.getUniqueProductsSold());
        }
        return builder.build();
    }
}
