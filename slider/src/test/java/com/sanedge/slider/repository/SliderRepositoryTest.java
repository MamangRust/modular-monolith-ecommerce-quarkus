package com.sanedge.slider.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.sanedge.slider.domain.requests.FindAllSliderRequest;
import com.sanedge.slider.entity.Slider;

import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
@TestReactiveTransaction
class SliderRepositoryTest {
    @Inject SliderQueryRepository sliderQueryRepo;
    @Inject SliderCommandRepository sliderCommandRepo;

    private Slider createSlider(String name) {
        Slider s = new Slider();
        s.setName(name);
        s.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
        s.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
        return sliderQueryRepo.persist(s).await().indefinitely();
    }

    @Test
    void testCreateAndFindById() {
        Slider saved = createSlider("Promo 1");
        assertThat(saved).isNotNull();
        assertThat(saved.id).isNotNull();
        Slider found = sliderQueryRepo.findById(saved.id).await().indefinitely();
        assertThat(found).isNotNull();
    }

    @Test
    void testFindAllExcludesTrashed() {
        createSlider("Active");
        Slider trashed = createSlider("Trashed");
        sliderCommandRepo.trash(trashed.id).await().indefinitely();
        FindAllSliderRequest req = new FindAllSliderRequest();
        req.setPage(1);
        req.setPageSize(10);
        var result = sliderQueryRepo.findSliders(req).await().indefinitely();
        assertThat(result.getData()).hasSize(1);
    }

    @Test
    void testTrashSlider() {
        Slider saved = createSlider("TrashMe");
        Slider trashed = sliderCommandRepo.trash(saved.id).await().indefinitely();
        assertThat(trashed).isNotNull();
        assertThat(trashed.getDeletedAt()).isNotNull();
    }

    @Test
    void testRestoreSlider() {
        Slider saved = createSlider("RestoreMe");
        sliderCommandRepo.trash(saved.id).await().indefinitely();
        Slider restored = sliderCommandRepo.restore(saved.id).await().indefinitely();
        assertThat(restored).isNotNull();
        assertThat(restored.getDeletedAt()).isNull();
    }

    @Test
    void testDeletePermanent() {
        Slider saved = createSlider("DeleteMe");
        sliderCommandRepo.trash(saved.id).await().indefinitely();
        Slider deleted = sliderCommandRepo.deletePermanent(saved.id).await().indefinitely();
        assertThat(deleted).isNotNull();
        Slider check = sliderQueryRepo.findById(saved.id).await().indefinitely();
        assertThat(check).isNull();
    }
}
