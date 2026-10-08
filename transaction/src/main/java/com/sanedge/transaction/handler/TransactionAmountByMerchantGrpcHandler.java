package com.sanedge.transaction.handler;

import java.util.stream.Collectors;

import com.sanedge.transaction.domain.requests.FindMonthlyAmountByMerchantRequest;
import com.sanedge.transaction.domain.requests.YearAmountTransactionMerchant;
import com.sanedge.transaction.service.statsbymerchant.TransactionAmountByMerchantService;

import io.grpc.Status;
import com.sanedge.common.grpc.GrpcErrorMapper;
import io.quarkus.grpc.GrpcService;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import pb.transaction.stats.MutinyTransactionAmountByMerchantServiceGrpc;
import pb.transaction.TransactionCommon.ApiResponseTransactionMonthAmountFailed;
import pb.transaction.TransactionCommon.ApiResponseTransactionMonthAmountSuccess;
import pb.transaction.TransactionCommon.ApiResponseTransactionYearAmountFailed;
import pb.transaction.TransactionCommon.ApiResponseTransactionYearAmountSuccess;
import pb.transaction.TransactionCommon.TransactionMonthlyAmountFailed;
import pb.transaction.TransactionCommon.TransactionMonthlyAmountSuccess;
import pb.transaction.TransactionCommon.TransactionYearlyAmountFailed;
import pb.transaction.TransactionCommon.TransactionYearlyAmountSuccess;
import pb.transaction.stats.TransactionAmount.MonthAmountTransactionMerchantRequest;
import pb.transaction.stats.TransactionAmount.YearAmountTransactionMerchantRequest;
import com.sanedge.common.utils.IdValidator;

@GrpcService
@Singleton
public class TransactionAmountByMerchantGrpcHandler extends MutinyTransactionAmountByMerchantServiceGrpc.TransactionAmountByMerchantServiceImplBase {

    @Inject
    TransactionAmountByMerchantService transactionAmountByMerchantService;        @Override
        @WithSession
        public Uni<ApiResponseTransactionMonthAmountSuccess> getMonthlyAmountSuccessByMerchant(MonthAmountTransactionMerchantRequest request) {
        if (request.getMerchantId() <= 0) {
            return IdValidator.invalid("Merchant id");
        }
        FindMonthlyAmountByMerchantRequest domainReq = new FindMonthlyAmountByMerchantRequest();
        domainReq.setMerchantId((long) request.getMerchantId());
        domainReq.setYear(request.getYear());
        domainReq.setMonth(request.getMonth());

        return transactionAmountByMerchantService.findMonthlyAmountSuccessByMerchant(domainReq)
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
    }        @Override
        @WithSession
        public Uni<ApiResponseTransactionYearAmountSuccess> getYearlyAmountSuccessByMerchant(YearAmountTransactionMerchantRequest request) {
        if (request.getMerchantId() <= 0) {
            return IdValidator.invalid("Merchant id");
        }
        YearAmountTransactionMerchant domainReq = new YearAmountTransactionMerchant();
        domainReq.setMerchantId(request.getMerchantId());
        domainReq.setYear(request.getYear());

        return transactionAmountByMerchantService.findYearlyAmountSuccessByMerchant(domainReq)
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
    }        @Override
        @WithSession
        public Uni<ApiResponseTransactionMonthAmountFailed> getMonthlyAmountFailedByMerchant(MonthAmountTransactionMerchantRequest request) {
        if (request.getMerchantId() <= 0) {
            return IdValidator.invalid("Merchant id");
        }
        FindMonthlyAmountByMerchantRequest domainReq = new FindMonthlyAmountByMerchantRequest();
        domainReq.setMerchantId((long) request.getMerchantId());
        domainReq.setYear(request.getYear());
        domainReq.setMonth(request.getMonth());

        return transactionAmountByMerchantService.findMonthlyAmountFailedByMerchant(domainReq)
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
    }        @Override
        @WithSession
        public Uni<ApiResponseTransactionYearAmountFailed> getYearlyAmountFailedByMerchant(YearAmountTransactionMerchantRequest request) {
        if (request.getMerchantId() <= 0) {
            return IdValidator.invalid("Merchant id");
        }
        YearAmountTransactionMerchant domainReq = new YearAmountTransactionMerchant();
        domainReq.setMerchantId(request.getMerchantId());
        domainReq.setYear(request.getYear());

        return transactionAmountByMerchantService.findYearlyAmountFailedByMerchant(domainReq)
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
