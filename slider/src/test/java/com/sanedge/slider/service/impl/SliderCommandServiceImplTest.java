package com.sanedge.slider.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.slider.domain.requests.CreateSliderRequest;
import com.sanedge.slider.domain.requests.UpdateSliderRequest;
import com.sanedge.slider.domain.response.SliderResponse;
import com.sanedge.slider.domain.response.SliderResponseDeleteAt;
import com.sanedge.slider.entity.Slider;
import com.sanedge.slider.repository.SliderCommandRepository;
import com.sanedge.slider.repository.SliderQueryRepository;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

@ExtendWith(MockitoExtension.class)
class SliderCommandServiceImplTest {

    @Mock
    private SliderCommandRepository sliderCommandRepository;

    @Mock
    private SliderQueryRepository sliderQueryRepository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private SliderCommandServiceImpl sliderService;
    private Validator validator;

    @BeforeEach
    void setUp() throws Exception {
        // Initialize validator
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

        // Mock the TracingMetrics.traceAndMeasure to execute the supplier directly
        lenient().when(tracingMetrics.traceAndMeasure(anyString(), anyString(), any(Attributes.class), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<Uni<?>> supplier = invocation.getArgument(3);
                    return supplier.get();
                });
        lenient().when(tracingMetrics.traceAndMeasure(anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<Uni<?>> supplier = invocation.getArgument(2);
                    return supplier.get();
                });

        // Mock Redis cache invalidation
        lenient().when(redisService.deleteReactive(anyString()))
                .thenReturn(Uni.createFrom().voidItem());

        sliderService = new SliderCommandServiceImpl(
                sliderCommandRepository,
                sliderQueryRepository,
                validator,
                redisService,
                tracingMetrics
        );
    }

    // ========== CREATE SLIDER TESTS ==========

    @Test
    @DisplayName("createSlider - should successfully create a slider")
    void createSlider_Success() {
        // Arrange
        CreateSliderRequest request = new CreateSliderRequest();
        request.setNama("Summer Sale Slider");
        request.setFilePath("/images/summer-sale.jpg");

        Slider savedSlider = createTestSlider(1L, "Summer Sale Slider", "/images/summer-sale.jpg");

        when(sliderCommandRepository.persist(any(Slider.class)))
                .thenReturn(Uni.createFrom().item(savedSlider));

        // Act
        ApiResponse<SliderResponse> result = sliderService.createSlider(request)
                .await().indefinitely();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Slider created successfully");
        assertThat(result.data()).isNotNull();
        assertThat(result.data().getName()).isEqualTo("Summer Sale Slider");
        assertThat(result.data().getImage()).isEqualTo("/images/summer-sale.jpg");

        verify(sliderCommandRepository).persist(any(Slider.class));
    }

    @Test
    @DisplayName("createSlider - should set all fields correctly on entity")
    void createSlider_AllFieldsSetCorrectly() {
        // Arrange
        CreateSliderRequest request = new CreateSliderRequest();
        request.setNama("Promo Slider");
        request.setFilePath("/images/promo.jpg");

        when(sliderCommandRepository.persist(any(Slider.class)))
                .thenAnswer(invocation -> {
                    Slider slider = invocation.getArgument(0);
                    // Verify all fields are set
                    assertThat(slider.getName()).isEqualTo("Promo Slider");
                    assertThat(slider.getImage()).isEqualTo("/images/promo.jpg");

                    // Set id via reflection and return
                    setId(slider, 5L);
                    return Uni.createFrom().item(slider);
                });

        // Act
        ApiResponse<SliderResponse> result = sliderService.createSlider(request)
                .await().indefinitely();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        verify(sliderCommandRepository).persist(any(Slider.class));
    }

    // ========== UPDATE SLIDER TESTS ==========

