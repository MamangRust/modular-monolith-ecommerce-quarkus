package com.sanedge.slider.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.slider.domain.response.SliderResponse;
import com.sanedge.slider.domain.response.SliderResponseDeleteAt;
import com.sanedge.slider.service.SliderCommandService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.ResourceNotFoundException;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.smallrye.mutiny.Uni;
import pb.slider.SliderCommand.CreateSliderRequest;
import pb.slider.SliderCommand.UpdateSliderRequest;
import pb.slider.SliderCommon.ApiResponseSlider;
import pb.slider.SliderCommon.ApiResponseSliderAll;
import pb.slider.SliderCommon.ApiResponseSliderDelete;
import pb.slider.SliderCommon.ApiResponseSliderDeleteAt;
import pb.slider.SliderCommon.FindByIdSliderRequest;
import com.google.protobuf.Empty;

@ExtendWith(MockitoExtension.class)
class SliderCommandGrpcHandlerTest {

    @Mock
    private SliderCommandService sliderCommandService;

    private SliderCommandGrpcHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        handler = new SliderCommandGrpcHandler();
        injectService(handler, sliderCommandService);
    }

    private void injectService(Object target, Object service) throws Exception {
        Field field = target.getClass().getDeclaredField("sliderCommandService");
        field.setAccessible(true);
        field.set(target, service);
    }

    // ========== CREATE SLIDER HANDLER TESTS ==========

    @Test
    @DisplayName("create - should return success response when slider created successfully")
    void create_Success() {
        // Arrange
        CreateSliderRequest request = CreateSliderRequest.newBuilder()
                .setName("Summer Sale Slider")
                .setImage("/images/summer-sale.jpg")
                .build();

        SliderResponse mockResponse = SliderResponse.builder()
                .id(1L)
                .name("Summer Sale Slider")
                .image("/images/summer-sale.jpg")
                .createdAt("2026-07-01 10:00:00")
                .updatedAt("2026-07-01 10:00:00")
                .build();

        ApiResponse<SliderResponse> apiResponse = ApiResponse.success(
                "Slider created successfully", mockResponse);

        when(sliderCommandService.createSlider(any()))
                .thenReturn(Uni.createFrom().item(apiResponse));

        // Act
        ApiResponseSlider response = handler.create(request).await().indefinitely();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getMessage()).isEqualTo("Slider created successfully");
        assertThat(response.hasData()).isTrue();
        assertThat(response.getData().getId()).isEqualTo(1);
        assertThat(response.getData().getName()).isEqualTo("Summer Sale Slider");
        assertThat(response.getData().getImage()).isEqualTo("/images/summer-sale.jpg");
    }

    @Test
    @DisplayName("create - should return NOT_FOUND error when ResourceNotFoundException thrown")
    void create_NotFoundError() {
        // Arrange
        CreateSliderRequest request = CreateSliderRequest.newBuilder()
                .setName("Test Slider")
                .setImage("/images/test.jpg")
                .build();

        when(sliderCommandService.createSlider(any()))
                .thenReturn(Uni.createFrom().failure(new ResourceNotFoundException("Slider not found")));

        // Act & Assert
        StatusRuntimeException exception = null;
        try {
            handler.create(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            exception = e;
        }

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus().getCode()).isEqualTo(Status.NOT_FOUND.getCode());
        assertThat(exception.getStatus().getDescription()).isEqualTo("Slider not found");
    }

    @Test
    @DisplayName("create - should return INTERNAL error when generic exception thrown")
    void create_InternalError() {
        // Arrange
        CreateSliderRequest request = CreateSliderRequest.newBuilder()
                .setName("Test Slider")
                .setImage("/images/test.jpg")
                .build();

        when(sliderCommandService.createSlider(any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Database error")));

        // Act & Assert
        StatusRuntimeException exception = null;
        try {
            handler.create(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            exception = e;
        }

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus().getCode()).isEqualTo(Status.INTERNAL.getCode());
        assertThat(exception.getStatus().getDescription()).isEqualTo("Database error");
    }

    @Test
    @DisplayName("create - should correctly map null data in response")
    void create_NullData() {
        // Arrange
        CreateSliderRequest request = CreateSliderRequest.newBuilder()
                .setName("Test Slider")
                .setImage("/images/test.jpg")
                .build();

        ApiResponse<SliderResponse> apiResponse = ApiResponse.success("Created", null);

        when(sliderCommandService.createSlider(any()))
                .thenReturn(Uni.createFrom().item(apiResponse));

        // Act
        ApiResponseSlider response = handler.create(request).await().indefinitely();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.hasData()).isFalse();
    }

    // ========== UPDATE SLIDER HANDLER TESTS ==========

    @Test
    @DisplayName("update - should return success response when slider updated successfully")
    void update_Success() {
        // Arrange
        UpdateSliderRequest request = UpdateSliderRequest.newBuilder()
                .setId(1)
                .setName("Updated Slider")
                .setImage("/images/updated.jpg")
                .build();

        SliderResponse mockResponse = SliderResponse.builder()
                .id(1L)
                .name("Updated Slider")
                .image("/images/updated.jpg")
                .createdAt("2026-07-01 10:00:00")
                .updatedAt("2026-07-02 10:00:00")
                .build();

        ApiResponse<SliderResponse> apiResponse = ApiResponse.success(
                "Slider updated successfully", mockResponse);

        when(sliderCommandService.updateSlider(any()))
                .thenReturn(Uni.createFrom().item(apiResponse));

        // Act
        ApiResponseSlider response = handler.update(request).await().indefinitely();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getMessage()).isEqualTo("Slider updated successfully");
        assertThat(response.hasData()).isTrue();
        assertThat(response.getData().getId()).isEqualTo(1);
        assertThat(response.getData().getName()).isEqualTo("Updated Slider");
        assertThat(response.getData().getImage()).isEqualTo("/images/updated.jpg");
    }

    @Test
    @DisplayName("update - should return NOT_FOUND error when slider not found")
    void update_NotFoundError() {
        // Arrange
        UpdateSliderRequest request = UpdateSliderRequest.newBuilder()
                .setId(999)
                .setName("Updated Slider")
                .setImage("/images/updated.jpg")
                .build();

        when(sliderCommandService.updateSlider(any()))
                .thenReturn(Uni.createFrom().failure(new ResourceNotFoundException("Slider not found")));

        // Act & Assert
        StatusRuntimeException exception = null;
        try {
            handler.update(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            exception = e;
        }

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus().getCode()).isEqualTo(Status.NOT_FOUND.getCode());
        assertThat(exception.getStatus().getDescription()).isEqualTo("Slider not found");
    }

    // ========== TRASH SLIDER HANDLER TESTS ==========

    @Test
    @DisplayName("trashedSlider - should return success response when slider trashed successfully")
    void trashedSlider_Success() {
        // Arrange
        FindByIdSliderRequest request = FindByIdSliderRequest.newBuilder()
                .setId(1)
                .build();

        SliderResponseDeleteAt mockResponse = SliderResponseDeleteAt.builder()
                .id(1L)
                .name("Trashed Slider")
                .image("/images/slider.jpg")
                .createdAt("2026-07-01 10:00:00")
                .updatedAt("2026-07-01 10:00:00")
                .deletedAt("2026-07-02 10:00:00")
                .build();

        ApiResponse<SliderResponseDeleteAt> apiResponse = ApiResponse.success(
                "Slider trashed successfully!", mockResponse);

        when(sliderCommandService.trashedSlider(1))
                .thenReturn(Uni.createFrom().item(apiResponse));

        // Act
        ApiResponseSliderDeleteAt response = handler.trashedSlider(request).await().indefinitely();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getMessage()).isEqualTo("Slider trashed successfully!");
        assertThat(response.hasData()).isTrue();
        assertThat(response.getData().getId()).isEqualTo(1);
        assertThat(response.getData().getDeletedAt().getValue()).isEqualTo("2026-07-02 10:00:00");
    }

    @Test
    @DisplayName("trashedSlider - should return NOT_FOUND error when slider not found")
    void trashedSlider_NotFoundError() {
        // Arrange
        FindByIdSliderRequest request = FindByIdSliderRequest.newBuilder()
                .setId(999)
                .build();

        when(sliderCommandService.trashedSlider(999))
                .thenReturn(Uni.createFrom().failure(
                        new ResourceNotFoundException("Slider not found or already trashed")));

        // Act & Assert
        StatusRuntimeException exception = null;
        try {
            handler.trashedSlider(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            exception = e;
        }

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus().getCode()).isEqualTo(Status.NOT_FOUND.getCode());
        assertThat(exception.getStatus().getDescription()).isEqualTo("Slider not found or already trashed");
    }

    // ========== RESTORE SLIDER HANDLER TESTS ==========

    @Test
    @DisplayName("restoreSlider - should return success response when slider restored successfully")
    void restoreSlider_Success() {
        // Arrange
        FindByIdSliderRequest request = FindByIdSliderRequest.newBuilder()
                .setId(1)
                .build();

        SliderResponseDeleteAt mockResponse = SliderResponseDeleteAt.builder()
                .id(1L)
                .name("Restored Slider")
                .image("/images/slider.jpg")
                .createdAt("2026-07-01 10:00:00")
                .updatedAt("2026-07-02 10:00:00")
                .deletedAt("")
                .build();

        ApiResponse<SliderResponseDeleteAt> apiResponse = ApiResponse.success(
                "Slider restored successfully!", mockResponse);

        when(sliderCommandService.restoreSlider(1))
                .thenReturn(Uni.createFrom().item(apiResponse));

        // Act
        ApiResponseSliderDeleteAt response = handler.restoreSlider(request).await().indefinitely();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getMessage()).isEqualTo("Slider restored successfully!");
        assertThat(response.hasData()).isTrue();
        assertThat(response.getData().getId()).isEqualTo(1);
    }

    @Test
    @DisplayName("restoreSlider - should return NOT_FOUND error when slider not found")
    void restoreSlider_NotFoundError() {
        // Arrange
        FindByIdSliderRequest request = FindByIdSliderRequest.newBuilder()
                .setId(999)
                .build();

        when(sliderCommandService.restoreSlider(999))
                .thenReturn(Uni.createFrom().failure(
                        new ResourceNotFoundException("Slider not found or not trashed")));

        // Act & Assert
        StatusRuntimeException exception = null;
        try {
            handler.restoreSlider(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            exception = e;
        }

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus().getCode()).isEqualTo(Status.NOT_FOUND.getCode());
        assertThat(exception.getStatus().getDescription()).isEqualTo("Slider not found or not trashed");
    }

    // ========== DELETE SLIDER PERMANENT HANDLER TESTS ==========

    @Test
    @DisplayName("deleteSliderPermanent - should return success response when slider deleted permanently")
    void deleteSliderPermanent_Success() {
        // Arrange
        FindByIdSliderRequest request = FindByIdSliderRequest.newBuilder()
                .setId(1)
                .build();

        ApiResponse<Void> apiResponse = ApiResponse.success("Slider permanently deleted!");

        when(sliderCommandService.deleteSliderPermanent(1))
                .thenReturn(Uni.createFrom().item(apiResponse));

        // Act
        ApiResponseSliderDelete response = handler.deleteSliderPermanent(request).await().indefinitely();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getMessage()).isEqualTo("Slider permanently deleted!");
    }

    @Test
    @DisplayName("deleteSliderPermanent - should return NOT_FOUND error when slider not found")
    void deleteSliderPermanent_NotFoundError() {
        // Arrange
        FindByIdSliderRequest request = FindByIdSliderRequest.newBuilder()
                .setId(999)
                .build();

        when(sliderCommandService.deleteSliderPermanent(999))
                .thenReturn(Uni.createFrom().failure(
                        new ResourceNotFoundException("Slider not found or not trashed")));

        // Act & Assert
        StatusRuntimeException exception = null;
        try {
            handler.deleteSliderPermanent(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            exception = e;
        }

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus().getCode()).isEqualTo(Status.NOT_FOUND.getCode());
        assertThat(exception.getStatus().getDescription()).isEqualTo("Slider not found or not trashed");
    }

    // ========== RESTORE ALL SLIDERS HANDLER TESTS ==========

    @Test
    @DisplayName("restoreAllSlider - should return success response when all sliders restored")
    void restoreAllSlider_Success() {
        // Arrange
        Empty request = Empty.newBuilder().build();

        ApiResponse<Void> apiResponse = ApiResponse.success("All sliders restored successfully!");

        when(sliderCommandService.restoreAllSliders())
                .thenReturn(Uni.createFrom().item(apiResponse));

        // Act
        ApiResponseSliderAll response = handler.restoreAllSlider(request).await().indefinitely();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getMessage()).isEqualTo("All sliders restored successfully!");
    }

    @Test
    @DisplayName("restoreAllSlider - should return INTERNAL error when no trashed sliders found")
    void restoreAllSlider_NotFoundError() {
        // Arrange
        Empty request = Empty.newBuilder().build();

        when(sliderCommandService.restoreAllSliders())
                .thenReturn(Uni.createFrom().failure(
                        new ResourceNotFoundException("No trashed sliders found")));

        // Act & Assert
        StatusRuntimeException exception = null;
        try {
            handler.restoreAllSlider(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            exception = e;
        }

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus().getCode()).isEqualTo(Status.INTERNAL.getCode());
        assertThat(exception.getStatus().getDescription()).isEqualTo("No trashed sliders found");
    }

    // ========== DELETE ALL SLIDERS PERMANENT HANDLER TESTS ==========

    @Test
    @DisplayName("deleteAllSliderPermanent - should return success response when all sliders deleted permanently")
    void deleteAllSliderPermanent_Success() {
        // Arrange
        Empty request = Empty.newBuilder().build();

        ApiResponse<Void> apiResponse = ApiResponse.success("All sliders permanently deleted!");

        when(sliderCommandService.deleteAllSlidersPermanent())
                .thenReturn(Uni.createFrom().item(apiResponse));

        // Act
        ApiResponseSliderAll response = handler.deleteAllSliderPermanent(request).await().indefinitely();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getMessage()).isEqualTo("All sliders permanently deleted!");
    }

    @Test
    @DisplayName("deleteAllSliderPermanent - should return INTERNAL error when no trashed sliders found")
    void deleteAllSliderPermanent_NotFoundError() {
        // Arrange
        Empty request = Empty.newBuilder().build();

        when(sliderCommandService.deleteAllSlidersPermanent())
                .thenReturn(Uni.createFrom().failure(
                        new ResourceNotFoundException("No trashed sliders found")));

        // Act & Assert
        StatusRuntimeException exception = null;
        try {
            handler.deleteAllSliderPermanent(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            exception = e;
        }

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus().getCode()).isEqualTo(Status.INTERNAL.getCode());
        assertThat(exception.getStatus().getDescription()).isEqualTo("No trashed sliders found");
    }
}