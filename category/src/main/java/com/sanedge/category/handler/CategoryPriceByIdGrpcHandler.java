package com.sanedge.category.handler;

import java.util.stream.Collectors;

import com.sanedge.category.domain.requests.MonthPriceIdRequest;
import com.sanedge.category.domain.requests.YearPriceIdRequest;
import com.sanedge.category.service.stats.CategoryPriceByIdService;

import io.grpc.Status;
import com.sanedge.common.grpc.GrpcErrorMapper;
import io.quarkus.grpc.GrpcService;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import pb.category.stats.MutinyCategoryPriceByIdServiceGrpc;
import pb.category.CategoryCommon.ApiResponseCategoryMonthPrice;
import pb.category.CategoryCommon.ApiResponseCategoryYearPrice;
import pb.category.CategoryCommon.FindYearCategoryById;
import com.sanedge.common.utils.IdValidator;

@GrpcService
@Singleton
public class CategoryPriceByIdGrpcHandler extends MutinyCategoryPriceByIdServiceGrpc.CategoryPriceByIdServiceImplBase {

    @Inject
    CategoryPriceByIdService categoryPriceByIdService;

    @Override
    @WithSession
    public Uni<ApiResponseCategoryMonthPrice> findMonthPriceById(FindYearCategoryById request) {
        if (request.getCategoryId() <= 0) {
            return IdValidator.invalid("Category id");
        }
        MonthPriceIdRequest domainReq = new MonthPriceIdRequest();
        domainReq.setYear(request.getYear());
        domainReq.setCategoryId(request.getCategoryId());

        return categoryPriceByIdService.findMonthPriceById(domainReq)
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
    public Uni<ApiResponseCategoryYearPrice> findYearPriceById(FindYearCategoryById request) {
        if (request.getCategoryId() <= 0) {
            return IdValidator.invalid("Category id");
        }
        YearPriceIdRequest domainReq = new YearPriceIdRequest();
        domainReq.setYear(request.getYear());
        domainReq.setCategoryId(request.getCategoryId());

        return categoryPriceByIdService.findYearPriceById(domainReq)
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
