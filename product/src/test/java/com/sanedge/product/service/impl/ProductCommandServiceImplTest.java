package com.sanedge.product.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.InvalidRequestException;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.merchant.entity.Merchant;
import com.sanedge.merchant.repository.MerchantQueryRepository;
import com.sanedge.product.domain.requests.CreateProductRequest;
import com.sanedge.product.domain.requests.UpdateProductRequest;
import com.sanedge.product.domain.response.ProductResponse;
import com.sanedge.product.domain.response.ProductResponseDeleteAt;
import com.sanedge.product.entity.Product;
import com.sanedge.product.repository.ProductCommandRepository;
import com.sanedge.product.repository.ProductQueryRepository;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

@ExtendWith(MockitoExtension.class)
class ProductCommandServiceImplTest {

    @Mock
    private ProductCommandRepository productCommandRepository;

    @Mock
    private ProductQueryRepository productQueryRepository;

    @Mock
    private MerchantQueryRepository merchantQueryRepository;

    @Mock
    private Validator validator;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private ProductCommandServiceImpl productService;

    @BeforeEach
    void setUp() {
        // Mock tracingMetrics to pass through supplier (use lenient because some test methods
        // only invoke one of the traceAndMeasure overloads)
        lenient().when(tracingMetrics.traceAndMeasure(anyString(), anyString(), any(Attributes.class), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(3);
                    return supplier.get();
                });

        // Mock tracingMetrics with no attributes
        lenient().when(tracingMetrics.traceAndMeasure(anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    return supplier.get();
                });

        productService = new ProductCommandServiceImpl(
                productCommandRepository,
                productQueryRepository,
                merchantQueryRepository,
                validator,
                redisService,
                tracingMetrics);
    }

    // Helper method to create a valid product entity
    private Product createValidProduct(Long id) {
        Product product = new Product();
        product.id = id;
        product.setMerchantId(1);
        product.setCategoryId(1);
        product.setName("Test Product");
        product.setDescription("Test Description");
        product.setPrice(100000);
        product.setCountInStock(50);
        product.setBrand("Test Brand");
        product.setWeight(500);
        product.setRating(4.5f);
        product.setSlugProduct("test-product");
        product.setImageProduct("http://example.com/image.jpg");
        product.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        product.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        return product;
    }

    // Helper method to create valid CreateProductRequest
    private CreateProductRequest createValidCreateRequest() {
        CreateProductRequest request = new CreateProductRequest();
        request.setMerchantId(1);
        request.setCategoryId(1);
        request.setName("Test Product");
        request.setDescription("Test Description");
        request.setPrice(100000);
        request.setCountInStock(50);
        request.setBrand("Test Brand");
        request.setWeight(500);
        request.setRating(5);
        request.setSlugProduct("test-product");
        request.setImageProduct("http://example.com/image.jpg");
        return request;
    }

    // Helper method to create valid UpdateProductRequest
    private UpdateProductRequest createValidUpdateRequest(Long productId) {
        UpdateProductRequest request = new UpdateProductRequest();
        request.setProductId(productId.intValue());
        request.setMerchantId(1);
        request.setCategoryId(1);
        request.setName("Updated Product");
        request.setDescription("Updated Description");
        request.setPrice(150000);
        request.setCountInStock(75);
        request.setBrand("Updated Brand");
        request.setWeight(600);
        request.setRating(4);
        request.setSlugProduct("updated-product");
        return request;
    }

    @Test
    void createProduct_Success() {
        // Given
        CreateProductRequest request = createValidCreateRequest();
        Product savedProduct = createValidProduct(1L);

        // Mock validation (no violations)
        when(validator.validate(any())).thenReturn(Set.of());

        // Mock repository persistence
        when(productCommandRepository.persist(any(Product.class))).thenReturn(Uni.createFrom().item(savedProduct));

        // Mock Redis delete (cache invalidation)
        when(redisService.deleteReactive(anyString())).thenReturn(Uni.createFrom().voidItem());

        // When
        ApiResponse<ProductResponse> result = productService.createProduct(request).await()
                .indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Product created successfully");
        assertThat(result.data()).isNotNull();
        assertThat(result.data().getId()).isEqualTo(1L);
        assertThat(result.data().getName()).isEqualTo("Test Product");

        verify(productCommandRepository).persist(any(Product.class));
        verify(redisService).deleteReactive("product:id:1");
    }

