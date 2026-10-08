package com.sanedge.order.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.common.adapter.merchant.MerchantPort;
import com.sanedge.common.adapter.model.Merchant;
import com.sanedge.common.adapter.model.OrderItem;
import com.sanedge.common.adapter.model.Product;
import com.sanedge.common.adapter.model.ShippingAddress;
import com.sanedge.common.adapter.model.User;
import com.sanedge.common.adapter.order_item.OrderItemPort;
import com.sanedge.common.adapter.product.ProductPort;
import com.sanedge.common.adapter.shipping_address.ShippingAddressPort;
import com.sanedge.common.adapter.transaction.TransactionPort;
import com.sanedge.common.adapter.user.UserPort;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.ForbiddenException;
import com.sanedge.common.exception.InvalidRequestException;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.order.domain.requests.CreateOrderItemRequest;
import com.sanedge.order.domain.requests.CreateOrderRequest;
import com.sanedge.order.domain.requests.CreateShippingAddressRequest;
import com.sanedge.order.domain.requests.UpdateOrderRequest;
import com.sanedge.order.domain.response.OrderResponse;
import com.sanedge.order.domain.response.OrderResponseDeleteAt;
import com.sanedge.order.entity.Order;
import com.sanedge.order.repository.OrderCommandRepository;
import com.sanedge.order.repository.OrderQueryRepository;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

@ExtendWith(MockitoExtension.class)
class OrderCommandServiceImplTest {

        @Mock
        private OrderQueryRepository orderQueryRepository;

        @Mock
        private OrderCommandRepository orderCommandRepository;

        @Mock
        private Validator validator;

        @Mock
        private RedisService redisService;

        @Mock
        private TracingMetrics tracingMetrics;

        @Mock
        private MerchantPort merchantPort;

        @Mock
        private UserPort userPort;

        @Mock
        private ProductPort productPort;

        @Mock
        private OrderItemPort orderItemPort;

        @Mock
        private TransactionPort transactionPort;

        @Mock
        private ShippingAddressPort shippingAddressPort;

        private OrderCommandServiceImpl service;

