package com.sanedge.slider.handler;

import com.sanedge.slider.domain.requests.CreateSliderRequest;
import com.sanedge.slider.domain.requests.UpdateSliderRequest;
import com.sanedge.slider.domain.response.SliderResponse;
import com.sanedge.slider.domain.response.SliderResponseDeleteAt;
import com.sanedge.slider.service.SliderCommandService;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import pb.slider.MutinySliderCommandServiceGrpc;
import pb.slider.SliderCommon.ApiResponseSlider;
import pb.slider.SliderCommon.ApiResponseSliderDeleteAt;
import pb.slider.SliderCommon.ApiResponseSliderDelete;
import pb.slider.SliderCommon.ApiResponseSliderAll;
import pb.slider.SliderCommon.FindByIdSliderRequest;

@GrpcService
@Singleton
public class SliderCommandGrpcHandler extends MutinySliderCommandServiceGrpc.SliderCommandServiceImplBase {

    @Inject
    SliderCommandService sliderCommandService;

    @Override
    public Uni<ApiResponseSlider> create(pb.slider.SliderCommand.CreateSliderRequest request) {
        CreateSliderRequest domainReq = new CreateSliderRequest();
        domainReq.setNama(request.getName());
        domainReq.setFilePath(request.getImage());

        return sliderCommandService.createSlider(domainReq)
                .map(apiResp -> {
                    ApiResponseSlider.Builder builder = ApiResponseSlider.newBuilder()
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
    public Uni<ApiResponseSlider> update(pb.slider.SliderCommand.UpdateSliderRequest request) {
        UpdateSliderRequest domainReq = new UpdateSliderRequest();
        domainReq.setId(request.getId());
        domainReq.setNama(request.getName());
        domainReq.setFilePath(request.getImage());

        return sliderCommandService.updateSlider(domainReq)
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
    public Uni<ApiResponseSliderDeleteAt> trashedSlider(FindByIdSliderRequest request) {
        return sliderCommandService.trashedSlider(request.getId())
                .map(apiResp -> {
                    ApiResponseSliderDeleteAt.Builder builder = ApiResponseSliderDeleteAt.newBuilder()
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
    public Uni<ApiResponseSliderDeleteAt> restoreSlider(FindByIdSliderRequest request) {
        return sliderCommandService.restoreSlider(request.getId())
                .map(apiResp -> {
                    ApiResponseSliderDeleteAt.Builder builder = ApiResponseSliderDeleteAt.newBuilder()
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
    public Uni<ApiResponseSliderDelete> deleteSliderPermanent(FindByIdSliderRequest request) {
        return sliderCommandService.deleteSliderPermanent(request.getId())
                .map(apiResp -> ApiResponseSliderDelete.newBuilder()
                        .setStatus(apiResp.status())
                        .setMessage(apiResp.message())
                        .build())
                .onFailure().transform(e -> {
                    if (e instanceof com.sanedge.common.exception.ResourceNotFoundException) {
                        return Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException();
                    }
                    return Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException();
                });
    }

    @Override
    public Uni<ApiResponseSliderAll> restoreAllSlider(com.google.protobuf.Empty request) {
        return sliderCommandService.restoreAllSliders()
                .map(apiResp -> ApiResponseSliderAll.newBuilder()
                        .setStatus(apiResp.status())
                        .setMessage(apiResp.message())
                        .build())
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseSliderAll> deleteAllSliderPermanent(com.google.protobuf.Empty request) {
        return sliderCommandService.deleteAllSlidersPermanent()
                .map(apiResp -> ApiResponseSliderAll.newBuilder()
                        .setStatus(apiResp.status())
                        .setMessage(apiResp.message())
                        .build())
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
        pb.slider.SliderCommon.SliderResponseDeleteAt.Builder builder = pb.slider.SliderCommon.SliderResponseDeleteAt
                .newBuilder()
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
}