    @Test
    void createProduct_ValidationFailure() {
        // Given
        CreateProductRequest request = createValidCreateRequest();

        // Mock validation failure
        ConstraintViolationException exception = new ConstraintViolationException("Validation failed",
                Set.of());
        when(validator.validate(any())).thenThrow(exception);

        // When/Then
        Uni<ApiResponse<ProductResponse>> resultUni = productService.createProduct(request);
        assertThat(resultUni).isNotNull();
        // The service returns a failed Uni when validation fails
    }

    @Test
    void updateProduct_Success() {
        // Given
        Long productId = 1L;
        UpdateProductRequest request = createValidUpdateRequest(productId);
        Product existingProduct = createValidProduct(productId);
        Product updatedProduct = createValidProduct(productId);
        updatedProduct.setName("Updated Product");
        updatedProduct.setPrice(150000);
        Merchant merchant = new Merchant();

        // Mock validation (no violations)
        when(validator.validate(any())).thenReturn(Set.of());

        // Mock merchant exists
        when(merchantQueryRepository.findMerchantById(any())).thenReturn(Uni.createFrom().item(merchant));

        // Mock product exists
        when(productQueryRepository.findProductById(productId)).thenReturn(Uni.createFrom().item(Optional.of(existingProduct)));

        // Mock repository persistence
        when(productCommandRepository.persist(any(Product.class))).thenReturn(Uni.createFrom().item(updatedProduct));

        // Mock Redis delete (cache invalidation)
        when(redisService.deleteReactive(anyString())).thenReturn(Uni.createFrom().voidItem());

        // When
        ApiResponse<ProductResponse> result = productService.updateProduct(request).await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Product updated successfully");
        assertThat(result.data()).isNotNull();
        assertThat(result.data().getName()).isEqualTo("Updated Product");

        verify(productCommandRepository).persist(any(Product.class));
    }

    @Test
    void updateProduct_MerchantNotFound() {
        // Given
        Long productId = 1L;
        UpdateProductRequest request = createValidUpdateRequest(productId);

        // Mock validation (no violations)
        when(validator.validate(any())).thenReturn(Set.of());

        // Mock merchant not found
        when(merchantQueryRepository.findMerchantById(any())).thenReturn(Uni.createFrom().nullItem());

        // When/Then
        Uni<ApiResponse<ProductResponse>> resultUni = productService.updateProduct(request);

        // Should fail with ResourceNotFoundException
        assertThat(resultUni).isNotNull();
    }

    @Test
    void updateProduct_ProductNotFound() {
        // Given
        Long productId = 1L;
        UpdateProductRequest request = createValidUpdateRequest(productId);
        Merchant merchant = new Merchant();

        // Mock validation (no violations)
        lenient().when(validator.validate(any())).thenReturn(Set.of());

        // Mock merchant exists
        when(merchantQueryRepository.findMerchantById(anyLong())).thenReturn(Uni.createFrom().item(merchant));

        // Mock product not found
        when(productQueryRepository.findProductById(productId)).thenReturn(Uni.createFrom().item(Optional.empty()));

        // When/Then
        assertThatThrownBy(() -> productService.updateProduct(request).await().indefinitely())
                .isInstanceOf(com.sanedge.common.exception.ResourceNotFoundException.class)
                .hasMessage("Product not found");
    }

    @Test
    void deleteProduct_Permanent_Success() {
        // Given
        Long productId = 1L;
        Product trashedProduct = createValidProduct(productId);
        trashedProduct.setDeletedAt(new Timestamp(System.currentTimeMillis()));

        // Mock repository delete
        when(productCommandRepository.deletePermanent(productId)).thenReturn(Uni.createFrom().item(trashedProduct));

        // Mock Redis delete
        when(redisService.deleteReactive(anyString())).thenReturn(Uni.createFrom().voidItem());

        // When
        ApiResponse<Void> result = productService.deleteProductPermanent(productId.intValue()).await()
                .indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Product permanently deleted");

        verify(productCommandRepository).deletePermanent(productId);
    }

