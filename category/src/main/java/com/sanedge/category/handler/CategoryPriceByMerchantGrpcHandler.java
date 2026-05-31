package com.sanedge.category.handler;

import java.util.stream.Collectors;

import com.sanedge.category.domain.requests.MonthPriceMerchantRequest;
import com.sanedge.category.domain.requests.YearPriceMerchantRequest;
import com.sanedge.category.service.stats.CategoryPriceByMerchantService;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import pb.category.stats.MutinyCategoryPriceByMerchantGrpc;
import pb.category.CategoryCommon.ApiResponseCategoryMonthPrice;
import pb.category.CategoryCommon.ApiResponseCategoryYearPrice;
import pb.category.CategoryCommon.FindYearCategoryByMerchant;

@GrpcService
@Singleton
public class CategoryPriceByMerchantGrpcHandler extends MutinyCategoryPriceByMerchantGrpc.CategoryPriceByMerchantImplBase {

    @Inject
    CategoryPriceByMerchantService categoryPriceByMerchantService;

    @Override
    public Uni<ApiResponseCategoryMonthPrice> findMonthPriceByMerchant(FindYearCategoryByMerchant request) {
        MonthPriceMerchantRequest domainReq = new MonthPriceMerchantRequest();
        domainReq.setYear(request.getYear());
        domainReq.setMerchantId(request.getMerchantId());

        return categoryPriceByMerchantService.findMonthPriceByMerchant(domainReq)
                .map(apiResp -> {
                    ApiResponseCategoryMonthPrice.Builder builder = ApiResponseCategoryMonthPrice.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.addAllData(apiResp.data().stream()
                                .map(d -> pb.category.CategoryCommon.CategoryMonthPriceResponse.newBuilder()
                                        .setMonth(d.getMonth())
                                        .setCategoryId(d.getCategoryId().intValue())
                                        .setCategoryName(d.getCategoryName())
                                        .setOrderCount(d.getOrderCount().intValue())
                                        .setItemsSold(d.getItemsSold().intValue())
                                        .setTotalRevenue(d.getTotalRevenue().intValue())
                                        .build())
                                .collect(Collectors.toList()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseCategoryYearPrice> findYearPriceByMerchant(FindYearCategoryByMerchant request) {
        YearPriceMerchantRequest domainReq = new YearPriceMerchantRequest();
        domainReq.setYear(request.getYear());
        domainReq.setMerchantId(request.getMerchantId());

        return categoryPriceByMerchantService.findYearPriceByMerchant(domainReq)
                .map(apiResp -> {
                    ApiResponseCategoryYearPrice.Builder builder = ApiResponseCategoryYearPrice.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.addAllData(apiResp.data().stream()
                                .map(d -> pb.category.CategoryCommon.CategoryYearPriceResponse.newBuilder()
                                        .setYear(d.getYear())
                                        .setCategoryId(d.getCategoryId().intValue())
                                        .setCategoryName(d.getCategoryName())
                                        .setOrderCount(d.getOrderCount().intValue())
                                        .setItemsSold(d.getItemsSold().intValue())
                                        .setTotalRevenue(d.getTotalRevenue().intValue())
                                        .setUniqueProductsSold(d.getUniqueProductsSold().intValue())
                                        .build())
                                .collect(Collectors.toList()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }
}
