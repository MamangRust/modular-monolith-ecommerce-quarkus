package com.sanedge.cart.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.cart.domain.requests.CreateCartRequest;
import com.sanedge.cart.domain.requests.DeleteCartRequest;
import com.sanedge.cart.domain.requests.FindAllCartsRequest;
import com.sanedge.cart.domain.response.CartResponse;
import com.sanedge.cart.entity.Cart;
import com.sanedge.cart.repository.CartCommandRepository;
import com.sanedge.cart.repository.CartQueryRepository;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.domain.response.ApiResponsePagination;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartQueryRepository cartQueryRepository;

    @Mock
    private CartCommandRepository cartCommandRepository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private CartServiceImpl cartService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();

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

        cartService = new CartServiceImpl(
                cartQueryRepository,
                cartCommandRepository,
                redisService,
                objectMapper,
                tracingMetrics
        );
    }

    // ========== CREATE CART TESTS ==========

    @Test
    @DisplayName("createCart - should successfully create a cart")
    void createCart_Success() {
        CreateCartRequest request = new CreateCartRequest();
        request.setUserId(1);
        request.setProductId(100);
        request.setQuantity(2);

        Cart savedCart = createTestCart(1L, 1, 100, "Product 100", 2);

        when(cartCommandRepository.persist(any(Cart.class)))
                .thenReturn(Uni.createFrom().item(savedCart));

        ApiResponse<CartResponse> result = cartService.createCart(request)
                .await().indefinitely();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Cart created successfully");
        assertThat(result.data()).isNotNull();
        assertThat(result.data().getUserId()).isEqualTo(1);
        assertThat(result.data().getProductId()).isEqualTo(100);
        assertThat(result.data().getQuantity()).isEqualTo(2);
        verify(cartCommandRepository).persist(any(Cart.class));
    }

    // ========== DELETE CART (PERMANENT) TESTS ==========

    @Test
    @DisplayName("deletePermanent - should successfully delete cart permanently")
    void deletePermanent_Success() {
        Long cartId = 1L;
        Cart existingCart = createTestCart(cartId, 1, 100, "Product 100", 2);

        when(cartCommandRepository.findById(cartId))
                .thenReturn(Uni.createFrom().item(existingCart));
        when(cartCommandRepository.deleteCartById(cartId))
                .thenReturn(Uni.createFrom().item(true));

        ApiResponse<Void> result = cartService.deletePermanent(cartId)
                .await().indefinitely();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Cart deleted permanently");
        verify(cartCommandRepository).findById(cartId);
        verify(cartCommandRepository).deleteCartById(cartId);
    }

    @Test
    @DisplayName("deletePermanent - should fail when cart not found")
    void deletePermanent_NotFound() {
        Long cartId = 999L;

        when(cartCommandRepository.findById(cartId))
                .thenReturn(Uni.createFrom().nullItem());

        assertThatThrownBy(() -> cartService.deletePermanent(cartId).await().indefinitely())
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Cart not found with id: " + cartId);
    }

    // ========== DELETE ALL CARTS (PERMANENT) TESTS ==========

    @Test
    @DisplayName("deleteAllPermanently - should successfully delete all carts")
    void deleteAllPermanently_Success() {
        DeleteCartRequest request = new DeleteCartRequest();
        request.setCartIds(List.of(1, 2, 3));

        when(cartCommandRepository.deleteCartsByIds(any()))
                .thenReturn(Uni.createFrom().item(true));

        ApiResponse<Void> result = cartService.deleteAllPermanently(request)
                .await().indefinitely();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Carts deleted permanently");
        verify(cartCommandRepository).deleteCartsByIds(any());
    }

    // ========== FIND ALL CARTS TESTS ==========

    @Test
    @DisplayName("findAll - should fetch from DB and cache when cache miss")
    void findAll_CacheMiss() {
        FindAllCartsRequest request = new FindAllCartsRequest();
        request.setUserId(1);
        request.setPage(1);
        request.setPageSize(10);
        request.setSearch(null);

        Cart cart1 = createTestCart(1L, 1, 100, "Product 100", 2);
        Cart cart2 = createTestCart(2L, 1, 101, "Product 101", 1);

        PagedResult<Cart> pagedResult = new PagedResult<>(List.of(cart1, cart2), 2);

        // Cache miss
        when(redisService.getReactive(anyString()))
                .thenReturn(Uni.createFrom().nullItem());
        when(cartQueryRepository.findCartsByUser(any()))
                .thenReturn(Uni.createFrom().item(pagedResult));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponsePagination<List<CartResponse>> result = cartService.findAll(request)
                .await().indefinitely();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.data()).hasSize(2);
        verify(cartQueryRepository).findCartsByUser(any());
        verify(redisService).setWithExpirationReactive(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("findAll - should return empty list when no carts found")
    void findAll_EmptyList() {
        FindAllCartsRequest request = new FindAllCartsRequest();
        request.setUserId(999);
        request.setPage(1);
        request.setPageSize(10);
        request.setSearch(null);

        PagedResult<Cart> emptyResult = new PagedResult<>(List.of(), 0);

        when(redisService.getReactive(anyString()))
                .thenReturn(Uni.createFrom().nullItem());
        when(cartQueryRepository.findCartsByUser(any()))
                .thenReturn(Uni.createFrom().item(emptyResult));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponsePagination<List<CartResponse>> result = cartService.findAll(request)
                .await().indefinitely();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.data()).isEmpty();
        assertThat(result.pagination().totalRecords()).isEqualTo(0);
    }

    @Test
    @DisplayName("findAll - should handle search query correctly")
    void findAll_WithSearch() {
        FindAllCartsRequest request = new FindAllCartsRequest();
        request.setUserId(1);
        request.setPage(1);
        request.setPageSize(10);
        request.setSearch("Product 100");

        Cart cart1 = createTestCart(1L, 1, 100, "Product 100", 2);

        PagedResult<Cart> pagedResult = new PagedResult<>(List.of(cart1), 1);

        when(redisService.getReactive(anyString()))
                .thenReturn(Uni.createFrom().nullItem());
        when(cartQueryRepository.findCartsByUser(any()))
                .thenReturn(Uni.createFrom().item(pagedResult));
        when(redisService.setWithExpirationReactive(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponsePagination<List<CartResponse>> result = cartService.findAll(request)
                .await().indefinitely();

        assertThat(result).isNotNull();
        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).getName()).isEqualTo("Product 100");
        verify(cartQueryRepository).findCartsByUser(any());
    }

    // ========== HELPER METHODS ==========

    private Cart createTestCart(Long id, Integer userId, Integer productId, String name, Integer quantity) {
        Cart cart = new Cart();
        try {
            // PanacheEntity has the id field, so we need to access it through the parent class hierarchy
            java.lang.reflect.Field idField = null;
            Class<?> clazz = cart.getClass();
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
                idField.set(cart, id);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to set cart id", e);
        }
        cart.setUserId(userId);
        cart.setProductId(productId);
        cart.setName(name);
        cart.setPrice(100);
        cart.setImage("default.png");
        cart.setQuantity(quantity);
        cart.setWeight(100);
        cart.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
        cart.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
        return cart;
    }
}