        @BeforeEach
        void setUp() throws Exception {
                service = new OrderCommandServiceImpl(
                                orderQueryRepository,
                                orderCommandRepository,
                                validator,
                                redisService,
                                tracingMetrics);

                setField(service, "merchantPort", merchantPort);
                setField(service, "userPort", userPort);
                setField(service, "productPort", productPort);
                setField(service, "orderItemPort", orderItemPort);
                setField(service, "transactionPort", transactionPort);
                setField(service, "shippingAddressPort", shippingAddressPort);

                lenient().doAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        Supplier<Uni<?>> supplier = invocation.getArgument(3);
                        return supplier.get();
                }).when(tracingMetrics)
                                .traceAndMeasure(
                                                anyString(),
                                                anyString(),
                                                any(Attributes.class),
                                                any());

                lenient().doAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        Supplier<Uni<?>> supplier = invocation.getArgument(2);
                        return supplier.get();
                }).when(tracingMetrics)
                                .traceAndMeasure(anyString(), anyString(), any());

                lenient().when(redisService.deleteReactive(anyString()))
                                .thenReturn(Uni.createFrom().voidItem());
                lenient().when(orderCommandRepository.updateTotalPrice(any(Long.class), any(Integer.class)))
                                .thenReturn(Uni.createFrom().item(1));
                lenient().when(validator.validate(any())).thenReturn(Collections.emptySet());

                // Default read model untuk jalur trash/restore: order tanpa item.
                lenient().when(orderItemPort.findOrderItemByOrder(anyInt()))
                                .thenReturn(Uni.createFrom().item(List.of()));
                // Default agar order dianggap belum dibayar saat purge.
                lenient().when(transactionPort.findByOrderId(anyInt()))
                                .thenReturn(Uni.createFrom().nullItem());
                // Kompensasi saat create gagal tidak boleh menggagalkan kegagalan asli.
                lenient().when(orderItemPort.deleteByOrderIdRollback(anyInt()))
                                .thenReturn(Uni.createFrom().item(true));
                lenient().when(orderCommandRepository.deleteCreated(anyLong()))
                                .thenReturn(Uni.createFrom().item(true));
        }

        private void setField(Object target, String fieldName, Object value) throws Exception {
                Field field = target.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
        }

        private Order createTestOrder(Long id, Integer merchantId, Integer userId, Integer totalPrice) {
                Order order = new Order();
                order.id = id;
                order.setMerchantId(merchantId);
                order.setUserId(userId);
                order.setTotalPrice(totalPrice);
                order.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
                order.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
                return order;
        }

        private CreateOrderRequest createValidCreateOrderRequest() {
                CreateOrderRequest request = new CreateOrderRequest();
                request.setMerchantId(100);
                request.setUserId(100);

                CreateOrderItemRequest item = new CreateOrderItemRequest();
                item.setProductId(1);
                item.setQuantity(2);
                item.setPrice(100);
                request.setItems(List.of(item));

                CreateShippingAddressRequest shipping = new CreateShippingAddressRequest();
                shipping.setAlamat("123 Test Street");
                shipping.setProvinsi("Test Province");
                shipping.setKota("Test City");
                shipping.setCourier("Test Courier");
                shipping.setShippingMethod("standard");
                shipping.setShippingCost(10000);
                shipping.setNegara("Test Country");
                request.setShippingAddress(shipping);

                return request;
        }

        private void mockMerchantAndUser() {
                lenient().when(merchantPort.findById(anyInt()))
                                .thenReturn(Uni.createFrom().item(new Merchant(
                                                100, 1, "Merchant", null, null, null, null, "active", null, null)));

                lenient().when(userPort.findById(anyInt()))
                                .thenReturn(Uni.createFrom().item(new User(
                                                100, "Test", "User", "test@test.com", null, null, null)));
        }

        private void mockProductAndOrderItem() {
                lenient().when(productPort.findById(anyInt()))
                                .thenReturn(Uni.createFrom().item(new Product(
                                                1, 100, 1, "Product", null, 100, 100, null, 0, 0f, null, null, null,
                                                null)));

                lenient().when(productPort.adjustStock(anyInt(), anyInt()))
                                .thenReturn(Uni.createFrom().item(new Product(
                                                1, 100, 1, "Product", null, 100, 98, null, 0, 0f, null, null, null,
                                                null)));

                lenient().when(orderItemPort.create(any()))
                                .thenReturn(Uni.createFrom().item(new OrderItem(1, 1, 1, 2, 100, null, null)));
        }

        private void mockShipping() {
                lenient().when(shippingAddressPort.create(any()))
                                .thenReturn(Uni.createFrom().item(new ShippingAddress(
                                                1, 1, null, null, null, null, null, 10000, null, null)));
        }

        @Nested
        @DisplayName("create order tests")
        class CreateOrderTests {

                @Test
                @DisplayName("should successfully create order when all validations pass")
                void createOrder_Success() {
                        CreateOrderRequest request = createValidCreateOrderRequest();
                        mockMerchantAndUser();
                        mockProductAndOrderItem();
                        mockShipping();

                        Order savedOrder = createTestOrder(null, 100, 100, 0);
                        when(orderCommandRepository.persistNew(any(Order.class)))
                                        .thenAnswer(inv -> {
                                                Order o = inv.getArgument(0);
                                                if (o.id == null)
                                                        o.id = 1L;
                                                return Uni.createFrom().item(o);
                                        });

                        ApiResponse<OrderResponse> response = service.create(request).await().indefinitely();

                        assertThat(response).isNotNull();
                        assertThat(response.status()).isEqualTo("success");
                        assertThat(response.message()).isEqualTo("Order created successfully");
                        assertThat(response.data()).isNotNull();
                        assertThat(response.data().getId()).isEqualTo(1L);
                }

                @Test
                @DisplayName("should fail when validation errors occur")
                void createOrder_ValidationError() {
                        CreateOrderRequest request = createValidCreateOrderRequest();

                        @SuppressWarnings("unchecked")
                        ConstraintViolation<Object> violation = (ConstraintViolation<Object>) org.mockito.Mockito
                                        .mock(ConstraintViolation.class);
                        lenient().when(violation.getPropertyPath())
                                        .thenReturn(org.mockito.Mockito.mock(jakarta.validation.Path.class));
                        lenient().when(violation.getMessage()).thenReturn("test error message");
                        when(validator.validate(any())).thenReturn(Set.of(violation));

                        assertThatThrownBy(() -> service.create(request).await().indefinitely())
                                        .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
                }

                @Test
                @DisplayName("should fail when merchant not found")
                void createOrder_MerchantNotFound() {
                        CreateOrderRequest request = createValidCreateOrderRequest();

                        when(merchantPort.findById(anyInt()))
                                        .thenReturn(Uni.createFrom().failure(
                                                        new ResourceNotFoundException("Merchant not found: 100")));

                        assertThatThrownBy(() -> service.create(request).await().indefinitely())
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("Merchant not found");
                }

                @Test
                @DisplayName("should fail when user not found")
                void createOrder_UserNotFound() {
                        CreateOrderRequest request = createValidCreateOrderRequest();

                        when(merchantPort.findById(anyInt()))
                                        .thenReturn(Uni.createFrom().item(new Merchant(
                                                        100, 1, "Merchant", null, null, null, null, "active", null,
                                                        null)));
                        when(userPort.findById(anyInt()))
                                        .thenReturn(Uni.createFrom().failure(
                                                        new ResourceNotFoundException("User not found: 100")));

                        assertThatThrownBy(() -> service.create(request).await().indefinitely())
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("User not found");
                }

                @Test
                @DisplayName("should fail when product not found for order item")
                void createOrder_ProductNotFound() {
                        CreateOrderRequest request = createValidCreateOrderRequest();
                        mockMerchantAndUser();

                        Order savedOrder = createTestOrder(null, 100, 100, 0);
                        when(orderCommandRepository.persistNew(any(Order.class)))
                                        .thenAnswer(inv -> {
                                                Order o = inv.getArgument(0);
                                                if (o.id == null)
                                                        o.id = 1L;
                                                return Uni.createFrom().item(o);
                                        });

                        when(productPort.findById(anyInt()))
                                        .thenReturn(Uni.createFrom().failure(
                                                        new ResourceNotFoundException("Product not found: 1")));

                        assertThatThrownBy(() -> service.create(request).await().indefinitely())
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("Product not found");
                }

                @Test
                @DisplayName("should fail when insufficient stock")
                void createOrder_InsufficientStock() {
                        CreateOrderRequest request = createValidCreateOrderRequest();
                        mockMerchantAndUser();

                        Order savedOrder = createTestOrder(null, 100, 100, 0);
                        when(orderCommandRepository.persistNew(any(Order.class)))
                                        .thenAnswer(inv -> {
                                                Order o = inv.getArgument(0);
                                                if (o.id == null)
                                                        o.id = 1L;
                                                return Uni.createFrom().item(o);
                                        });

                        when(productPort.findById(anyInt()))
                                        .thenReturn(Uni.createFrom().item(new Product(
                                                        1, 100, 1, "Product", null, 100, 1, null, 0, 0f, null, null,
                                                        null, null)));

                        assertThatThrownBy(() -> service.create(request).await().indefinitely())
                                        .isInstanceOf(InvalidRequestException.class)
                                        .hasMessageContaining("Insufficient stock");
                }
        }

        @Test
        @DisplayName("should use authoritative product price and atomic stock delta")
        void createOrder_UsesAuthoritativePriceAndStockDelta() {
                CreateOrderRequest request = createValidCreateOrderRequest();
                request.getItems().get(0).setPrice(1);
                mockMerchantAndUser();
                mockShipping();

                when(productPort.findById(anyInt()))
                                .thenReturn(Uni.createFrom().item(new Product(
                                                1, 100, 1, "Product", null, 250, 100, null, 0, 0f, null, null, null,
                                                null)));
                when(productPort.adjustStock(anyInt(), anyInt()))
                                .thenReturn(Uni.createFrom().item(new Product(
                                                1, 100, 1, "Product", null, 250, 98, null, 0, 0f, null, null, null,
                                                null)));
                when(orderItemPort.create(any()))
                                .thenReturn(Uni.createFrom().item(new OrderItem(1, 1, 1, 2, 250, null, null)));
                when(orderCommandRepository.persistNew(any(Order.class))).thenAnswer(invocation -> {
                        Order order = invocation.getArgument(0);
                        if (order.id == null) {
                                order.id = 1L;
                        }
                        return Uni.createFrom().item(order);
                });

                service.create(request).await().indefinitely();

                ArgumentCaptor<Integer> productIdCaptor = ArgumentCaptor.forClass(Integer.class);
                ArgumentCaptor<Integer> deltaCaptor = ArgumentCaptor.forClass(Integer.class);
                verify(productPort).adjustStock(productIdCaptor.capture(), deltaCaptor.capture());
                assertThat(productIdCaptor.getValue()).isEqualTo(1);
                assertThat(deltaCaptor.getValue()).isEqualTo(-2);

                ArgumentCaptor<OrderItemPort.CreateData> itemCaptor =
                                ArgumentCaptor.forClass(OrderItemPort.CreateData.class);
                verify(orderItemPort).create(itemCaptor.capture());
                assertThat(itemCaptor.getValue().price()).isEqualTo(250);
        }

        @Nested
        @DisplayName("update order tests")
        class UpdateOrderTests {

                @Test
                @DisplayName("should fail when orderId is null")
                void updateOrder_NullOrderId() {
                        UpdateOrderRequest request = new UpdateOrderRequest();
                        request.setOrderId(null);
                        request.setUserId(100);

                        assertThatThrownBy(() -> service.update(request).await().indefinitely())
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("OrderId is required");
                }

                @Test
                @DisplayName("should fail when order not found")
                void updateOrder_OrderNotFound() {
                        UpdateOrderRequest request = new UpdateOrderRequest();
                        request.setOrderId(999);
                        request.setUserId(100);
                        request.setItems(List.of());
                        request.setShippingAddress(new com.sanedge.order.domain.requests.UpdateShippingAddressRequest());

                        when(orderQueryRepository.findOrderById(any(Long.class)))
                                        .thenReturn(Uni.createFrom().item(Optional.empty()));

                        assertThatThrownBy(() -> service.update(request).await().indefinitely())
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("Order not found");
                }

                @Test
                @DisplayName("should fail with validation error when required fields are missing")
                void updateOrder_ValidationError() {
                        UpdateOrderRequest request = new UpdateOrderRequest();
                        request.setOrderId(1);
                        request.setUserId(100);
                        request.setItems(null);
                        request.setShippingAddress(null);

                        @SuppressWarnings("unchecked")
                        ConstraintViolation<Object> violation = (ConstraintViolation<Object>) org.mockito.Mockito
                                        .mock(ConstraintViolation.class);
                        lenient().when(violation.getPropertyPath())
                                        .thenReturn(org.mockito.Mockito.mock(jakarta.validation.Path.class));
                        lenient().when(violation.getMessage()).thenReturn("must not be null");
                        when(validator.validate(any())).thenReturn(Set.of(violation));

                        assertThatThrownBy(() -> service.update(request).await().indefinitely())
                                        .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
                }

                @Test
                @DisplayName("should reject update when user does not own the order")
                void updateOrder_DifferentOwner() {
                        UpdateOrderRequest request = new UpdateOrderRequest();
                        request.setOrderId(1);
                        request.setUserId(200);

                        Order existingOrder = createTestOrder(1L, 100, 100, 500);
                        when(orderQueryRepository.findOrderById(1L))
                                        .thenReturn(Uni.createFrom().item(Optional.of(existingOrder)));

                        when(userPort.findById(anyInt()))
                                        .thenReturn(Uni.createFrom().item(new User(
                                                        200, "Other", "User", "other@test.com", null, null, null)));

                        assertThatThrownBy(() -> service.update(request).await().indefinitely())
                                        .isInstanceOf(ForbiddenException.class)
                                        .hasMessageContaining("not allowed");
                }

                @Test
                @DisplayName("should fail when user not found")
                void updateOrder_UserNotFound() {
                        UpdateOrderRequest request = new UpdateOrderRequest();
                        request.setOrderId(1);
                        request.setUserId(999);

                        Order existingOrder = createTestOrder(1L, 100, 100, 500);
                        when(orderQueryRepository.findOrderById(1L))
                                        .thenReturn(Uni.createFrom().item(Optional.of(existingOrder)));

                        when(userPort.findById(anyInt()))
                                        .thenReturn(Uni.createFrom().failure(
                                                        new ResourceNotFoundException("User not found: 999")));

                        assertThatThrownBy(() -> service.update(request).await().indefinitely())
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("User not found");
                }
        }

        @Nested
        @DisplayName("trash order tests")
        class TrashOrderTests {

                @Test
                @DisplayName("should successfully trash existing order")
                void trashOrder_Success() {
                        Long orderId = 1L;
                        Order trashedOrder = createTestOrder(orderId, 100, 100, 500);
                        trashedOrder.setDeletedAt(Timestamp.valueOf(LocalDateTime.now()));

                        when(orderCommandRepository.trashed(orderId)).thenReturn(Uni.createFrom().item(trashedOrder));

                        ApiResponse<OrderResponseDeleteAt> response = service.trash(orderId).await().indefinitely();

                        assertThat(response).isNotNull();
                        assertThat(response.status()).isEqualTo("success");
                        assertThat(response.message()).isEqualTo("Order trashed successfully!");
                        assertThat(response.data()).isNotNull();
                        assertThat(response.data().getId()).isEqualTo(orderId);
                }

                @Test
                @DisplayName("should fail when order not found or already trashed")
                void trashOrder_NotFound() {
                        Long orderId = 999L;

                        when(orderCommandRepository.trashed(orderId)).thenReturn(Uni.createFrom().nullItem());

                        assertThatThrownBy(() -> service.trash(orderId).await().indefinitely())
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("Order not found or already trashed");
                }
        }

        @Nested
        @DisplayName("restore order tests")
        class RestoreOrderTests {

                @Test
                @DisplayName("should successfully restore trashed order")
                void restoreOrder_Success() {
                        Long orderId = 1L;
                        Order restoredOrder = createTestOrder(orderId, 100, 100, 500);

                        when(orderCommandRepository.restore(orderId)).thenReturn(Uni.createFrom().item(restoredOrder));

                        ApiResponse<OrderResponseDeleteAt> response = service.restore(orderId).await().indefinitely();

                        assertThat(response).isNotNull();
                        assertThat(response.status()).isEqualTo("success");
                        assertThat(response.message()).isEqualTo("Order restored successfully!");
                        assertThat(response.data()).isNotNull();
                }

                @Test
                @DisplayName("should fail when order not found or not trashed")
                void restoreOrder_NotFound() {
                        Long orderId = 999L;

                        when(orderCommandRepository.restore(orderId)).thenReturn(Uni.createFrom().nullItem());

                        assertThatThrownBy(() -> service.restore(orderId).await().indefinitely())
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("Order not found or not trashed");
                }
        }

        @Nested
        @DisplayName("delete order tests")
        class DeleteOrderTests {

                @Test
                @DisplayName("should successfully permanently delete trashed order")
                void deleteOrder_Success() {
                        Long orderId = 1L;
                        Order deletedOrder = createTestOrder(orderId, 100, 100, 500);

                        when(orderCommandRepository.deletePermanent(orderId))
                                        .thenReturn(Uni.createFrom().item(deletedOrder));

                        ApiResponse<Void> response = service.delete(orderId).await().indefinitely();

                        assertThat(response).isNotNull();
                        assertThat(response.status()).isEqualTo("success");
                        assertThat(response.message()).isEqualTo("Order permanently deleted!");
                }

                @Test
                @DisplayName("should fail when order not found or not trashed")
                void deleteOrder_NotFound() {
                        Long orderId = 999L;

                        when(orderCommandRepository.deletePermanent(orderId)).thenReturn(Uni.createFrom().nullItem());

                        assertThatThrownBy(() -> service.delete(orderId).await().indefinitely())
                                        .isInstanceOf(InvalidRequestException.class)
                                        .hasMessageContaining("must be trashed");
                }
        }

        @Nested
        @DisplayName("restore all orders tests")
        class RestoreAllTests {

                @Test
                @DisplayName("should successfully restore all trashed orders")
                void restoreAll_Success() {
                        Order trashed = createTestOrder(1L, 100, 100, 500);
                        when(orderCommandRepository.findAllDeleted())
                                        .thenReturn(Uni.createFrom().item(List.of(trashed)));
                        when(orderCommandRepository.restore(1L))
                                        .thenReturn(Uni.createFrom().item(trashed));

                        ApiResponse<Void> response = service.restoreAll().await().indefinitely();

                        assertThat(response).isNotNull();
                        assertThat(response.status()).isEqualTo("success");
                        assertThat(response.message()).isEqualTo("All orders restored successfully!");
                }

                @Test
                @DisplayName("should fail when no trashed orders found")
                void restoreAll_NoTrashedOrders() {
                        when(orderCommandRepository.findAllDeleted())
                                        .thenReturn(Uni.createFrom().item(List.of()));

                        assertThatThrownBy(() -> service.restoreAll().await().indefinitely())
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("No trashed orders found");
                }
        }

        @Nested
        @DisplayName("delete all orders tests")
        class DeleteAllTests {

                @Test
                @DisplayName("should successfully delete all trashed orders")
                void deleteAll_Success() {
                        when(orderCommandRepository.deleteAllDeleted())
                                        .thenReturn(Uni.createFrom().item(true));

                        ApiResponse<Void> response = service.deleteAll().await().indefinitely();

                        assertThat(response).isNotNull();
                        assertThat(response.status()).isEqualTo("success");
                        assertThat(response.message()).isEqualTo("All orders permanently deleted!");
                }

                @Test
                @DisplayName("should fail when no trashed orders found")
                void deleteAll_NoTrashedOrders() {
                        when(orderCommandRepository.deleteAllDeleted())
                                        .thenReturn(Uni.createFrom().item(false));

                        assertThatThrownBy(() -> service.deleteAll().await().indefinitely())
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("No trashed orders found");
                }
        }
}
