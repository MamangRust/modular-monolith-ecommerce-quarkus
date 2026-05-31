package com.sanedge.transaction.handler;

import java.util.stream.Collectors;

import com.sanedge.transaction.service.statsbymerchant.TransactionMethodByMerchantService;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import pb.transaction.stats.MutinyTransactionMethodByMerchantServiceGrpc;
import pb.transaction.TransactionCommon.ApiResponseTransactionMonthPaymentMethod;
import pb.transaction.TransactionCommon.ApiResponseTransactionYearPaymentmethod;
import pb.transaction.TransactionCommon.TransactionMonthlyMethod;
import pb.transaction.TransactionCommon.TransactionYearlyMethod;
import pb.transaction.stats.TransactionMethod.MonthMethodTransactionMerchantRequest;
import pb.transaction.stats.TransactionMethod.YearMethodTransactionMerchantRequest;

@GrpcService
@Singleton
public class TransactionMethodByMerchantGrpcHandler
                extends MutinyTransactionMethodByMerchantServiceGrpc.TransactionMethodByMerchantServiceImplBase {

        @Inject
        TransactionMethodByMerchantService transactionMethodByMerchantService;

        @Override
        public Uni<ApiResponseTransactionMonthPaymentMethod> getMonthlyTransactionMethodByMerchantSuccess(
                        MonthMethodTransactionMerchantRequest request) {
                com.sanedge.transaction.domain.requests.MonthMethodTransactionMerchantRequest domainReq = new com.sanedge.transaction.domain.requests.MonthMethodTransactionMerchantRequest();
                domainReq.setMerchantId(request.getMerchantId());
                domainReq.setYear(request.getYear());
                domainReq.setMonth(request.getMonth());

                return transactionMethodByMerchantService.findMonthlyMethodByMerchantSuccess(domainReq)
                                .map(apiResp -> {
                                        ApiResponseTransactionMonthPaymentMethod.Builder builder = ApiResponseTransactionMonthPaymentMethod
                                                        .newBuilder()
                                                        .setStatus(apiResp.status())
                                                        .setMessage(apiResp.message());
                                        if (apiResp.data() != null) {
                                                builder.addAllData(apiResp.data().stream()
                                                                .map(d -> TransactionMonthlyMethod.newBuilder()
                                                                                .setMonth(d.getMonth())
                                                                                .setPaymentMethod(d.getPaymentMethod())
                                                                                .setTotalTransactions(d
                                                                                                .getTotalTransactions()
                                                                                                .intValue())
                                                                                .setTotalAmount(d.getTotalAmount()
                                                                                                .intValue())
                                                                                .build())
                                                                .collect(Collectors.toList()));
                                        }
                                        return builder.build();
                                })
                                .onFailure()
                                .transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }

        @Override
        public Uni<ApiResponseTransactionYearPaymentmethod> getYearlyTransactionMethodByMerchantSuccess(
                        YearMethodTransactionMerchantRequest request) {
                com.sanedge.transaction.domain.requests.YearMethodTransactionMerchantRequest domainReq = new com.sanedge.transaction.domain.requests.YearMethodTransactionMerchantRequest();
                domainReq.setMerchantId(request.getMerchantId());
                domainReq.setYear(request.getYear());

                return transactionMethodByMerchantService.findYearlyMethodByMerchantSuccess(domainReq)
                                .map(apiResp -> {
                                        ApiResponseTransactionYearPaymentmethod.Builder builder = ApiResponseTransactionYearPaymentmethod
                                                        .newBuilder()
                                                        .setStatus(apiResp.status())
                                                        .setMessage(apiResp.message());
                                        if (apiResp.data() != null) {
                                                builder.addAllData(apiResp.data().stream()
                                                                .map(d -> TransactionYearlyMethod.newBuilder()
                                                                                .setYear(d.getYear())
                                                                                .setPaymentMethod(d.getPaymentMethod())
                                                                                .setTotalTransactions(d
                                                                                                .getTotalTransactions()
                                                                                                .intValue())
                                                                                .setTotalAmount(d.getTotalAmount()
                                                                                                .intValue())
                                                                                .build())
                                                                .collect(Collectors.toList()));
                                        }
                                        return builder.build();
                                })
                                .onFailure()
                                .transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }

        @Override
        public Uni<ApiResponseTransactionMonthPaymentMethod> getMonthlyTransactionMethodByMerchantFailed(
                        MonthMethodTransactionMerchantRequest request) {
                com.sanedge.transaction.domain.requests.MonthMethodTransactionMerchantRequest domainReq = new com.sanedge.transaction.domain.requests.MonthMethodTransactionMerchantRequest();
                domainReq.setMerchantId(request.getMerchantId());
                domainReq.setYear(request.getYear());
                domainReq.setMonth(request.getMonth());

                return transactionMethodByMerchantService.findMonthlyMethodByMerchantFailed(domainReq)
                                .map(apiResp -> {
                                        ApiResponseTransactionMonthPaymentMethod.Builder builder = ApiResponseTransactionMonthPaymentMethod
                                                        .newBuilder()
                                                        .setStatus(apiResp.status())
                                                        .setMessage(apiResp.message());
                                        if (apiResp.data() != null) {
                                                builder.addAllData(apiResp.data().stream()
                                                                .map(d -> TransactionMonthlyMethod.newBuilder()
                                                                                .setMonth(d.getMonth())
                                                                                .setPaymentMethod(d.getPaymentMethod())
                                                                                .setTotalTransactions(d
                                                                                                .getTotalTransactions()
                                                                                                .intValue())
                                                                                .setTotalAmount(d.getTotalAmount()
                                                                                                .intValue())
                                                                                .build())
                                                                .collect(Collectors.toList()));
                                        }
                                        return builder.build();
                                })
                                .onFailure()
                                .transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }

        @Override
        public Uni<ApiResponseTransactionYearPaymentmethod> getYearlyTransactionMethodByMerchantFailed(
                        YearMethodTransactionMerchantRequest request) {
                com.sanedge.transaction.domain.requests.YearMethodTransactionMerchantRequest domainReq = new com.sanedge.transaction.domain.requests.YearMethodTransactionMerchantRequest();
                domainReq.setMerchantId(request.getMerchantId());
                domainReq.setYear(request.getYear());

                return transactionMethodByMerchantService.findYearlyMethodByMerchantFailed(domainReq)
                                .map(apiResp -> {
                                        ApiResponseTransactionYearPaymentmethod.Builder builder = ApiResponseTransactionYearPaymentmethod
                                                        .newBuilder()
                                                        .setStatus(apiResp.status())
                                                        .setMessage(apiResp.message());
                                        if (apiResp.data() != null) {
                                                builder.addAllData(apiResp.data().stream()
                                                                .map(d -> TransactionYearlyMethod.newBuilder()
                                                                                .setYear(d.getYear())
                                                                                .setPaymentMethod(d.getPaymentMethod())
                                                                                .setTotalTransactions(d
                                                                                                .getTotalTransactions()
                                                                                                .intValue())
                                                                                .setTotalAmount(d.getTotalAmount()
                                                                                                .intValue())
                                                                                .build())
                                                                .collect(Collectors.toList()));
                                        }
                                        return builder.build();
                                })
                                .onFailure()
                                .transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
}
