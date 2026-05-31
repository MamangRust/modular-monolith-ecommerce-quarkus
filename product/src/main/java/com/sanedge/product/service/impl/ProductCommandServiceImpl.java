package com.sanedge.product.service.impl;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.common.config.RedisService;
import com.sanedge.product.domain.requests.CreateProductRequest;
import com.sanedge.product.domain.requests.UpdateProductRequest;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.product.domain.response.ProductResponse;
import com.sanedge.product.domain.response.ProductResponseDeleteAt;
import com.sanedge.product.entity.Product;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.merchant.repository.MerchantQueryRepository;
import com.sanedge.product.repository.ProductCommandRepository;
import com.sanedge.product.repository.ProductQueryRepository;
import com.sanedge.product.service.ProductCommandService;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

@ApplicationScoped
public class ProductCommandServiceImpl implements ProductCommandService {
    private static final Logger logger = LoggerFactory.getLogger(ProductCommandServiceImpl.class);

    ProductCommandRepository productCommandRepository;
    ProductQueryRepository productQueryRepository;
    MerchantQueryRepository merchantQueryRepository;
    Validator validator;
    OpenTelemetry openTelemetry;
    RedisService redisService;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    @Inject
    public ProductCommandServiceImpl(ProductCommandRepository productCommandRepository,
                                    ProductQueryRepository productQueryRepository,
                                    MerchantQueryRepository merchantQueryRepository,
                                    Validator validator,
                                    OpenTelemetry openTelemetry,
                                    RedisService redisService) {
        this.productCommandRepository = productCommandRepository;
        this.productQueryRepository = productQueryRepository;
        this.merchantQueryRepository = merchantQueryRepository;
        this.validator = validator;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.tracer = openTelemetry.getTracer("product-command-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("product-command-service");

        this.requestsTotal = meter.counterBuilder("requests_total")
                .setDescription("Total number of requests")
                .build();
        this.requestDurationSeconds = meter.histogramBuilder("request_duration_seconds")
                .setDescription("Request duration in seconds")
                .setUnit("s")
                .build();
    }

    private <T> void validateRequest(T req) {
        Set<ConstraintViolation<T>> violations = validator.validate(req);
        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (ConstraintViolation<T> violation : violations) {
                sb.append(violation.getPropertyPath()).append(": ").append(violation.getMessage()).append("; ");
            }
            logger.error("Validation failed: {}", sb);
            throw new ConstraintViolationException("Validation failed: " + sb, violations);
        }
    }

