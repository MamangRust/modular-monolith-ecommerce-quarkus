package com.sanedge.transaction.handler;

import java.util.stream.Collectors;

import com.sanedge.transaction.service.statsbymerchant.TransactionMethodByMerchantService;

import io.grpc.Status;
import com.sanedge.common.grpc.GrpcErrorMapper;
import io.quarkus.grpc.GrpcService;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
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
import com.sanedge.common.utils.IdValidator;

@GrpcService
@Singleton
public class TransactionMethodByMerchantGrpcHandler
                extends MutinyTransactionMethodByMerchantServiceGrpc.TransactionMethodByMerchantServiceImplBase {

        @Inject
        TransactionMethodByMerchantService transactionMethodByMerchantService;

        @Override
        @WithSession
        public Uni<ApiResponseTransactionMonthPaymentMethod> getMonthlyTransactionMethodByMerchantSuccess(
                        MonthMethodTransactionMerchantRequest request) {
                if (request.getMerchantId() <= 0) {
                    return IdValidator.invalid("Merchant id");
                }
                com.sanedge.transaction.domain.requests.FindMonthlyMethodsByMerchantRequest domainReq = new com.sanedge.transaction.domain.requests.FindMonthlyMethodsByMerchantRequest();
                domainReq.setMerchantId((long) request.getMerchantId());
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
                                .transform(e -> GrpcErrorMapper.toStatusRuntimeException(e));
        }

        @Override
        @WithSession
        public Uni<ApiResponseTransactionYearPaymentmethod> getYearlyTransactionMethodByMerchantSuccess(
                        YearMethodTransactionMerchantRequest request) {
                if (request.getMerchantId() <= 0) {
                    return IdValidator.invalid("Merchant id");
                }
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
                                .transform(e -> GrpcErrorMapper.toStatusRuntimeException(e));
        }

        @Override
        @WithSession
        public Uni<ApiResponseTransactionMonthPaymentMethod> getMonthlyTransactionMethodByMerchantFailed(
                        MonthMethodTransactionMerchantRequest request) {
                if (request.getMerchantId() <= 0) {
                    return IdValidator.invalid("Merchant id");
                }
                com.sanedge.transaction.domain.requests.FindMonthlyMethodsByMerchantRequest domainReq = new com.sanedge.transaction.domain.requests.FindMonthlyMethodsByMerchantRequest();
                domainReq.setMerchantId((long) request.getMerchantId());
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
                                .transform(e -> GrpcErrorMapper.toStatusRuntimeException(e));
        }

        @Override
        @WithSession
        public Uni<ApiResponseTransactionYearPaymentmethod> getYearlyTransactionMethodByMerchantFailed(
                        YearMethodTransactionMerchantRequest request) {
                if (request.getMerchantId() <= 0) {
                    return IdValidator.invalid("Merchant id");
                }
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
                                .transform(e -> GrpcErrorMapper.toStatusRuntimeException(e));
        }
}
