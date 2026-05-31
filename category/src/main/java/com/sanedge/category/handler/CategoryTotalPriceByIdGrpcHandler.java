package com.sanedge.category.handler;

import java.util.stream.Collectors;

import com.sanedge.category.domain.requests.MonthTotalPriceIdRequest;
import com.sanedge.category.domain.requests.YearTotalPriceIdRequest;
import com.sanedge.category.service.stats.CategoryTotalPriceByIdService;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import pb.category.stats.MutinyCategoryTotalPriceByIdGrpc;
import pb.category.CategoryCommon.ApiResponseCategoryMonthlyTotalPrice;
import pb.category.CategoryCommon.ApiResponseCategoryYearlyTotalPrice;
import pb.category.CategoryCommon.FindYearMonthTotalPriceById;
import pb.category.CategoryCommon.FindYearTotalPriceById;

@GrpcService
@Singleton
public class CategoryTotalPriceByIdGrpcHandler extends MutinyCategoryTotalPriceByIdGrpc.CategoryTotalPriceByIdImplBase {

    @Inject
    CategoryTotalPriceByIdService categoryTotalPriceByIdService;

    @Override
    public Uni<ApiResponseCategoryMonthlyTotalPrice> findMonthlyTotalPricesById(FindYearMonthTotalPriceById request) {
        MonthTotalPriceIdRequest domainReq = new MonthTotalPriceIdRequest();
        domainReq.setYear(request.getYear());
        domainReq.setMonth(request.getMonth());
        domainReq.setCategoryId((long) request.getCategoryId());

        return categoryTotalPriceByIdService.findMonthlyTotalPriceById(domainReq)
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
    public Uni<ApiResponseCategoryYearlyTotalPrice> findYearlyTotalPricesById(FindYearTotalPriceById request) {
        YearTotalPriceIdRequest domainReq = new YearTotalPriceIdRequest();
        domainReq.setYear(request.getYear());
        domainReq.setCategoryId(request.getCategoryId());

        return categoryTotalPriceByIdService.findYearlyTotalPriceById(domainReq)
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
