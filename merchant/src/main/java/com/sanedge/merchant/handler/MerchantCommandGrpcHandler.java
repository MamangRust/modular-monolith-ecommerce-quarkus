package com.sanedge.merchant.handler;

import com.sanedge.merchant.service.MerchantCommandService;
import com.sanedge.merchant.service.MerchantQueryService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.merchant.domain.response.MerchantResponse;
import com.sanedge.merchant.domain.response.MerchantResponseDeleteAt;

import pb.merchant.MutinyMerchantCommandServiceGrpc;
import pb.merchant.MerchantCommand.CreateMerchantRequest;
import pb.merchant.MerchantCommand.UpdateMerchantRequest;
import pb.merchant.MerchantCommand.UpdateMerchantStatusRequest;
import pb.merchant.MerchantCommon.ApiResponseMerchantDelete;
import pb.merchant.MerchantCommon.ApiResponseMerchantAll;
import pb.merchant.MerchantCommon.FindByIdMerchantRequest;
import pb.merchant.MerchantCommon.ApiResponseMerchant;
import pb.merchant.MerchantCommon.ApiResponseMerchantDeleteAt;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import com.google.protobuf.Empty;

@GrpcService
@Singleton
public class MerchantCommandGrpcHandler extends MutinyMerchantCommandServiceGrpc.MerchantCommandServiceImplBase {

    @Inject
    MerchantCommandService merchantCommandService;

    @Inject
    MerchantQueryService merchantQueryService;

