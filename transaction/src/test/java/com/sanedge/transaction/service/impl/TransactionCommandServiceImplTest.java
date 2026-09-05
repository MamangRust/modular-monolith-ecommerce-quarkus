package com.sanedge.transaction.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.InvalidRequestException;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.transaction.domain.requests.CreateTransactionRequest;
import com.sanedge.transaction.domain.requests.UpdateTransactionRequest;
import com.sanedge.transaction.domain.response.TransactionResponse;
import com.sanedge.transaction.domain.response.TransactionResponseDeleteAt;
import com.sanedge.transaction.entity.Transaction;
import com.sanedge.transaction.enums.PaymentStatus;
import com.sanedge.transaction.repository.TransactionCommandRepository;
import com.sanedge.transaction.repository.TransactionQueryRepository;
import com.sanedge.transaction.service.KafkaService;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import pb.order.OrderCommon;

@ExtendWith(MockitoExtension.class)
class TransactionCommandServiceImplTest {

    @Mock
    private TransactionQueryRepository transactionQueryRepository;

    @Mock
    private TransactionCommandRepository transactionCommandRepository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    @Mock
    private pb.merchant.MerchantQueryService merchantQueryService;

    @Mock
    private pb.order.OrderQueryService orderQueryService;

    @Mock
    private pb.order_item.OrderItemQueryService orderItemQueryService;

    @Mock
    private pb.shipping_address.ShippingQueryService shippingQueryService;

    @Mock
    private pb.user.UserQueryService userQueryService;

    @Mock
    private KafkaService kafkaService;

    private TransactionCommandServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new TransactionCommandServiceImpl(
                transactionQueryRepository,
                transactionCommandRepository,
                redisService,
                tracingMetrics);

        // Direct field assignment (same package access - impl subpackage)
        service.merchantQueryService = merchantQueryService;
        service.orderQueryService = orderQueryService;
        service.orderItemQueryService = orderItemQueryService;
        service.shippingQueryService = shippingQueryService;
        service.userQueryService = userQueryService;
        service.kafkaService = kafkaService;

