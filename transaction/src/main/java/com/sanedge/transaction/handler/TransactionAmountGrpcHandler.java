package com.sanedge.transaction.handler;

import java.util.stream.Collectors;

import com.sanedge.transaction.domain.requests.MonthAmountTransactionRequest;
import com.sanedge.transaction.service.stats.TransactionAmountService;

import io.grpc.Status;
import com.sanedge.common.grpc.GrpcErrorMapper;
import io.quarkus.grpc.GrpcService;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import pb.transaction.stats.MutinyTransactionAmountServiceGrpc;
import pb.transaction.TransactionCommon.ApiResponseTransactionMonthAmountFailed;
import pb.transaction.TransactionCommon.ApiResponseTransactionMonthAmountSuccess;
import pb.transaction.TransactionCommon.ApiResponseTransactionYearAmountFailed;
import pb.transaction.TransactionCommon.ApiResponseTransactionYearAmountSuccess;
import pb.transaction.TransactionCommon.TransactionMonthlyAmountFailed;
import pb.transaction.TransactionCommon.TransactionMonthlyAmountSuccess;
import pb.transaction.TransactionCommon.TransactionYearlyAmountFailed;
import pb.transaction.TransactionCommon.TransactionYearlyAmountSuccess;
import pb.transaction.stats.TransactionAmount.YearAmountTransactionRequest;

@GrpcService
@Singleton
public class TransactionAmountGrpcHandler extends MutinyTransactionAmountServiceGrpc.TransactionAmountServiceImplBase {

    @Inject
    TransactionAmountService transactionAmountService;

    @Override
    @WithSession
    public Uni<ApiResponseTransactionMonthAmountSuccess> getMonthlyAmountSuccess(pb.transaction.stats.TransactionAmount.MonthAmountTransactionRequest request) {
        MonthAmountTransactionRequest domainReq = new MonthAmountTransactionRequest();
        domainReq.setYear(request.getYear());
        domainReq.setMonth(request.getMonth());

        return transactionAmountService.findMonthlyAmountSuccess(domainReq)
                .map(apiResp -> {
                    ApiResponseTransactionMonthAmountSuccess.Builder builder = ApiResponseTransactionMonthAmountSuccess.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.addAllData(apiResp.data().stream().map(d -> TransactionMonthlyAmountSuccess.newBuilder()
                                .setYear(d.getYear())
                                .setMonth(d.getMonth())
                                .setTotalSuccess(d.getTotalSuccess().intValue())
                                .setTotalAmount(d.getTotalAmount().intValue())
                                .build()).collect(Collectors.toList()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> GrpcErrorMapper.toStatusRuntimeException(e));
    }

    @Override
    @WithSession
    public Uni<ApiResponseTransactionYearAmountSuccess> getYearlyAmountSuccess(YearAmountTransactionRequest request) {
        return transactionAmountService.findYearlyAmountSuccess(request.getYear())
                .map(apiResp -> {
                    ApiResponseTransactionYearAmountSuccess.Builder builder = ApiResponseTransactionYearAmountSuccess.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.addAllData(apiResp.data().stream().map(d -> TransactionYearlyAmountSuccess.newBuilder()
                                .setYear(d.getYear())
                                .setTotalSuccess(d.getTotalSuccess().intValue())
                                .setTotalAmount(d.getTotalAmount().intValue())
                                .build()).collect(Collectors.toList()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> GrpcErrorMapper.toStatusRuntimeException(e));
    }

    @Override
    @WithSession
    public Uni<ApiResponseTransactionMonthAmountFailed> getMonthlyAmountFailed(pb.transaction.stats.TransactionAmount.MonthAmountTransactionRequest request) {
        MonthAmountTransactionRequest domainReq = new MonthAmountTransactionRequest();
        domainReq.setYear(request.getYear());
        domainReq.setMonth(request.getMonth());

        return transactionAmountService.findMonthlyAmountFailed(domainReq)
                .map(apiResp -> {
                    ApiResponseTransactionMonthAmountFailed.Builder builder = ApiResponseTransactionMonthAmountFailed.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.addAllData(apiResp.data().stream().map(d -> TransactionMonthlyAmountFailed.newBuilder()
                                .setYear(d.getYear())
                                .setMonth(d.getMonth())
                                .setTotalFailed(d.getTotalFailed().intValue())
                                .setTotalAmount(d.getTotalAmount().intValue())
                                .build()).collect(Collectors.toList()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> GrpcErrorMapper.toStatusRuntimeException(e));
    }

    @Override
    @WithSession
    public Uni<ApiResponseTransactionYearAmountFailed> getYearlyAmountFailed(YearAmountTransactionRequest request) {
        return transactionAmountService.findYearlyAmountFailed(request.getYear())
                .map(apiResp -> {
                    ApiResponseTransactionYearAmountFailed.Builder builder = ApiResponseTransactionYearAmountFailed.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.addAllData(apiResp.data().stream().map(d -> TransactionYearlyAmountFailed.newBuilder()
                                .setYear(d.getYear())
                                .setTotalFailed(d.getTotalFailed().intValue())
                                .setTotalAmount(d.getTotalAmount().intValue())
                                .build()).collect(Collectors.toList()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> GrpcErrorMapper.toStatusRuntimeException(e));
    }
}
