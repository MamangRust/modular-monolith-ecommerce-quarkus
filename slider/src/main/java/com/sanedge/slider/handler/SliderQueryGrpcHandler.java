package com.sanedge.slider.handler;

import com.sanedge.slider.domain.requests.FindAllSliderRequest;
import com.sanedge.slider.domain.response.SliderResponse;
import com.sanedge.slider.domain.response.SliderResponseDeleteAt;
import com.sanedge.slider.service.SliderQueryService;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import pb.slider.MutinySliderQueryServiceGrpc;
import pb.slider.SliderCommon.ApiResponseSlider;
import pb.slider.SliderCommon.ApiResponsePaginationSlider;
import pb.slider.SliderCommon.ApiResponsePaginationSliderDeleteAt;
import pb.slider.SliderCommon.FindByIdSliderRequest;

@GrpcService
@Singleton
public class SliderQueryGrpcHandler extends MutinySliderQueryServiceGrpc.SliderQueryServiceImplBase {

    @Inject
    SliderQueryService sliderQueryService;

    @Override
    public Uni<ApiResponsePaginationSlider> findAll(pb.slider.SliderQuery.FindAllSliderRequest request) {
        FindAllSliderRequest req = new FindAllSliderRequest();
        req.setPage(request.getPage());
        req.setPageSize(request.getPageSize());
        req.setSearch(request.getSearch());

        return sliderQueryService.findAll(req)
                .map(apiResp -> {
                    ApiResponsePaginationSlider.Builder builder = ApiResponsePaginationSlider.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (SliderResponse r : apiResp.data()) {
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
    public Uni<ApiResponseSlider> findById(FindByIdSliderRequest request) {
        return sliderQueryService.findById(request.getId())
                .map(apiResp -> {
                    ApiResponseSlider.Builder builder = ApiResponseSlider.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        builder.setData(toProto(apiResp.data()));
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> {
                    if (e instanceof com.sanedge.common.exception.ResourceNotFoundException) {
                        return Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException();
                    }
                    return Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException();
                });
    }

    @Override
    public Uni<ApiResponsePaginationSliderDeleteAt> findByActive(pb.slider.SliderQuery.FindAllSliderRequest request) {
        FindAllSliderRequest req = new FindAllSliderRequest();
        req.setPage(request.getPage());
        req.setPageSize(request.getPageSize());
        req.setSearch(request.getSearch());

        return sliderQueryService.findByActive(req)
                .map(apiResp -> {
                    ApiResponsePaginationSliderDeleteAt.Builder builder = ApiResponsePaginationSliderDeleteAt.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (SliderResponseDeleteAt r : apiResp.data()) {
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
    public Uni<ApiResponsePaginationSliderDeleteAt> findByTrashed(pb.slider.SliderQuery.FindAllSliderRequest request) {
        FindAllSliderRequest req = new FindAllSliderRequest();
        req.setPage(request.getPage());
        req.setPageSize(request.getPageSize());
        req.setSearch(request.getSearch());

        return sliderQueryService.findByTrashed(req)
                .map(apiResp -> {
                    ApiResponsePaginationSliderDeleteAt.Builder builder = ApiResponsePaginationSliderDeleteAt.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (SliderResponseDeleteAt r : apiResp.data()) {
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

    private pb.slider.SliderCommon.SliderResponse toProto(SliderResponse r) {
        if (r == null) {
            return pb.slider.SliderCommon.SliderResponse.getDefaultInstance();
        }
        return pb.slider.SliderCommon.SliderResponse.newBuilder()
                .setId(r.getId().intValue())
                .setName(r.getName() != null ? r.getName() : "")
                .setImage(r.getImage() != null ? r.getImage() : "")
                .setCreatedAt(r.getCreatedAt() != null ? r.getCreatedAt() : "")
                .setUpdatedAt(r.getUpdatedAt() != null ? r.getUpdatedAt() : "")
                .build();
    }

    private pb.slider.SliderCommon.SliderResponseDeleteAt toProto(SliderResponseDeleteAt r) {
        if (r == null) {
            return pb.slider.SliderCommon.SliderResponseDeleteAt.getDefaultInstance();
        }
        pb.slider.SliderCommon.SliderResponseDeleteAt.Builder builder = pb.slider.SliderCommon.SliderResponseDeleteAt.newBuilder()
                .setId(r.getId().intValue())
                .setName(r.getName() != null ? r.getName() : "")
                .setImage(r.getImage() != null ? r.getImage() : "")
                .setCreatedAt(r.getCreatedAt() != null ? r.getCreatedAt() : "")
                .setUpdatedAt(r.getUpdatedAt() != null ? r.getUpdatedAt() : "");
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