    private Uni<Void> invalidateCache(Long productId) {
        if (productId != null) {
            return redisService.deleteReactive("product:id:" + productId).replaceWithVoid();
        }
        return Uni.createFrom().voidItem();
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<ProductResponse>> createProduct(CreateProductRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("createProduct")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "product-service")
                .setAttribute("operation", "create_product")
                .setAttribute("product.name", req.getName())
                .startSpan();

        logger.info("🆕 Creating product: {}", req.getName());

        try {
            validateRequest(req);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            return Uni.createFrom().failure(e);
        }

        Product product = new Product();
        product.setMerchantId(req.getMerchantId());
        product.setCategoryId(req.getCategoryId());
        product.setName(req.getName());
        product.setDescription(req.getDescription());
        product.setPrice(req.getPrice());
        product.setCountInStock(req.getCountInStock());
        product.setBrand(req.getBrand());
        product.setWeight(req.getWeight());
        product.setRating(req.getRating().floatValue());
        product.setSlugProduct(req.getSlugProduct());
        product.setImageProduct(req.getImageProduct());

        return productCommandRepository.persist(product)
                .chain(saved -> {
                    ProductResponse response = ProductResponse.from(saved);
                    span.setAttribute("product.id", saved.id);

                    return invalidateCache(saved.id)
                            .map(v -> {
                                logger.info("Successfully created product with ID: {}", saved.id);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "create_product",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Product created successfully", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to create product: {}", req.getName(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_product",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_product"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<ProductResponse>> updateProduct(UpdateProductRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("updateProduct")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "product-service")
                .setAttribute("operation", "update_product")
                .setAttribute("product.id", req.getProductId().toString())
                .startSpan();

        logger.info("✏️ Updating product ID: {}", req.getProductId());

        try {
            validateRequest(req);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            return Uni.createFrom().failure(e);
        }

        return merchantQueryRepository.findMerchantById(req.getMerchantId().longValue())
                .chain(merchant -> {
                    if (merchant == null) {
                        throw new ResourceNotFoundException("Merchant not found with id " + req.getMerchantId());
                    }
                    return productQueryRepository.findProductById(req.getProductId().longValue());
                })
                .chain(optProduct -> {
                    if (optProduct.isEmpty()) {
                        throw new ResourceNotFoundException("Product not found");
                    }
                    Product product = optProduct.get();

                    if (req.getImageProduct() != null) {
                        product.setImageProduct(req.getImageProduct());
                    }

                    product.setMerchantId(req.getMerchantId());
                    product.setCategoryId(req.getCategoryId());
                    product.setName(req.getName());
                    product.setDescription(req.getDescription());
                    product.setPrice(req.getPrice());
                    product.setCountInStock(req.getCountInStock());
                    product.setBrand(req.getBrand());
                    product.setWeight(req.getWeight());
                    product.setRating(req.getRating().floatValue());
                    product.setSlugProduct(req.getSlugProduct());

                    return productCommandRepository.persist(product);
                })
                .chain(updated -> {
                    ProductResponse response = ProductResponse.from(updated);

                    return invalidateCache(updated.id)
                            .map(v -> {
                                logger.info("Successfully updated product with ID: {}", updated.id);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "update_product",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Product updated successfully", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to update product ID: {}", req.getProductId(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_product",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_product"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<ProductResponseDeleteAt>> trashedProduct(Integer productId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("trashProduct")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "product-service")
                .setAttribute("operation", "trash_product")
                .setAttribute("product.id", productId.toString())
                .startSpan();

        logger.info("🗑️ Trashing product ID: {}", productId);

        return productCommandRepository.trashed(productId.longValue())
                .chain(product -> {
                    if (product == null) {
                        throw new ResourceNotFoundException("Product not found or already trashed");
                    }
                    ProductResponseDeleteAt response = ProductResponseDeleteAt.from(product);

                    return invalidateCache(productId.longValue())
                            .map(v -> {
                                logger.info("Successfully trashed product with ID: {}", productId);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "trash_product",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Product trashed successfully", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to trash product ID: {}", productId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_product",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_product"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<ProductResponseDeleteAt>> restoreProduct(Integer productId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreProduct")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "product-service")
                .setAttribute("operation", "restore_product")
                .setAttribute("product.id", productId.toString())
                .startSpan();

        logger.info("♻️ Restoring product ID: {}", productId);

        return productCommandRepository.restore(productId.longValue())
                .chain(product -> {
                    if (product == null) {
                        throw new ResourceNotFoundException("Product not found or not trashed");
                    }
                    ProductResponseDeleteAt response = ProductResponseDeleteAt.from(product);

                    return invalidateCache(productId.longValue())
                            .map(v -> {
                                logger.info("Successfully restored product with ID: {}", productId);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "restore_product",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Product restored successfully", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to restore product ID: {}", productId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_product",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_product"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<ProductResponse>> updateProductCountStock(Integer productId, Integer stock) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("updateProductCountStock")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "product-service")
                .setAttribute("operation", "update_product_count_stock")
                .setAttribute("product.id", productId.toString())
                .startSpan();

        logger.info("✏️ Updating product stock ID: {}, stock: {}", productId, stock);

        return productQueryRepository.findProductById(productId.longValue())
                .chain(optProduct -> {
                    if (optProduct.isEmpty()) {
                        throw new ResourceNotFoundException("Product not found");
                    }
                    Product product = optProduct.get();
                    product.setCountInStock(stock);

                    return productCommandRepository.persist(product);
                })
                .chain(updated -> {
                    ProductResponse response = ProductResponse.from(updated);

                    return invalidateCache(updated.id)
                            .map(v -> {
                                logger.info("Successfully updated product stock with ID: {}", updated.id);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "update_product_count_stock",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Product stock updated successfully", response);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to update product stock ID: {}", productId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_product_count_stock",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_product_count_stock"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteProductPermanent(Integer productId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteProductPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "product-service")
                .setAttribute("operation", "delete_product_permanent")
                .setAttribute("product.id", productId.toString())
                .startSpan();

        logger.warn("🧨 Permanently deleting product ID: {}", productId);

        return productCommandRepository.deletePermanent(productId.longValue())
                .chain(deleted -> {
                    return invalidateCache(productId.longValue())
                            .map(v -> {
                                logger.info("Successfully permanently deleted product with ID: {}", productId);
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "delete_product_permanent",
                                        AttributeKey.stringKey("status"), "success"));

                                return ApiResponse.success("Product permanently deleted", deleted);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to permanently delete product ID: {}", productId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_product_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_product_permanent"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> restoreAllProducts() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreAllProducts")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "product-service")
                .setAttribute("operation", "restore_all_products")
                .startSpan();

        logger.info("🔄 Restoring ALL trashed products");

        return productCommandRepository.restoreAllDeleted()
                .map(restored -> {
                    logger.info("Successfully restored all trashed products");
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_products",
                            AttributeKey.stringKey("status"), "success"));

                    return ApiResponse.success("All products restored successfully", restored);
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to restore all products", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_products",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_products"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteAllProductsPermanent() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteAllProductsPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "product-service")
                .setAttribute("operation", "delete_all_products_permanent")
                .startSpan();

        logger.warn("💣 Permanently deleting ALL trashed products");

        return productCommandRepository.deleteAllDeleted()
                .map(deleted -> {
                    logger.info("Successfully permanently deleted all trashed products");
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_products_permanent",
                            AttributeKey.stringKey("status"), "success"));

                    return ApiResponse.success("All products permanently deleted", deleted);
                })
                .onFailure().invoke(e -> {
                    logger.error("Failed to delete all products", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_products_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_products_permanent"));
                });
    }
}