    @Override
    public Uni<ApiResponseMerchant> create(CreateMerchantRequest request) {
        com.sanedge.merchant.domain.requests.CreateMerchantRequest domainReq = new com.sanedge.merchant.domain.requests.CreateMerchantRequest();
        domainReq.setName(request.getName());
        domainReq.setUserId((long) request.getUserId());

        return merchantCommandService.createMerchant(domainReq)
                .map(apiResp -> {
                    ApiResponseMerchant.Builder builder = ApiResponseMerchant.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.setData(toProto(apiResp.data()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseMerchant> update(UpdateMerchantRequest request) {
        com.sanedge.merchant.domain.requests.UpdateMerchantRequest domainReq = new com.sanedge.merchant.domain.requests.UpdateMerchantRequest();
        domainReq.setMerchantId((long) request.getMerchantId());
        domainReq.setName(request.getName());
        domainReq.setUserId((long) request.getUserId());
        domainReq.setStatus(request.getStatus());

        return merchantCommandService.updateMerchant(domainReq)
                .map(apiResp -> {
                    ApiResponseMerchant.Builder builder = ApiResponseMerchant.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.setData(toProto(apiResp.data()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseMerchant> updateStatus(UpdateMerchantStatusRequest request) {
        return merchantQueryService.findById((long) request.getMerchantId())
                .chain(apiResp -> {
                    if (apiResp == null || apiResp.data() == null) {
                        return Uni.createFrom().failure(Status.NOT_FOUND.withDescription("Merchant not found").asRuntimeException());
                    }
                    MerchantResponse existing = apiResp.data();
                    com.sanedge.merchant.domain.requests.UpdateMerchantRequest domainReq = new com.sanedge.merchant.domain.requests.UpdateMerchantRequest();
                    domainReq.setMerchantId((long) request.getMerchantId());
                    domainReq.setName(existing.getName());
                    domainReq.setUserId(existing.getUserId().longValue());
                    domainReq.setStatus(request.getStatus());

                    return merchantCommandService.updateMerchant(domainReq)
                            .map(updateResp -> {
                                ApiResponseMerchant.Builder builder = ApiResponseMerchant.newBuilder()
                                        .setStatus(updateResp.status())
                                        .setMessage(updateResp.message());
                                if (updateResp.data() != null) {
                                    builder.setData(toProto(updateResp.data()));
                                }
                                return builder.build();
                            });
                })
                .onFailure().transform(e -> {
                    if (e instanceof io.grpc.StatusRuntimeException) {
                        return e;
                    }
                    return Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException();
                });
    }

    @Override
    public Uni<ApiResponseMerchantDeleteAt> trashedMerchant(FindByIdMerchantRequest request) {
        return merchantCommandService.trashMerchant((long) request.getId())
                .map(apiResp -> {
                    ApiResponseMerchantDeleteAt.Builder builder = ApiResponseMerchantDeleteAt.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.setData(toProtoDeleteAt(apiResp.data()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseMerchant> restoreMerchant(FindByIdMerchantRequest request) {
        return merchantCommandService.restoreMerchant((long) request.getId())
                .map(apiResp -> {
                    ApiResponseMerchant.Builder builder = ApiResponseMerchant.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.setData(toProto(apiResp.data()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseMerchantDelete> deleteMerchantPermanent(FindByIdMerchantRequest request) {
        return merchantCommandService.deleteMerchant((long) request.getId())
                .map(apiResp -> ApiResponseMerchantDelete.newBuilder()
                        .setStatus(apiResp.status())
                        .setMessage(apiResp.message())
                        .build())
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseMerchantAll> restoreAllMerchant(Empty request) {
        return merchantCommandService.restoreAll()
                .map(apiResp -> ApiResponseMerchantAll.newBuilder()
                        .setStatus(apiResp.status())
                        .setMessage(apiResp.message())
                        .build())
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseMerchantAll> deleteAllMerchantPermanent(Empty request) {
        return merchantCommandService.deleteAll()
                .map(apiResp -> ApiResponseMerchantAll.newBuilder()
                        .setStatus(apiResp.status())
                        .setMessage(apiResp.message())
                        .build())
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    private pb.merchant.MerchantCommon.MerchantResponse toProto(MerchantResponse r) {
        if (r == null) {
            return pb.merchant.MerchantCommon.MerchantResponse.getDefaultInstance();
        }
        pb.merchant.MerchantCommon.MerchantResponse.Builder builder = pb.merchant.MerchantCommon.MerchantResponse.newBuilder();
        if (r.getId() != null) {
            builder.setId(r.getId().intValue());
        }
        if (r.getName() != null) {
            builder.setName(r.getName());
        }
        if (r.getStatus() != null) {
            builder.setStatus(r.getStatus());
        }
        if (r.getUserId() != null) {
            builder.setUserId(r.getUserId().intValue());
        }
        if (r.getCreatedAt() != null) {
            builder.setCreatedAt(r.getCreatedAt());
        }
        if (r.getUpdatedAt() != null) {
            builder.setUpdatedAt(r.getUpdatedAt());
        }
        return builder.build();
    }

    private pb.merchant.MerchantCommon.MerchantResponse toProto(MerchantResponseDeleteAt r) {
        if (r == null) {
            return pb.merchant.MerchantCommon.MerchantResponse.getDefaultInstance();
        }
        pb.merchant.MerchantCommon.MerchantResponse.Builder builder = pb.merchant.MerchantCommon.MerchantResponse.newBuilder();
        if (r.getId() != null) {
            builder.setId(r.getId().intValue());
        }
        if (r.getName() != null) {
            builder.setName(r.getName());
        }
        if (r.getStatus() != null) {
            builder.setStatus(r.getStatus());
        }
        if (r.getUserId() != null) {
            builder.setUserId(r.getUserId().intValue());
        }
        if (r.getCreatedAt() != null) {
            builder.setCreatedAt(r.getCreatedAt());
        }
        if (r.getUpdatedAt() != null) {
            builder.setUpdatedAt(r.getUpdatedAt());
        }
        return builder.build();
    }

    private pb.merchant.MerchantCommon.MerchantResponseDeleteAt toProtoDeleteAt(MerchantResponseDeleteAt r) {
        if (r == null) {
            return pb.merchant.MerchantCommon.MerchantResponseDeleteAt.getDefaultInstance();
        }
        pb.merchant.MerchantCommon.MerchantResponseDeleteAt.Builder builder = pb.merchant.MerchantCommon.MerchantResponseDeleteAt.newBuilder();
        if (r.getId() != null) {
            builder.setId(r.getId().intValue());
        }
        if (r.getName() != null) {
            builder.setName(r.getName());
        }
        if (r.getStatus() != null) {
            builder.setStatus(r.getStatus());
        }
        if (r.getUserId() != null) {
            builder.setUserId(r.getUserId().intValue());
        }
        if (r.getCreatedAt() != null) {
            builder.setCreatedAt(r.getCreatedAt());
        }
        if (r.getUpdatedAt() != null) {
            builder.setUpdatedAt(r.getUpdatedAt());
        }
        if (r.getDeletedAt() != null) {
            builder.setDeletedAt(com.google.protobuf.StringValue.of(r.getDeletedAt()));
        }
        return builder.build();
    }
}
