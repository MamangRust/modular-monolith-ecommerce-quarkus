package com.sanedge.order_item.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.sanedge.order_item.domain.requests.FindAllOrderItemRequest;
import com.sanedge.order_item.entity.OrderItem;

import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
@TestReactiveTransaction
class OrderItemRepositoryTest {
    @Inject OrderItemRepository orderItemRepository;

    private OrderItem createItem(Integer orderId, Integer productId) {
        OrderItem o = new OrderItem();
        o.setOrderId(orderId);
        o.setProductId(productId);
        o.setPrice(15000);
        o.setQuantity(2);
        o.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
        o.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
        return orderItemRepository.persist(o).await().indefinitely();
    }

    @Test
    void testCreateAndFindById() {
        OrderItem saved = createItem(100, 1);
        assertThat(saved).isNotNull();
        OrderItem found = orderItemRepository.findById(saved.id).await().indefinitely();
        assertThat(found).isNotNull();
    }

    @Test
    void testFindByOrderId() {
        createItem(100, 1);
        createItem(100, 2);
        createItem(200, 3);
        var result = orderItemRepository.findOrderItemByOrder(100L).await().indefinitely();
        assertThat(result).hasSize(2);
    }

    @Test
    void testFindAllItemsPaged() {
        for (int i = 0; i < 5; i++) createItem(100, i);
        FindAllOrderItemRequest req = new FindAllOrderItemRequest();
        req.setPage(1);
        req.setPageSize(10);
        var result = orderItemRepository.findOrderItems(req).await().indefinitely();
        assertThat(result.getData()).hasSize(5);
    }

    @Test
    void testTrashOrderItem() {
        OrderItem saved = createItem(100, 1);
        OrderItem trashed = orderItemRepository.trash(saved.id).await().indefinitely();
        assertThat(trashed).isNotNull();
        assertThat(trashed.getDeletedAt()).isNotNull();
    }

    @Test
    void testRestoreOrderItem() {
        OrderItem saved = createItem(100, 1);
        orderItemRepository.trash(saved.id).await().indefinitely();
        OrderItem restored = orderItemRepository.restore(saved.id).await().indefinitely();
        assertThat(restored).isNotNull();
        assertThat(restored.getDeletedAt()).isNull();
    }

    @Test
    void testDeletePermanent() {
        OrderItem saved = createItem(100, 1);
        orderItemRepository.trash(saved.id).await().indefinitely();
        OrderItem deleted = orderItemRepository.deletePermanent(saved.id).await().indefinitely();
        assertThat(deleted).isNotNull();
        assertThat(orderItemRepository.findById(saved.id).await().indefinitely()).isNull();
    }
}
