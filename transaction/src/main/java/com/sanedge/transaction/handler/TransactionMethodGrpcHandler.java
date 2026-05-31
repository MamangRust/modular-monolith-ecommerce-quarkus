package com.sanedge.transaction.handler;

import java.util.stream.Collectors;

import com.sanedge.transaction.service.stats.TransactionMethodService;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import pb.transaction.stats.MutinyTransactionMethodServiceGrpc;
import pb.transaction.TransactionCommon.ApiResponseTransactionMonthPaymentMethod;
import pb.transaction.TransactionCommon.ApiResponseTransactionYearPaymentmethod;
import pb.transaction.TransactionCommon.TransactionMonthlyMethod;
import pb.transaction.TransactionCommon.TransactionYearlyMethod;
import pb.transaction.stats.TransactionMethod.MonthMethodTransactionRequest;
import pb.transaction.stats.TransactionMethod.YearMethodTransactionRequest;

@GrpcService
@Singleton
public class TransactionMethodGrpcHandler extends MutinyTransactionMethodServiceGrpc.TransactionMethodServiceImplBase {

    @Inject
    TransactionMethodService transactionMethodService;

    @Override
    public Uni<ApiResponseTransactionMonthPaymentMethod> getMonthlyTransactionMethodSuccess(
            MonthMethodTransactionRequest request) {
        com.sanedge.transaction.domain.requests.MonthMethodTransactionRequest domainReq = new com.sanedge.transaction.domain.requests.MonthMethodTransactionRequest();
        domainReq.setYear(request.getYear());
        domainReq.setMonth(request.getMonth());

        return transactionMethodService.findMonthlyMethodSuccess(domainReq)
                .map(apiResp -> {
                    ApiResponseTransactionMonthPaymentMethod.Builder builder = ApiResponseTransactionMonthPaymentMethod
                            .newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.addAllData(apiResp.data().stream().map(d -> TransactionMonthlyMethod.newBuilder()
                                .setMonth(d.getMonth())
                                .setPaymentMethod(d.getPaymentMethod())
                                .setTotalTransactions(d.getTotalTransactions().intValue())
                                .setTotalAmount(d.getTotalAmount().intValue())
                                .build()).collect(Collectors.toList()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseTransactionYearPaymentmethod> getYearlyTransactionMethodSuccess(
            YearMethodTransactionRequest request) {
        return transactionMethodService.findYearlyMethodSuccess(request.getYear())
                .map(apiResp -> {
                    ApiResponseTransactionYearPaymentmethod.Builder builder = ApiResponseTransactionYearPaymentmethod
                            .newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.addAllData(apiResp.data().stream().map(d -> TransactionYearlyMethod.newBuilder()
                                .setYear(d.getYear())
                                .setPaymentMethod(d.getPaymentMethod())
                                .setTotalTransactions(d.getTotalTransactions().intValue())
                                .setTotalAmount(d.getTotalAmount().intValue())
                                .build()).collect(Collectors.toList()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseTransactionMonthPaymentMethod> getMonthlyTransactionMethodFailed(
            MonthMethodTransactionRequest request) {
        com.sanedge.transaction.domain.requests.MonthMethodTransactionRequest domainReq = new com.sanedge.transaction.domain.requests.MonthMethodTransactionRequest();
        domainReq.setYear(request.getYear());
        domainReq.setMonth(request.getMonth());

        return transactionMethodService.findMonthlyMethodFailed(domainReq)
                .map(apiResp -> {
                    ApiResponseTransactionMonthPaymentMethod.Builder builder = ApiResponseTransactionMonthPaymentMethod
                            .newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.addAllData(apiResp.data().stream().map(d -> TransactionMonthlyMethod.newBuilder()
                                .setMonth(d.getMonth())
                                .setPaymentMethod(d.getPaymentMethod())
                                .setTotalTransactions(d.getTotalTransactions().intValue())
                                .setTotalAmount(d.getTotalAmount().intValue())
                                .build()).collect(Collectors.toList()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseTransactionYearPaymentmethod> getYearlyTransactionMethodFailed(
            YearMethodTransactionRequest request) {
        return transactionMethodService.findYearlyMethodFailed(request.getYear())
                .map(apiResp -> {
                    ApiResponseTransactionYearPaymentmethod.Builder builder = ApiResponseTransactionYearPaymentmethod
                            .newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.addAllData(apiResp.data().stream().map(d -> TransactionYearlyMethod.newBuilder()
                                .setYear(d.getYear())
                                .setPaymentMethod(d.getPaymentMethod())
                                .setTotalTransactions(d.getTotalTransactions().intValue())
                                .setTotalAmount(d.getTotalAmount().intValue())
                                .build()).collect(Collectors.toList()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }
}