    @Test
    @DisplayName("updateSlider - should successfully update a slider")
    void updateSlider_Success() {
        // Arrange
        Integer sliderId = 1;
        Slider existingSlider = createTestSlider(1L, "Old Slider Name", "/images/old.jpg");

        Slider updatedSlider = createTestSlider(1L, "New Slider Name", "/images/new.jpg");

        UpdateSliderRequest request = new UpdateSliderRequest();
        request.setId(sliderId);
        request.setNama("New Slider Name");
        request.setFilePath("/images/new.jpg");

        when(sliderCommandRepository.findById(anyLong()))
                .thenReturn(Uni.createFrom().item(existingSlider));
        when(sliderCommandRepository.persist(any(Slider.class)))
                .thenReturn(Uni.createFrom().item(updatedSlider));

        // Act
        ApiResponse<SliderResponse> result = sliderService.updateSlider(request)
                .await().indefinitely();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Slider updated successfully");
        assertThat(result.data()).isNotNull();
        assertThat(result.data().getName()).isEqualTo("New Slider Name");

        verify(sliderCommandRepository).findById(1L);
        verify(sliderCommandRepository).persist(any(Slider.class));
    }

    @Test
    @DisplayName("updateSlider - should update only name when filePath is null")
    void updateSlider_PartialUpdate() {
        // Arrange
        Integer sliderId = 1;
        Slider existingSlider = createTestSlider(1L, "Old Slider Name", "/images/old.jpg");

        Slider updatedSlider = createTestSlider(1L, "Updated Slider Name", "/images/old.jpg");

        UpdateSliderRequest request = new UpdateSliderRequest();
        request.setId(sliderId);
        request.setNama("Updated Slider Name");
        request.setFilePath(null); // No new image

        when(sliderCommandRepository.findById(anyLong()))
                .thenReturn(Uni.createFrom().item(existingSlider));
        when(sliderCommandRepository.persist(any(Slider.class)))
                .thenAnswer(invocation -> {
                    Slider slider = invocation.getArgument(0);
                    // Image should remain unchanged
                    assertThat(slider.getImage()).isEqualTo("/images/old.jpg");
                    assertThat(slider.getName()).isEqualTo("Updated Slider Name");
                    return Uni.createFrom().item(slider);
                });

        // Act
        ApiResponse<SliderResponse> result = sliderService.updateSlider(request)
                .await().indefinitely();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
    }

    @Test
    @DisplayName("updateSlider - should fail when slider not found")
    void updateSlider_NotFound() {
        // Arrange
        UpdateSliderRequest request = new UpdateSliderRequest();
        request.setId(999);
        request.setNama("Updated Slider");
        request.setFilePath("/images/updated.jpg");

        when(sliderCommandRepository.findById(anyLong()))
                .thenReturn(Uni.createFrom().nullItem());

        // Act & Assert
        assertThatThrownBy(() -> sliderService.updateSlider(request).await().indefinitely())
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Slider not found");

        verify(sliderCommandRepository).findById(999L);
    }

    // ========== TRASH SLIDER TESTS ==========

    @Test
    @DisplayName("trashedSlider - should successfully trash a slider")
    void trashedSlider_Success() {
        // Arrange
        Integer sliderId = 1;
        Slider trashedSlider = createTestSlider(1L, "Trashed Slider", "/images/slider.jpg");
        trashedSlider.setDeletedAt(Timestamp.valueOf(LocalDateTime.now()));

        when(sliderCommandRepository.trashed(anyLong()))
                .thenReturn(Uni.createFrom().item(trashedSlider));

        // Act
        ApiResponse<SliderResponseDeleteAt> result = sliderService.trashedSlider(sliderId)
                .await().indefinitely();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Slider trashed successfully!");
        assertThat(result.data()).isNotNull();
        assertThat(result.data().getId()).isEqualTo(1L);
        assertThat(result.data().getDeletedAt()).isNotNull();

        verify(sliderCommandRepository).trashed(1L);
    }

    @Test
    @DisplayName("trashedSlider - should fail when slider not found or already trashed")
    void trashedSlider_NotFound() {
        // Arrange
        Integer sliderId = 999;

        when(sliderCommandRepository.trashed(anyLong()))
                .thenReturn(Uni.createFrom().nullItem());

        // Act & Assert
        assertThatThrownBy(() -> sliderService.trashedSlider(sliderId).await().indefinitely())
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Slider not found or already trashed");

        verify(sliderCommandRepository).trashed(999L);
    }