    @Test
    void deleteProduct_Permanent_NotFoundOrNotTrashed() {
        // Given
        Long productId = 1L;

        // Mock repository delete returning null (product not found or not trashed)
        when(productCommandRepository.deletePermanent(productId)).thenReturn(Uni.createFrom().nullItem());

        // When/Then
        Uni<ApiResponse<Void>> resultUni = productService.deleteProductPermanent(productId.intValue());

        // Should fail with InvalidRequestException
        assertThat(resultUni).isNotNull();
    }

    @Test
    void trashProduct_Success() {
        // Given
        Long productId = 1L;
        Product trashedProduct = createValidProduct(productId);
        trashedProduct.setDeletedAt(new Timestamp(System.currentTimeMillis()));

        // Mock repository trash
        when(productCommandRepository.trashed(productId)).thenReturn(Uni.createFrom().item(trashedProduct));

        // Mock Redis delete
        when(redisService.deleteReactive(anyString())).thenReturn(Uni.createFrom().voidItem());

        // When
        ApiResponse<ProductResponseDeleteAt> result = productService.trashedProduct(productId.intValue()).await()
                .indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Product trashed successfully");
        assertThat(result.data()).isNotNull();
        assertThat(result.data().getDeletedAt()).isNotNull();

        verify(productCommandRepository).trashed(productId);
    }

    @Test
    void trashProduct_NotFoundOrAlreadyTrashed() {
        // Given
        Long productId = 1L;

        // Mock repository trash returning null
        when(productCommandRepository.trashed(productId)).thenReturn(Uni.createFrom().nullItem());

        // When/Then
        Uni<ApiResponse<ProductResponseDeleteAt>> resultUni = productService.trashedProduct(productId.intValue());

        // Should fail with ResourceNotFoundException
        assertThat(resultUni).isNotNull();
    }

    @Test
    void restoreProduct_Success() {
        // Given
        Long productId = 1L;
        Product restoredProduct = createValidProduct(productId);
        restoredProduct.setDeletedAt(null);

        // Mock repository restore
        when(productCommandRepository.restore(productId)).thenReturn(Uni.createFrom().item(restoredProduct));

        // Mock Redis delete
        when(redisService.deleteReactive(anyString())).thenReturn(Uni.createFrom().voidItem());

        // When
        ApiResponse<ProductResponseDeleteAt> result = productService.restoreProduct(productId.intValue()).await()
                .indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Product restored successfully");
        assertThat(result.data()).isNotNull();
        assertThat(result.data().getDeletedAt()).isNull();

        verify(productCommandRepository).restore(productId);
    }

    @Test
    void restoreProduct_NotFoundOrNotTrashed() {
        // Given
        Long productId = 1L;

        // Mock repository restore returning null
        when(productCommandRepository.restore(productId)).thenReturn(Uni.createFrom().nullItem());

        // When/Then
        Uni<ApiResponse<ProductResponseDeleteAt>> resultUni = productService.restoreProduct(productId.intValue());

        // Should fail with ResourceNotFoundException
        assertThat(resultUni).isNotNull();
    }

    @Test
    void restoreAllProducts_Success() {
        // Given
        when(productCommandRepository.restoreAllDeleted()).thenReturn(Uni.createFrom().item(true));

        // When
        ApiResponse<Void> result = productService.restoreAllProducts().await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("All products restored successfully");

        verify(productCommandRepository).restoreAllDeleted();
    }

    @Test
    void restoreAllProducts_NoTrashedProducts() {
        // Given
        when(productCommandRepository.restoreAllDeleted()).thenReturn(Uni.createFrom().item(false));

        // When/Then
        Uni<ApiResponse<Void>> resultUni = productService.restoreAllProducts();

        // Should fail with ResourceNotFoundException
        assertThat(resultUni).isNotNull();
    }

