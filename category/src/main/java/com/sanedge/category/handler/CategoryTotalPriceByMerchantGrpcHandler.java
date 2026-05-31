package com.sanedge.category.handler;

import java.util.stream.Collectors;

import com.sanedge.category.domain.requests.MonthTotalPriceMerchantRequest;
import com.sanedge.category.domain.requests.YearTotalPriceMerchantRequest;
import com.sanedge.category.service.stats.CategoryTotalPriceByMerchantService;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import pb.category.stats.MutinyCategoryTotalPriceByMerchantGrpc;
import pb.category.CategoryCommon.ApiResponseCategoryMonthlyTotalPrice;
import pb.category.CategoryCommon.ApiResponseCategoryYearlyTotalPrice;
import pb.category.CategoryCommon.FindYearMonthTotalPriceByMerchant;
import pb.category.CategoryCommon.FindYearTotalPriceByMerchant;

@GrpcService
@Singleton
public class CategoryTotalPriceByMerchantGrpcHandler extends MutinyCategoryTotalPriceByMerchantGrpc.CategoryTotalPriceByMerchantImplBase {

    @Inject
    CategoryTotalPriceByMerchantService categoryTotalPriceByMerchantService;

    @Override
    public Uni<ApiResponseCategoryMonthlyTotalPrice> findMonthlyTotalPricesByMerchant(
            FindYearMonthTotalPriceByMerchant request) {
        MonthTotalPriceMerchantRequest domainReq = new MonthTotalPriceMerchantRequest();
        domainReq.setYear(request.getYear());
        domainReq.setMonth(request.getMonth());
        domainReq.setMerchantId((long) request.getMerchantId());

        return categoryTotalPriceByMerchantService.findMonthlyTotalPriceByMerchant(domainReq)
                .map(apiResp -> {
                    ApiResponseCategoryMonthlyTotalPrice.Builder builder = ApiResponseCategoryMonthlyTotalPrice
                            .newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.addAllData(apiResp.data().stream()
                                .map(d -> pb.category.CategoryCommon.CategoriesMonthlyTotalPriceResponse.newBuilder()
                                        .setYear(d.getYear())
                                        .setMonth(d.getMonth())
                                        .setTotalRevenue(d.getTotalRevenue().intValue())
                                        .build())
                                .collect(Collectors.toList()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseCategoryYearlyTotalPrice> findYearlyTotalPricesByMerchant(
            FindYearTotalPriceByMerchant request) {
        YearTotalPriceMerchantRequest domainReq = new YearTotalPriceMerchantRequest();
        domainReq.setYear(request.getYear());
        domainReq.setMerchantId(request.getMerchantId());

        return categoryTotalPriceByMerchantService.findYearlyTotalPriceByMerchant(domainReq)
                .map(apiResp -> {
                    ApiResponseCategoryYearlyTotalPrice.Builder builder = ApiResponseCategoryYearlyTotalPrice
                            .newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.addAllData(apiResp.data().stream()
                                .map(d -> pb.category.CategoryCommon.CategoriesYearlyTotalPriceResponse.newBuilder()
                                        .setYear(d.getYear())
                                        .setTotalRevenue(d.getTotalRevenue().intValue())
                                        .build())
                                .collect(Collectors.toList()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }
}
