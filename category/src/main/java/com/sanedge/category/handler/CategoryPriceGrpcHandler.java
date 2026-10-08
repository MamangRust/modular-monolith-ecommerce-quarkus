package com.sanedge.category.handler;

import java.util.stream.Collectors;

import com.sanedge.category.service.stats.CategoryPriceService;

import io.grpc.Status;
import com.sanedge.common.grpc.GrpcErrorMapper;
import io.quarkus.grpc.GrpcService;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import pb.category.stats.MutinyCategoryPriceServiceGrpc;
import pb.category.CategoryCommon.ApiResponseCategoryMonthPrice;
import pb.category.CategoryCommon.ApiResponseCategoryYearPrice;
import pb.category.CategoryCommon.FindYearCategory;

@GrpcService
@Singleton
public class CategoryPriceGrpcHandler extends MutinyCategoryPriceServiceGrpc.CategoryPriceServiceImplBase {

    @Inject
    CategoryPriceService categoryPriceService;

    @Override
    @WithSession
    public Uni<ApiResponseCategoryMonthPrice> findMonthPrice(FindYearCategory request) {
        return categoryPriceService.findMonthPrice(request.getYear())
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
                .onFailure().transform(e -> GrpcErrorMapper.toStatusRuntimeException(e));
    }

    @Override
    @WithSession
    public Uni<ApiResponseCategoryYearPrice> findYearPrice(FindYearCategory request) {
        return categoryPriceService.findYearPrice(request.getYear())
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
                .onFailure().transform(e -> GrpcErrorMapper.toStatusRuntimeException(e));
    }
}