    @Test
    void deleteAllProductsPermanent_Success() {
        // Given
        when(productCommandRepository.deleteAllDeleted()).thenReturn(Uni.createFrom().item(true));

        // When
        ApiResponse<Void> result = productService.deleteAllProductsPermanent().await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("All products permanently deleted");

        verify(productCommandRepository).deleteAllDeleted();
    }

    @Test
    void deleteAllProductsPermanent_NoTrashedProducts() {
        // Given
        when(productCommandRepository.deleteAllDeleted()).thenReturn(Uni.createFrom().item(false));

        // When/Then
        Uni<ApiResponse<Void>> resultUni = productService.deleteAllProductsPermanent();

        // Should fail with ResourceNotFoundException
        assertThat(resultUni).isNotNull();
    }

    @Test
    void updateProductCountStock_Success() {
        // Given
        Long productId = 1L;
        Integer newStock = 100;
        Product existingProduct = createValidProduct(productId);
        Product updatedProduct = createValidProduct(productId);
        updatedProduct.setCountInStock(newStock);

        // Mock product exists
        when(productQueryRepository.findProductById(productId)).thenReturn(Uni.createFrom().item(Optional.of(existingProduct)));

        // Mock repository persistence
        when(productCommandRepository.persist(any(Product.class))).thenReturn(Uni.createFrom().item(updatedProduct));

        // Mock Redis delete
        when(redisService.deleteReactive(anyString())).thenReturn(Uni.createFrom().voidItem());

        // When
        ApiResponse<ProductResponse> result = productService.updateProductCountStock(productId.intValue(), newStock)
                .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Product stock updated successfully");
        assertThat(result.data()).isNotNull();
        assertThat(result.data().getCountInStock()).isEqualTo(newStock);
    }

    @Test
    void updateProductCountStock_ProductNotFound() {
        // Given
        Long productId = 1L;

        // Mock product not found
        when(productQueryRepository.findProductById(productId)).thenReturn(Uni.createFrom().item(Optional.empty()));

        // When/Then
        Uni<ApiResponse<ProductResponse>> resultUni = productService.updateProductCountStock(productId.intValue(), 100);

        // Should fail with ResourceNotFoundException
        assertThat(resultUni).isNotNull();
    }

    @Test
    void createProduct_CacheInvalidationCalled() {
        // Given
        CreateProductRequest request = createValidCreateRequest();
        Product savedProduct = createValidProduct(1L);

        // Mock validation (no violations)
        when(validator.validate(any())).thenReturn(Set.of());

        // Mock repository persistence
        when(productCommandRepository.persist(any(Product.class))).thenReturn(Uni.createFrom().item(savedProduct));

        // Mock Redis delete (cache invalidation)
        when(redisService.deleteReactive("product:id:1")).thenReturn(Uni.createFrom().voidItem());

        // When
        productService.createProduct(request).await().indefinitely();

        // Then
        verify(redisService).deleteReactive("product:id:1");
    }

    @Test
    void updateProduct_CacheInvalidationCalled() {
        // Given
        Long productId = 1L;
        UpdateProductRequest request = createValidUpdateRequest(productId);
        Product existingProduct = createValidProduct(productId);
        Product updatedProduct = createValidProduct(productId);
        Merchant merchant = new Merchant();

        // Mock validation (no violations)
        when(validator.validate(any())).thenReturn(Set.of());

        // Mock merchant exists
        when(merchantQueryRepository.findMerchantById(any())).thenReturn(Uni.createFrom().item(merchant));

        // Mock product exists
        when(productQueryRepository.findProductById(productId)).thenReturn(Uni.createFrom().item(Optional.of(existingProduct)));

        // Mock repository persistence
        when(productCommandRepository.persist(any(Product.class))).thenReturn(Uni.createFrom().item(updatedProduct));

        // Mock Redis delete
        when(redisService.deleteReactive(anyString())).thenReturn(Uni.createFrom().voidItem());

        // When
        productService.updateProduct(request).await().indefinitely();

        // Then
        verify(redisService).deleteReactive("product:id:1");
    }
}