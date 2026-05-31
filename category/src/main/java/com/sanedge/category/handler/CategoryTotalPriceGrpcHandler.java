package com.sanedge.category.handler;

import java.util.stream.Collectors;

import com.sanedge.category.domain.requests.MonthTotalPriceRequest;
import com.sanedge.category.service.stats.CategoryTotalPriceService;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import pb.category.stats.MutinyCategoryTotalPriceServiceGrpc;
import pb.category.CategoryCommon.ApiResponseCategoryMonthlyTotalPrice;
import pb.category.CategoryCommon.ApiResponseCategoryYearlyTotalPrice;
import pb.category.CategoryCommon.FindYearMonthTotalPrices;
import pb.category.CategoryCommon.FindYearTotalPrices;

@GrpcService
@Singleton
public class CategoryTotalPriceGrpcHandler extends MutinyCategoryTotalPriceServiceGrpc.CategoryTotalPriceServiceImplBase {

    @Inject
    CategoryTotalPriceService categoryTotalPriceService;

    @Override
    public Uni<ApiResponseCategoryMonthlyTotalPrice> findMonthlyTotalPrices(FindYearMonthTotalPrices request) {
        MonthTotalPriceRequest domainReq = new MonthTotalPriceRequest();
        domainReq.setYear(request.getYear());
        domainReq.setMonth(request.getMonth());

        return categoryTotalPriceService.findMonthlyTotalPrice(domainReq)
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
    public Uni<ApiResponseCategoryYearlyTotalPrice> findYearlyTotalPrices(FindYearTotalPrices request) {
        return categoryTotalPriceService.findYearlyTotalPrice(request.getYear())
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
