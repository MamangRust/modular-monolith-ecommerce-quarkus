package com.sanedge.order.handler;

import java.util.stream.Collectors;

import com.sanedge.order.domain.requests.MonthTotalRevenueMerchantRequest;
import com.sanedge.order.domain.requests.YearTotalRevenueMerchantRequest;
import com.sanedge.order.domain.response.OrderMonthlyTotalRevenueResponse;
import com.sanedge.order.domain.response.OrderYearlyTotalRevenueResponse;
import com.sanedge.order.service.OrderTotalRevenueByMerchantService;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import pb.order.stats.MutinyOrderTotalRevenueByMerchantGrpc;
import pb.order.OrderCommon.ApiResponseOrderMonthlyTotalRevenue;
import pb.order.OrderCommon.ApiResponseOrderYearlyTotalRevenue;
import pb.order.stats.OrderTotalRevenue.FindYearMonthTotalRevenueByMerchant;
import pb.order.stats.OrderTotalRevenue.FindYearTotalRevenueByMerchant;

@GrpcService
@Singleton
public class OrderTotalRevenueByMerchantGrpcHandler extends MutinyOrderTotalRevenueByMerchantGrpc.OrderTotalRevenueByMerchantImplBase {

    @Inject
    OrderTotalRevenueByMerchantService orderTotalRevenueByMerchantService;

    @Override
    public Uni<ApiResponseOrderMonthlyTotalRevenue> findMonthlyTotalRevenueByMerchant(
            FindYearMonthTotalRevenueByMerchant request) {
        MonthTotalRevenueMerchantRequest domainReq = new MonthTotalRevenueMerchantRequest();
        domainReq.setYear(request.getYear());
        domainReq.setMonth(request.getMonth());
        domainReq.setMerchantId(request.getMerchantId());

        return orderTotalRevenueByMerchantService.findMonthlyStatsByMerchant(domainReq)
                .map(apiResp -> {
                    ApiResponseOrderMonthlyTotalRevenue.Builder builder = ApiResponseOrderMonthlyTotalRevenue
                            .newBuilder()
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
    public Uni<ApiResponseOrderYearlyTotalRevenue> findYearlyTotalRevenueByMerchant(
            FindYearTotalRevenueByMerchant request) {
        YearTotalRevenueMerchantRequest domainReq = new YearTotalRevenueMerchantRequest();
        domainReq.setYear(request.getYear());
        domainReq.setMerchantId(request.getMerchantId());

        return orderTotalRevenueByMerchantService.findYearlyStatsByMerchant(domainReq)
                .map(apiResp -> {
                    ApiResponseOrderYearlyTotalRevenue.Builder builder = ApiResponseOrderYearlyTotalRevenue.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.addAllData(apiResp.data().stream().map(this::toProto).collect(Collectors.toList()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    private pb.order.OrderCommon.OrderMonthlyTotalRevenueResponse toProto(OrderMonthlyTotalRevenueResponse r) {
        if (r == null) {
            return pb.order.OrderCommon.OrderMonthlyTotalRevenueResponse.getDefaultInstance();
        }
        pb.order.OrderCommon.OrderMonthlyTotalRevenueResponse.Builder builder = pb.order.OrderCommon.OrderMonthlyTotalRevenueResponse
                .newBuilder();
        if (r.getYear() != null) {
            builder.setYear(r.getYear());
        }
        if (r.getMonth() != null) {
            builder.setMonth(r.getMonth());
        }
        if (r.getTotalRevenue() != null) {
            builder.setTotalRevenue(r.getTotalRevenue());
        }
        return builder.build();
    }

    private pb.order.OrderCommon.OrderYearlyTotalRevenueResponse toProto(OrderYearlyTotalRevenueResponse r) {
        if (r == null) {
            return pb.order.OrderCommon.OrderYearlyTotalRevenueResponse.getDefaultInstance();
        }
        pb.order.OrderCommon.OrderYearlyTotalRevenueResponse.Builder builder = pb.order.OrderCommon.OrderYearlyTotalRevenueResponse
                .newBuilder();
        if (r.getYear() != null) {
            builder.setYear(r.getYear());
        }
        if (r.getTotalRevenue() != null) {
            builder.setTotalRevenue(r.getTotalRevenue());
        }
        return builder.build();
    }
}