    // ========== RESTORE SLIDER TESTS ==========

    @Test
    @DisplayName("restoreSlider - should successfully restore a trashed slider")
    void restoreSlider_Success() {
        // Arrange
        Integer sliderId = 1;
        Slider restoredSlider = createTestSlider(1L, "Restored Slider", "/images/slider.jpg");
        // deletedAt is null when restored

        when(sliderCommandRepository.restore(anyLong()))
                .thenReturn(Uni.createFrom().item(restoredSlider));

        // Act
        ApiResponse<SliderResponseDeleteAt> result = sliderService.restoreSlider(sliderId)
                .await().indefinitely();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Slider restored successfully!");
        assertThat(result.data()).isNotNull();
        assertThat(result.data().getId()).isEqualTo(1L);

        verify(sliderCommandRepository).restore(1L);
    }

    @Test
    @DisplayName("restoreSlider - should fail when slider not found or not trashed")
    void restoreSlider_NotFound() {
        // Arrange
        Integer sliderId = 999;

        when(sliderCommandRepository.restore(anyLong()))
                .thenReturn(Uni.createFrom().nullItem());

        // Act & Assert
        assertThatThrownBy(() -> sliderService.restoreSlider(sliderId).await().indefinitely())
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Slider not found or not trashed");

        verify(sliderCommandRepository).restore(999L);
    }

    // ========== DELETE PERMANENT TESTS ==========

    @Test
    @DisplayName("deleteSliderPermanent - should successfully delete a trashed slider permanently")
    void deleteSliderPermanent_Success() {
        // Arrange
        Integer sliderId = 1;
        Slider trashedSlider = createTestSlider(1L, "To Delete", "/images/slider.jpg");
        trashedSlider.setDeletedAt(Timestamp.valueOf(LocalDateTime.now().minusDays(1)));

        when(sliderCommandRepository.deletePermanent(anyLong()))
                .thenReturn(Uni.createFrom().item(trashedSlider));

        // Act
        ApiResponse<Void> result = sliderService.deleteSliderPermanent(sliderId)
                .await().indefinitely();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Slider permanently deleted!");

        verify(sliderCommandRepository).deletePermanent(1L);
    }

    @Test
    @DisplayName("deleteSliderPermanent - should fail when slider not found or not trashed")
    void deleteSliderPermanent_NotTrashed() {
        // Arrange
        Integer sliderId = 999;

        when(sliderCommandRepository.deletePermanent(anyLong()))
                .thenReturn(Uni.createFrom().nullItem());

        // Act & Assert
        assertThatThrownBy(() -> sliderService.deleteSliderPermanent(sliderId).await().indefinitely())
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Slider not found or not trashed");

        verify(sliderCommandRepository).deletePermanent(999L);
    }

    // ========== RESTORE ALL TESTS ==========

    @Test
    @DisplayName("restoreAllSliders - should successfully restore all trashed sliders")
    void restoreAllSliders_Success() {
        // Arrange
        when(sliderCommandRepository.restoreAllDeleted())
                .thenReturn(Uni.createFrom().item(true));

        // Act
        ApiResponse<Void> result = sliderService.restoreAllSliders()
                .await().indefinitely();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("All sliders restored successfully!");

        verify(sliderCommandRepository).restoreAllDeleted();
    }

    @Test
    @DisplayName("restoreAllSliders - should fail when no trashed sliders found")
    void restoreAllSliders_NoSlidersFound() {
        // Arrange
        when(sliderCommandRepository.restoreAllDeleted())
                .thenReturn(Uni.createFrom().item(false));

        // Act & Assert
        assertThatThrownBy(() -> sliderService.restoreAllSliders().await().indefinitely())
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No trashed sliders found");

        verify(sliderCommandRepository).restoreAllDeleted();
    }

    // ========== DELETE ALL PERMANENT TESTS ==========

