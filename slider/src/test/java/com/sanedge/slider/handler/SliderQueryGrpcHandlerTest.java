package com.sanedge.slider.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.domain.response.ApiResponsePagination;
import com.sanedge.slider.domain.requests.FindAllSliderRequest;
import com.sanedge.slider.service.SliderQueryService;

import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class SliderQueryGrpcHandlerTest {
    @Mock SliderQueryService sliderQueryService;
    private SliderQueryGrpcHandler sliderQueryGrpcHandler;

    @BeforeEach
    void setUp() throws Exception {
        sliderQueryGrpcHandler = new SliderQueryGrpcHandler();
        Field f = SliderQueryGrpcHandler.class.getDeclaredField("sliderQueryService");
        f.setAccessible(true);
        f.set(sliderQueryGrpcHandler, sliderQueryService);
    }

    @Test
    void findAll_Success() {
        ApiResponsePagination<java.util.List<?>> resp = new ApiResponsePagination<>(java.util.List.of(), "success", "ok", null);
        lenient().when(sliderQueryService.findAllPaginated(any(FindAllSliderRequest.class))).thenReturn(Uni.createFrom().item(resp));
        var result = sliderQueryGrpcHandler.findAll(pb.slider.SliderQuery.FindAllSliderRequest.newBuilder().setPage(1).setPageSize(10).build()).await().indefinitely();
        assertThat(result).isNotNull();
    }

    @Test
    void findById_Success() {
        lenient().when(sliderQueryService.findById(any(Long.class))).thenAnswer(i -> Uni.createFrom().item(ApiResponse.success("ok", null)));
        var result = sliderQueryGrpcHandler.findById(pb.slider.SliderCommon.FindByIdSliderRequest.newBuilder().setId(1).build()).await().indefinitely();
        assertThat(result).isNotNull();
    }
}
