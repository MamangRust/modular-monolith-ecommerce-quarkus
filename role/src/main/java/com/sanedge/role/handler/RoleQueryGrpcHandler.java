package com.sanedge.role.handler;

import java.util.List;

import com.sanedge.common.domain.response.ApiResponsePagination;
import com.sanedge.role.domain.requests.FindAllRoles;
import com.sanedge.role.domain.response.RoleResponse;
import com.sanedge.role.domain.response.RoleResponseDeleteAt;
import com.sanedge.role.service.RoleQueryService;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import pb.role.MutinyRoleQueryServiceGrpc;
import pb.role.RoleCommon.ApiResponseRole;
import pb.role.RoleCommon.ApiResponsesRole;
import pb.role.RoleQuery.FindAllRoleRequest;
import pb.role.RoleCommon.FindByIdRoleRequest;
import pb.role.RoleQuery.FindByIdUserRoleRequest;
import pb.role.RoleQuery.FindByNameRoleRequest;
import pb.role.RoleCommon.ApiResponsePaginationRole;
import pb.role.RoleCommon.ApiResponsePaginationRoleDeleteAt;

@GrpcService
@Singleton
public class RoleQueryGrpcHandler extends MutinyRoleQueryServiceGrpc.RoleQueryServiceImplBase {

    @Inject
    RoleQueryService roleQueryService;

    @Override
    public Uni<ApiResponsePaginationRole> findAllRole(FindAllRoleRequest request) {
        FindAllRoles domainReq = new FindAllRoles();
        domainReq.setPage(request.getPage());
        domainReq.setPageSize(request.getPageSize());
        domainReq.setSearch(request.getSearch());

        return roleQueryService.findAllPaginated(domainReq)
                .map(apiResp -> {
                    ApiResponsePaginationRole.Builder builder = ApiResponsePaginationRole.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (RoleResponse r : apiResp.data()) {
                            builder.addData(toProto(r));
                        }
                    }
                    if (apiResp.pagination() != null) {
                        builder.setPagination(toProto(apiResp.pagination()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseRole> findByIdRole(FindByIdRoleRequest request) {
        return roleQueryService.findById((long) request.getRoleId())
                .map(apiResp -> {
                    ApiResponseRole.Builder builder = ApiResponseRole.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.setData(toProto(apiResp.data()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> {
                    if (e instanceof jakarta.ws.rs.NotFoundException) {
                        return Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException();
                    }
                    return Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException();
                });
    }

    @Override
    public Uni<ApiResponseRole> findByNameRole(FindByNameRoleRequest request) {
        return roleQueryService.findByName(request.getName())
                .map(apiResp -> {
                    ApiResponseRole.Builder builder = ApiResponseRole.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.setData(toProto(apiResp.data()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> {
                    if (e instanceof jakarta.ws.rs.NotFoundException) {
                        return Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException();
                    }
                    return Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException();
                });
    }

    @Override
    public Uni<ApiResponsePaginationRoleDeleteAt> findByActive(FindAllRoleRequest request) {
        FindAllRoles domainReq = new FindAllRoles();
        domainReq.setPage(request.getPage());
        domainReq.setPageSize(request.getPageSize());
        domainReq.setSearch(request.getSearch());

        return roleQueryService.findActivePaginated(domainReq)
                .map(apiResp -> {
                    ApiResponsePaginationRoleDeleteAt.Builder builder = ApiResponsePaginationRoleDeleteAt.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (RoleResponseDeleteAt r : apiResp.data()) {
                            builder.addData(toProto(r));
                        }
                    }
                    if (apiResp.pagination() != null) {
                        builder.setPagination(toProto(apiResp.pagination()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponsePaginationRoleDeleteAt> findByTrashed(FindAllRoleRequest request) {
        FindAllRoles domainReq = new FindAllRoles();
        domainReq.setPage(request.getPage());
        domainReq.setPageSize(request.getPageSize());
        domainReq.setSearch(request.getSearch());

        return roleQueryService.findTrashedPaginated(domainReq)
                .map(apiResp -> {
                    ApiResponsePaginationRoleDeleteAt.Builder builder = ApiResponsePaginationRoleDeleteAt.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (RoleResponseDeleteAt r : apiResp.data()) {
                            builder.addData(toProto(r));
                        }
                    }
                    if (apiResp.pagination() != null) {
                        builder.setPagination(toProto(apiResp.pagination()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponsesRole> findByUserId(FindByIdUserRoleRequest request) {
        return roleQueryService.findByUserId((long) request.getUserId())
                .map(apiResp -> {
                    ApiResponsesRole.Builder builder = ApiResponsesRole.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (RoleResponse r : apiResp.data()) {
                            builder.addData(toProto(r));
                        }
                    }
                    return builder.build();
                })
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

    private pb.Api.PaginationMeta toProto(com.sanedge.common.domain.response.PaginationMeta m) {
        if (m == null) {
            return pb.Api.PaginationMeta.getDefaultInstance();
        }
        return pb.Api.PaginationMeta.newBuilder()
                .setCurrentPage(m.currentPage())
                .setPageSize(m.pageSize())
                .setTotalPages(m.totalPages())
                .setTotalRecords(m.totalRecords())
                .build();
    }
}