        lenient().when(tracingMetrics.traceAndMeasure(anyString(), anyString(), any(Attributes.class), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<Uni<?>>) inv.getArgument(3)).get());
        lenient().when(tracingMetrics.traceAndMeasure(anyString(), anyString(), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<Uni<?>>) inv.getArgument(2)).get());

        lenient().when(redisService.deleteReactive(anyString()))
                .thenReturn(Uni.createFrom().voidItem());
    }

    // ========== Helper Methods ==========

    private Transaction createTestTransaction(Long id, Integer orderId, Integer merchantId, PaymentStatus status) {
        Transaction tx = new Transaction();
        tx.id = id;
        tx.setOrderId(orderId);
        tx.setMerchantId(merchantId);
        tx.setPaymentMethod("Credit Card");
        tx.setAmount(110000);
        tx.setStatus(status);
        tx.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
        tx.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
        return tx;
    }

    private CreateTransactionRequest createValidCreateRequest() {
        CreateTransactionRequest req = new CreateTransactionRequest();
        req.setOrderID(100);
        req.setMerchantID(200);
        req.setPaymentMethod("Credit Card");
        req.setAmount(110000);
        req.setPaymentStatus("success");
        return req;
    }

    private void mockMerchantOrderOrderItemShipping() {
        pb.merchant.MerchantCommon.ApiResponseMerchant merchantResponse = pb.merchant.MerchantCommon.ApiResponseMerchant
                .newBuilder()
                .setData(pb.merchant.MerchantCommon.MerchantResponse.newBuilder().setId(200).build())
                .build();
        lenient().when(merchantQueryService.findById(any()))
                .thenReturn(Uni.createFrom().item(merchantResponse));

        OrderCommon.ApiResponseOrder orderResponse = OrderCommon.ApiResponseOrder.newBuilder()
                .setData(OrderCommon.OrderResponse.newBuilder().setId(100).setUserId(50).build())
                .build();
        lenient().when(orderQueryService.findById(any()))
                .thenReturn(Uni.createFrom().item(orderResponse));

        pb.order_item.OrderItemCommon.ApiResponsesOrderItem orderItemResponse = pb.order_item.OrderItemCommon.ApiResponsesOrderItem
                .newBuilder()
                .addData(pb.order_item.OrderItemCommon.OrderItemResponse.newBuilder()
                        .setId(1)
                        .setOrderId(100)
                        .setProductId(10)
                        .setQuantity(2)
                        .setPrice(50000)
                        .build())
                .build();
        lenient().when(orderItemQueryService.findOrderItemByOrder(any()))
                .thenReturn(Uni.createFrom().item(orderItemResponse));

        pb.shipping_address.ShippingAddressCommon.ApiResponseShipping shippingResponse = pb.shipping_address.ShippingAddressCommon.ApiResponseShipping
                .newBuilder()
                .setData(pb.shipping_address.ShippingAddressCommon.ShippingResponse.newBuilder()
                        .setId(1)
                        .setShippingCost(10000)
                        .build())
                .build();
        lenient().when(shippingQueryService.findByOrder(any()))
                .thenReturn(Uni.createFrom().item(shippingResponse));
    }

    // ========== CREATE TRANSACTION TESTS ==========

    @Nested
    @DisplayName("create transaction tests")
    class CreateTransactionTests {

        @Test
        @DisplayName("should fail when merchant not found")
        void create_MerchantNotFound() {
            CreateTransactionRequest req = createValidCreateRequest();

            pb.merchant.MerchantCommon.ApiResponseMerchant merchantResponse = pb.merchant.MerchantCommon.ApiResponseMerchant
                    .newBuilder()
                    .setData(pb.merchant.MerchantCommon.MerchantResponse.newBuilder().setId(0).build())
                    .build();
            when(merchantQueryService.findById(any())).thenReturn(Uni.createFrom().item(merchantResponse));

            assertThatThrownBy(() -> service.create(req).await().indefinitely())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Merchant not found");
        }

        @Test
        @DisplayName("should fail when order not found")
        void create_OrderNotFound() {
            CreateTransactionRequest req = createValidCreateRequest();

            pb.merchant.MerchantCommon.ApiResponseMerchant merchantResponse = pb.merchant.MerchantCommon.ApiResponseMerchant
                    .newBuilder()
                    .setData(pb.merchant.MerchantCommon.MerchantResponse.newBuilder().setId(200).build())
                    .build();
            when(merchantQueryService.findById(any())).thenReturn(Uni.createFrom().item(merchantResponse));

            OrderCommon.ApiResponseOrder orderResponse = OrderCommon.ApiResponseOrder.newBuilder()
                    .setData(OrderCommon.OrderResponse.newBuilder().setId(0).build())
                    .build();
            when(orderQueryService.findById(any())).thenReturn(Uni.createFrom().item(orderResponse));

            assertThatThrownBy(() -> service.create(req).await().indefinitely())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Order not found");
        }

        @Test
        @DisplayName("should fail when no order items found")
        void create_NoOrderItems() {
            CreateTransactionRequest req = createValidCreateRequest();

            pb.merchant.MerchantCommon.ApiResponseMerchant merchantResponse = pb.merchant.MerchantCommon.ApiResponseMerchant
                    .newBuilder()
                    .setData(pb.merchant.MerchantCommon.MerchantResponse.newBuilder().setId(200).build())
                    .build();
            when(merchantQueryService.findById(any())).thenReturn(Uni.createFrom().item(merchantResponse));

            OrderCommon.ApiResponseOrder orderResponse = OrderCommon.ApiResponseOrder.newBuilder()
                    .setData(OrderCommon.OrderResponse.newBuilder().setId(100).setUserId(50).build())
                    .build();
            when(orderQueryService.findById(any())).thenReturn(Uni.createFrom().item(orderResponse));

            pb.order_item.OrderItemCommon.ApiResponsesOrderItem emptyItems = pb.order_item.OrderItemCommon.ApiResponsesOrderItem
                    .newBuilder().build();
            when(orderItemQueryService.findOrderItemByOrder(any()))
                    .thenReturn(Uni.createFrom().item(emptyItems));

            pb.shipping_address.ShippingAddressCommon.ApiResponseShipping shippingResponse = pb.shipping_address.ShippingAddressCommon.ApiResponseShipping
                    .newBuilder()
                    .setData(pb.shipping_address.ShippingAddressCommon.ShippingResponse.newBuilder()
                            .setId(1).setShippingCost(10000).build())
                    .build();
            when(shippingQueryService.findByOrder(any())).thenReturn(Uni.createFrom().item(shippingResponse));

            assertThatThrownBy(() -> service.create(req).await().indefinitely())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No order items found");
        }

        @Test
        @DisplayName("should fail when shipping not found")
        void create_ShippingNotFound() {
            CreateTransactionRequest req = createValidCreateRequest();

            pb.merchant.MerchantCommon.ApiResponseMerchant merchantResponse = pb.merchant.MerchantCommon.ApiResponseMerchant
                    .newBuilder()
                    .setData(pb.merchant.MerchantCommon.MerchantResponse.newBuilder().setId(200).build())
                    .build();
            when(merchantQueryService.findById(any())).thenReturn(Uni.createFrom().item(merchantResponse));

            OrderCommon.ApiResponseOrder orderResponse = OrderCommon.ApiResponseOrder.newBuilder()
                    .setData(OrderCommon.OrderResponse.newBuilder().setId(100).setUserId(50).build())
                    .build();
            when(orderQueryService.findById(any())).thenReturn(Uni.createFrom().item(orderResponse));

            pb.order_item.OrderItemCommon.ApiResponsesOrderItem orderItemResponse = pb.order_item.OrderItemCommon.ApiResponsesOrderItem
                    .newBuilder()
                    .addData(pb.order_item.OrderItemCommon.OrderItemResponse.newBuilder()
                            .setId(1).setQuantity(2).setPrice(50000).build())
                    .build();
            when(orderItemQueryService.findOrderItemByOrder(any()))
                    .thenReturn(Uni.createFrom().item(orderItemResponse));

            pb.shipping_address.ShippingAddressCommon.ApiResponseShipping emptyShipping = pb.shipping_address.ShippingAddressCommon.ApiResponseShipping
                    .newBuilder()
                    .setData(pb.shipping_address.ShippingAddressCommon.ShippingResponse.newBuilder()
                            .setId(0).build())
                    .build();
            when(shippingQueryService.findByOrder(any())).thenReturn(Uni.createFrom().item(emptyShipping));

            assertThatThrownBy(() -> service.create(req).await().indefinitely())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Shipping address not found");
        }

        @Test
        @DisplayName("should fail when order item has invalid quantity")
        void create_InvalidOrderItemQuantity() {
            CreateTransactionRequest req = createValidCreateRequest();

            pb.merchant.MerchantCommon.ApiResponseMerchant merchantResponse = pb.merchant.MerchantCommon.ApiResponseMerchant
                    .newBuilder()
                    .setData(pb.merchant.MerchantCommon.MerchantResponse.newBuilder().setId(200).build())
                    .build();
            when(merchantQueryService.findById(any())).thenReturn(Uni.createFrom().item(merchantResponse));

            OrderCommon.ApiResponseOrder orderResponse = OrderCommon.ApiResponseOrder.newBuilder()
                    .setData(OrderCommon.OrderResponse.newBuilder().setId(100).setUserId(50).build())
                    .build();
            when(orderQueryService.findById(any())).thenReturn(Uni.createFrom().item(orderResponse));

            pb.order_item.OrderItemCommon.ApiResponsesOrderItem orderItemResponse = pb.order_item.OrderItemCommon.ApiResponsesOrderItem
                    .newBuilder()
                    .addData(pb.order_item.OrderItemCommon.OrderItemResponse.newBuilder()
                            .setId(1).setQuantity(0).setPrice(50000).build())
                    .build();
            when(orderItemQueryService.findOrderItemByOrder(any()))
                    .thenReturn(Uni.createFrom().item(orderItemResponse));

            pb.shipping_address.ShippingAddressCommon.ApiResponseShipping shippingResponse = pb.shipping_address.ShippingAddressCommon.ApiResponseShipping
                    .newBuilder()
                    .setData(pb.shipping_address.ShippingAddressCommon.ShippingResponse.newBuilder()
                            .setId(1).setShippingCost(10000).build())
                    .build();
            when(shippingQueryService.findByOrder(any())).thenReturn(Uni.createFrom().item(shippingResponse));

            assertThatThrownBy(() -> service.create(req).await().indefinitely())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid order item quantity");
        }

        @Test
        @DisplayName("should fail when payment amount is insufficient")
        void create_InsufficientPaymentAmount() {
            CreateTransactionRequest req = createValidCreateRequest();
            req.setAmount(1); // too low

            mockMerchantOrderOrderItemShipping();

            assertThatThrownBy(() -> service.create(req).await().indefinitely())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Insufficient payment amount");
        }

        @Test
        @DisplayName("should successfully create transaction on happy path (via @Spy)")
        void create_Success() {
            CreateTransactionRequest req = createValidCreateRequest();
            // Calculated total = (2 * 50000 + 10000) * 1.11 = 122100, so set amount above this threshold
            req.setAmount(130000);

            mockMerchantOrderOrderItemShipping();

            when(transactionCommandRepository.persist(any(Transaction.class)))
                    .thenAnswer(inv -> {
                        Transaction t = inv.getArgument(0);
                        if (t.id == null) t.id = 1L;
                        return Uni.createFrom().item(t);
                    });

            // @Spy to bypass the userQueryService + kafkaService chain inside sendTransactionEmail
            TransactionCommandServiceImpl spiedService = spy(service);
            doReturn(Uni.createFrom().voidItem())
                    .when(spiedService).sendTransactionEmail(any(Transaction.class), any(OrderCommon.OrderResponse.class));

            ApiResponse<TransactionResponse> response = spiedService.create(req).await().indefinitely();

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo("success");
            assertThat(response.message()).isEqualTo("Transaction created successfully");
            assertThat(response.data()).isNotNull();
            assertThat(response.data().getId()).isEqualTo(1L);
            assertThat(response.data().getOrderId()).isEqualTo(100);
            assertThat(response.data().getMerchantId()).isEqualTo(200);

            // Verify the cache invalidation ran (5 redis deletes: tx, order, all, active, trashed, merchant)
            verify(redisService, atLeastOnce()).deleteReactive(anyString());
        }
    }

    // ========== UPDATE TRANSACTION TESTS ==========

    @Nested
    @DisplayName("update transaction tests")
    class UpdateTransactionTests {

        @Test
        @DisplayName("should fail when transaction not found")
        void update_TransactionNotFound() {
            UpdateTransactionRequest req = new UpdateTransactionRequest();
            req.setTransactionID(999);
            req.setOrderID(100);
            req.setMerchantID(200);
            req.setPaymentMethod("Credit Card");
            req.setAmount(110000);
            req.setPaymentStatus("success");

            when(transactionQueryRepository.findTransactionById(999L))
                    .thenReturn(Uni.createFrom().item(java.util.Optional.empty()));

            assertThatThrownBy(() -> service.update(req).await().indefinitely())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Transaction not found");
        }

        @Test
        @DisplayName("should fail when transaction is SUCCESS and cannot be modified")
        void update_TransactionAlreadySuccess() {
            UpdateTransactionRequest req = new UpdateTransactionRequest();
            req.setTransactionID(1);
            req.setOrderID(100);
            req.setMerchantID(200);
            req.setPaymentMethod("Credit Card");
            req.setAmount(110000);
            req.setPaymentStatus("success");

            Transaction existingTx = createTestTransaction(1L, 100, 200, PaymentStatus.SUCCESS);
            when(transactionQueryRepository.findTransactionById(1L))
                    .thenReturn(Uni.createFrom().item(java.util.Optional.of(existingTx)));

            assertThatThrownBy(() -> service.update(req).await().indefinitely())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Transaction cannot be modified");
        }

        @Test
        @DisplayName("should fail when transaction is REFUNDED and cannot be modified")
        void update_TransactionRefunded() {
            UpdateTransactionRequest req = new UpdateTransactionRequest();
            req.setTransactionID(1);
            req.setOrderID(100);
            req.setMerchantID(200);
            req.setPaymentMethod("Credit Card");
            req.setAmount(110000);
            req.setPaymentStatus("success");

            Transaction existingTx = createTestTransaction(1L, 100, 200, PaymentStatus.REFUNDED);
            when(transactionQueryRepository.findTransactionById(1L))
                    .thenReturn(Uni.createFrom().item(java.util.Optional.of(existingTx)));

            assertThatThrownBy(() -> service.update(req).await().indefinitely())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Transaction cannot be modified");
        }

        @Test
        @DisplayName("should fail when merchant not found during update")
        void update_MerchantNotFound() {
            UpdateTransactionRequest req = new UpdateTransactionRequest();
            req.setTransactionID(1);
            req.setOrderID(100);
            req.setMerchantID(200);
            req.setPaymentMethod("Credit Card");
            req.setAmount(110000);
            req.setPaymentStatus("success");

            Transaction existingTx = createTestTransaction(1L, 100, 200, PaymentStatus.PENDING);
            when(transactionQueryRepository.findTransactionById(1L))
                    .thenReturn(Uni.createFrom().item(java.util.Optional.of(existingTx)));

            pb.merchant.MerchantCommon.ApiResponseMerchant merchantResponse = pb.merchant.MerchantCommon.ApiResponseMerchant
                    .newBuilder()
                    .setData(pb.merchant.MerchantCommon.MerchantResponse.newBuilder().setId(0).build())
                    .build();
            when(merchantQueryService.findById(any())).thenReturn(Uni.createFrom().item(merchantResponse));

            assertThatThrownBy(() -> service.update(req).await().indefinitely())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Merchant not found");
        }

        @Test
        @DisplayName("should successfully update transaction on happy path")
        void update_Success() {
            UpdateTransactionRequest req = new UpdateTransactionRequest();
            req.setTransactionID(1);
            req.setOrderID(100);
            req.setMerchantID(200);
            req.setPaymentMethod("Debit Card");
            // Calculated total = (2 * 50000 + 10000) * 1.11 = 122100, so set amount above this threshold
            req.setAmount(130000);
            req.setPaymentStatus("success");

            Transaction existingTx = createTestTransaction(1L, 100, 200, PaymentStatus.PENDING);
            when(transactionQueryRepository.findTransactionById(1L))
                    .thenReturn(Uni.createFrom().item(java.util.Optional.of(existingTx)));

            mockMerchantOrderOrderItemShipping();

            when(transactionCommandRepository.persist(any(Transaction.class)))
                    .thenAnswer(inv -> {
                        Transaction t = inv.getArgument(0);
                        return Uni.createFrom().item(t);
                    });

            ApiResponse<TransactionResponse> response = service.update(req).await().indefinitely();

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo("success");
            assertThat(response.message()).isEqualTo("Transaction updated successfully");
            assertThat(response.data()).isNotNull();
            assertThat(response.data().getId()).isEqualTo(1L);

            // Verify the cache invalidation ran
            verify(redisService, atLeastOnce()).deleteReactive(anyString());
        }
    }

    // ========== TRASH TRANSACTION TESTS ==========

    @Nested
    @DisplayName("trash transaction tests")
    class TrashTransactionTests {

        @Test
        @DisplayName("should successfully trash existing transaction")
        void trash_Success() {
            Integer id = 1;
            Transaction trashedTx = createTestTransaction(1L, 100, 200, PaymentStatus.SUCCESS);
            trashedTx.setDeletedAt(Timestamp.valueOf(LocalDateTime.now()));

            when(transactionCommandRepository.trashed(1L)).thenReturn(Uni.createFrom().item(trashedTx));

            ApiResponse<TransactionResponseDeleteAt> response = service.trash(id).await().indefinitely();

            assertThat(response.status()).isEqualTo("success");
            assertThat(response.message()).isEqualTo("Transaction trashed successfully");
            assertThat(response.data()).isNotNull();
            assertThat(response.data().getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should fail when transaction not found for trash")
        void trash_NotFound() {
            Integer id = 999;
            when(transactionCommandRepository.trashed(999L)).thenReturn(Uni.createFrom().nullItem());

            assertThatThrownBy(() -> service.trash(id).await().indefinitely())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("not found or already trashed");
        }
    }

    // ========== RESTORE TRANSACTION TESTS ==========

    @Nested
    @DisplayName("restore transaction tests")
    class RestoreTransactionTests {

        @Test
        @DisplayName("should successfully restore trashed transaction")
        void restore_Success() {
            Integer id = 1;
            Transaction restoredTx = createTestTransaction(1L, 100, 200, PaymentStatus.SUCCESS);

            when(transactionCommandRepository.restore(1L)).thenReturn(Uni.createFrom().item(restoredTx));

            ApiResponse<TransactionResponseDeleteAt> response = service.restore(id).await().indefinitely();

            assertThat(response.status()).isEqualTo("success");
            assertThat(response.message()).isEqualTo("Transaction restored successfully");
            assertThat(response.data()).isNotNull();
        }

        @Test
        @DisplayName("should fail when transaction not found or not trashed")
        void restore_NotFound() {
            Integer id = 999;
            when(transactionCommandRepository.restore(999L)).thenReturn(Uni.createFrom().nullItem());

            assertThatThrownBy(() -> service.restore(id).await().indefinitely())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("not found or not trashed");
        }
    }

    // ========== DELETE TRANSACTION TESTS ==========

    @Nested
    @DisplayName("delete transaction tests")
    class DeleteTransactionTests {

        @Test
        @DisplayName("should successfully permanently delete trashed transaction")
        void delete_Success() {
            Integer id = 1;
            Transaction deletedTx = createTestTransaction(1L, 100, 200, PaymentStatus.SUCCESS);
            deletedTx.setDeletedAt(Timestamp.valueOf(LocalDateTime.now()));

            when(transactionCommandRepository.deletePermanent(1L))
                    .thenReturn(Uni.createFrom().item(deletedTx));

            ApiResponse<Void> response = service.delete(id).await().indefinitely();

            assertThat(response.status()).isEqualTo("success");
            assertThat(response.message()).isEqualTo("Transaction permanently deleted");
        }

        @Test
        @DisplayName("should fail when transaction not found for delete")
        void delete_NotFound() {
            Integer id = 999;
            when(transactionCommandRepository.deletePermanent(999L)).thenReturn(Uni.createFrom().nullItem());

            assertThatThrownBy(() -> service.delete(id).await().indefinitely())
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("must be trashed before permanent deletion");
        }
    }

    // ========== RESTORE ALL TESTS ==========

    @Nested
    @DisplayName("restore all transactions tests")
    class RestoreAllTests {

        @Test
        @DisplayName("should successfully restore all trashed transactions")
        void restoreAll_Success() {
            when(transactionCommandRepository.restoreAllDeleted()).thenReturn(Uni.createFrom().item(true));

            ApiResponse<Void> response = service.restoreAll().await().indefinitely();

            assertThat(response.status()).isEqualTo("success");
            assertThat(response.message()).isEqualTo("All transactions restored successfully");
        }

        @Test
        @DisplayName("should fail when no trashed transactions found")
        void restoreAll_NoTrashed() {
            when(transactionCommandRepository.restoreAllDeleted()).thenReturn(Uni.createFrom().item(false));

            assertThatThrownBy(() -> service.restoreAll().await().indefinitely())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("No trashed transactions found");
        }
    }

    // ========== DELETE ALL TESTS ==========

    @Nested
    @DisplayName("delete all transactions tests")
    class DeleteAllTests {

        @Test
        @DisplayName("should successfully delete all trashed transactions")
        void deleteAll_Success() {
            when(transactionCommandRepository.deleteAllDeleted()).thenReturn(Uni.createFrom().item(true));

            ApiResponse<Void> response = service.deleteAll().await().indefinitely();

            assertThat(response.status()).isEqualTo("success");
            assertThat(response.message()).isEqualTo("All transactions permanently deleted");
        }

        @Test
        @DisplayName("should fail when no trashed transactions found")
        void deleteAll_NoTrashed() {
            when(transactionCommandRepository.deleteAllDeleted()).thenReturn(Uni.createFrom().item(false));

            assertThatThrownBy(() -> service.deleteAll().await().indefinitely())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("No trashed transactions found");
        }
    }

    // ========== DELETE BY ORDER TESTS ==========

    @Nested
    @DisplayName("delete transaction by order tests")
    class DeleteByOrderTests {

        @Test
        @DisplayName("should successfully delete transaction by order id")
        void deleteByOrder_Success() {
            Integer orderId = 100;
            when(transactionCommandRepository.deleteByOrderPermanent(100L))
                    .thenReturn(Uni.createFrom().item(true));

            ApiResponse<Boolean> response = service.deleteByOrder(orderId).await().indefinitely();

            assertThat(response.status()).isEqualTo("success");
            assertThat(response.data()).isTrue();
        }
    }
}
