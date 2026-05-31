package com.sanedge.role.handler;

import com.google.protobuf.Empty;
import com.sanedge.role.domain.response.RoleResponse;
import com.sanedge.role.domain.response.RoleResponseDeleteAt;
import com.sanedge.role.domain.response.UserRoleResponse;
import com.sanedge.role.service.RoleCommandService;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import pb.role.MutinyRoleCommandServiceGrpc;
import pb.role.RoleCommand.CreateRoleRequest;
import pb.role.RoleCommand.UpdateRoleRequest;
import pb.role.RoleCommon.ApiResponseRole;
import pb.role.RoleCommon.ApiResponseRoleAll;
import pb.role.RoleCommon.ApiResponseRoleDelete;
import pb.role.RoleCommon.ApiResponseRoleDeleteAt;
import pb.role.RoleCommon.FindByIdRoleRequest;

@GrpcService
@Singleton
public class RoleCommandGrpcHandler extends MutinyRoleCommandServiceGrpc.RoleCommandServiceImplBase {

    @Inject
    RoleCommandService roleCommandService;

    @Override
    public Uni<ApiResponseRole> createRole(CreateRoleRequest request) {
        com.sanedge.role.domain.requests.CreateRoleRequest domainReq = new com.sanedge.role.domain.requests.CreateRoleRequest();
        domainReq.setName(request.getName());

        return roleCommandService.create(domainReq)
                .map(apiResp -> {
                    ApiResponseRole.Builder builder = ApiResponseRole.newBuilder()
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
    public Uni<ApiResponseRole> updateRole(UpdateRoleRequest request) {
        com.sanedge.role.domain.requests.UpdateRoleRequest domainReq = new com.sanedge.role.domain.requests.UpdateRoleRequest();
        domainReq.setRoleId(request.getId());
        domainReq.setName(request.getName());

        return roleCommandService.update(domainReq)
                .map(apiResp -> {
                    ApiResponseRole.Builder builder = ApiResponseRole.newBuilder()
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
    public Uni<ApiResponseRoleDeleteAt> trashedRole(FindByIdRoleRequest request) {
        return roleCommandService.trash((long) request.getRoleId())
                .map(apiResp -> {
                    ApiResponseRoleDeleteAt.Builder builder = ApiResponseRoleDeleteAt.newBuilder()
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
    public Uni<ApiResponseRoleDeleteAt> restoreRole(FindByIdRoleRequest request) {
        return roleCommandService.restore((long) request.getRoleId())
                .map(apiResp -> {
                    ApiResponseRoleDeleteAt.Builder builder = ApiResponseRoleDeleteAt.newBuilder()
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
    public Uni<ApiResponseRoleDelete> deleteRolePermanent(FindByIdRoleRequest request) {
        return roleCommandService.deletePermanent((long) request.getRoleId())
                .map(apiResp -> ApiResponseRoleDelete.newBuilder()
                        .setStatus(apiResp.status())
                        .setMessage(apiResp.message())
                        .build())
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseRoleAll> restoreAllRole(Empty request) {
        return roleCommandService.restoreAllTrashedRoles()
                .map(apiResp -> ApiResponseRoleAll.newBuilder()
                        .setStatus(apiResp.status())
                        .setMessage(apiResp.message())
                        .build())
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseRoleAll> deleteAllRolePermanent(Empty request) {
        return roleCommandService.deleteAllTrashedRoles()
                .map(apiResp -> ApiResponseRoleAll.newBuilder()
                        .setStatus(apiResp.status())
                        .setMessage(apiResp.message())
                        .build())
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    private pb.role.RoleCommon.RoleResponse toProto(RoleResponse r) {
        if (r == null) {
            return pb.role.RoleCommon.RoleResponse.getDefaultInstance();
        }
        pb.role.RoleCommon.RoleResponse.Builder builder = pb.role.RoleCommon.RoleResponse.newBuilder();
        if (r.getId() != null) {
            builder.setId(r.getId());
        }
        if (r.getName() != null) {
            builder.setName(r.getName());
        }
        if (r.getCreatedAt() != null) {
            builder.setCreatedAt(r.getCreatedAt());
        }
        if (r.getUpdatedAt() != null) {
            builder.setUpdatedAt(r.getUpdatedAt());
        }
        return builder.build();
    }

    private pb.role.RoleCommon.RoleResponseDeleteAt toProto(RoleResponseDeleteAt r) {
        if (r == null) {
            return pb.role.RoleCommon.RoleResponseDeleteAt.getDefaultInstance();
        }
        pb.role.RoleCommon.RoleResponseDeleteAt.Builder builder = pb.role.RoleCommon.RoleResponseDeleteAt.newBuilder();
        if (r.getId() != null) {
            builder.setId(r.getId());
        }
        if (r.getName() != null) {
            builder.setName(r.getName());
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

    @Override
    public Uni<pb.role.RoleCommon.ApiResponseUserRole> assignRoleToUser(
            pb.role.RoleCommon.AssignRoleToUserRequest request) {
        return roleCommandService.assignRoleToUser((long) request.getUserId(), (long) request.getRoleId())
                .map(apiResp -> {
                    pb.role.RoleCommon.ApiResponseUserRole.Builder builder = pb.role.RoleCommon.ApiResponseUserRole
                            .newBuilder()
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
    public Uni<Empty> removeRoleFromUser(pb.role.RoleCommon.RemoveRoleFromUserRequest request) {
        return roleCommandService.removeRoleFromUser((long) request.getUserId(), (long) request.getRoleId())
                .map(apiResp -> Empty.getDefaultInstance())
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    private pb.role.RoleCommon.UserRoleResponse toProto(UserRoleResponse ur) {
        if (ur == null) {
            return pb.role.RoleCommon.UserRoleResponse.getDefaultInstance();
        }
        pb.role.RoleCommon.UserRoleResponse.Builder builder = pb.role.RoleCommon.UserRoleResponse.newBuilder();
        builder.setUserRoleId(0);
        if (ur.getUserId() != null) {
            builder.setUserId(ur.getUserId().intValue());
        }
        if (ur.getRole() != null && ur.getRole().getId() != null) {
            builder.setRoleId(ur.getRole().getId());
        }
        return builder.build();
    }
}