    @Test
    @DisplayName("deleteAllSlidersPermanent - should successfully delete all trashed sliders permanently")
    void deleteAllSlidersPermanent_Success() {
        // Arrange
        when(sliderCommandRepository.deleteAllDeleted())
                .thenReturn(Uni.createFrom().item(true));

        // Act
        ApiResponse<Void> result = sliderService.deleteAllSlidersPermanent()
                .await().indefinitely();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("All sliders permanently deleted!");

        verify(sliderCommandRepository).deleteAllDeleted();
    }

    @Test
    @DisplayName("deleteAllSlidersPermanent - should fail when no trashed sliders found")
    void deleteAllSlidersPermanent_NoSlidersFound() {
        // Arrange
        when(sliderCommandRepository.deleteAllDeleted())
                .thenReturn(Uni.createFrom().item(false));

        // Act & Assert
        assertThatThrownBy(() -> sliderService.deleteAllSlidersPermanent().await().indefinitely())
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No trashed sliders found");

        verify(sliderCommandRepository).deleteAllDeleted();
    }

    // ========== CACHE INVALIDATION TESTS ==========

    @Test
    @DisplayName("createSlider - should invalidate cache after successful creation")
    void createSlider_InvalidatesCache() {
        // Arrange
        CreateSliderRequest request = new CreateSliderRequest();
        request.setNama("Cache Test Slider");
        request.setFilePath("/images/cache-test.jpg");

        Slider savedSlider = createTestSlider(1L, "Cache Test Slider", "/images/cache-test.jpg");

        when(sliderCommandRepository.persist(any(Slider.class)))
                .thenReturn(Uni.createFrom().item(savedSlider));

        // Act
        sliderService.createSlider(request).await().indefinitely();

        // Assert
        verify(redisService).deleteReactive("slider:all:*");
        verify(redisService).deleteReactive("slider:active:*");
        verify(redisService).deleteReactive("slider:trashed:*");
    }

    @Test
    @DisplayName("trashedSlider - should invalidate cache after successful trash")
    void trashedSlider_InvalidatesCache() {
        // Arrange
        Integer sliderId = 5;
        Slider trashedSlider = createTestSlider(5L, "Test Slider", "/images/test.jpg");
        trashedSlider.setDeletedAt(Timestamp.valueOf(LocalDateTime.now()));

        when(sliderCommandRepository.trashed(anyLong()))
                .thenReturn(Uni.createFrom().item(trashedSlider));

        // Act
        sliderService.trashedSlider(sliderId).await().indefinitely();

        // Assert
        verify(redisService).deleteReactive("slider:all:*");
        verify(redisService).deleteReactive("slider:active:*");
        verify(redisService).deleteReactive("slider:trashed:*");
    }

    @Test
    @DisplayName("restoreSlider - should invalidate cache after successful restore")
    void restoreSlider_InvalidatesCache() {
        // Arrange
        Integer sliderId = 3;
        Slider restoredSlider = createTestSlider(3L, "Test Slider", "/images/test.jpg");

        when(sliderCommandRepository.restore(anyLong()))
                .thenReturn(Uni.createFrom().item(restoredSlider));

        // Act
        sliderService.restoreSlider(sliderId).await().indefinitely();

        // Assert
        verify(redisService).deleteReactive("slider:all:*");
        verify(redisService).deleteReactive("slider:active:*");
        verify(redisService).deleteReactive("slider:trashed:*");
    }

    // ========== HELPER METHODS ==========

    private Slider createTestSlider(Long id, String name, String image) {
        Slider slider = new Slider();
        slider.setName(name);
        slider.setImage(image);
        slider.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
        slider.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
        setId(slider, id);
        return slider;
    }

    private void setId(Object entity, Long id) {
        try {
            Class<?> clazz = entity.getClass();
            Field idField = null;
            while (clazz != null && clazz != Object.class) {
                try {
                    idField = clazz.getDeclaredField("id");
                    break;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            if (idField != null) {
                idField.setAccessible(true);
                idField.set(entity, id);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to set id on entity", e);
        }
    }
}