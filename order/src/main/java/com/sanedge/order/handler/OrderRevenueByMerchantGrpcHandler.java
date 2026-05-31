package com.sanedge.order.handler;

import java.util.stream.Collectors;

import com.sanedge.order.domain.requests.MonthOrderMerchantRequest;
import com.sanedge.order.domain.requests.YearOrderMerchantRequest;
import com.sanedge.order.domain.response.OrderMonthlyResponse;
import com.sanedge.order.domain.response.OrderYearlyResponse;
import com.sanedge.order.service.OrderSoldOutByMerchantService;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import pb.order.stats.MutinyOrderRevenueByMerchantGrpc;
import pb.order.OrderCommon.ApiResponseOrderMonthly;
import pb.order.OrderCommon.ApiResponseOrderYearly;
import pb.order.stats.OrderRevenue.FindYearOrderByMerchant;

@GrpcService
@Singleton
public class OrderRevenueByMerchantGrpcHandler extends MutinyOrderRevenueByMerchantGrpc.OrderRevenueByMerchantImplBase {

    @Inject
    OrderSoldOutByMerchantService orderSoldOutByMerchantService;

    @Override
    public Uni<ApiResponseOrderMonthly> findMonthlyRevenueByMerchant(FindYearOrderByMerchant request) {
        MonthOrderMerchantRequest domainReq = new MonthOrderMerchantRequest();
        domainReq.setYear(request.getYear());
        domainReq.setMonth(request.getMonth());
        domainReq.setMerchantId(request.getMerchantId());

        return orderSoldOutByMerchantService.findMonthlyOrdersByMerchant(domainReq)
                .map(apiResp -> {
                    ApiResponseOrderMonthly.Builder builder = ApiResponseOrderMonthly.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.addAllData(apiResp.data().stream().map(this::toProto).collect(Collectors.toList()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseOrderYearly> findYearlyRevenueByMerchant(FindYearOrderByMerchant request) {
        YearOrderMerchantRequest domainReq = new YearOrderMerchantRequest();
        domainReq.setYear(request.getYear());
        domainReq.setMerchantId(request.getMerchantId());

        return orderSoldOutByMerchantService.findYearlyOrdersByMerchant(domainReq)
                .map(apiResp -> {
                    ApiResponseOrderYearly.Builder builder = ApiResponseOrderYearly.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.addAllData(apiResp.data().stream().map(this::toProto).collect(Collectors.toList()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
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
