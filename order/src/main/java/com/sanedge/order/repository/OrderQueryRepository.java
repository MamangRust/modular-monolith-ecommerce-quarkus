package com.sanedge.order.repository;

import java.util.Optional;

import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.order.entity.Order;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrderQueryRepository implements PanacheRepository<Order> {

    public Uni<PagedResult<Order>> findOrders(String keyword, int page, int size) {
        var query = """
                    (
                        ?1 IS NULL
                        OR CAST(id AS string) LIKE CONCAT('%', ?1, '%')
                        OR CAST(totalPrice AS string) LIKE CONCAT('%', ?1, '%')
                    )
                """;
        var panacheQuery = find(query, keyword).page(page, size);
        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    public Uni<PagedResult<Order>> findActiveOrders(String keyword, int page, int size) {
        var query = """
                    deletedAt IS NULL
                    AND (
                        ?1 IS NULL
                        OR CAST(id AS string) LIKE CONCAT('%', ?1, '%')
                        OR CAST(totalPrice AS string) LIKE CONCAT('%', ?1, '%')
                    )
                """;
        var panacheQuery = find(query, keyword).page(page, size);
        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    public Uni<PagedResult<Order>> findTrashedOrders(String keyword, int page, int size) {
        var query = """
                    deletedAt IS NOT NULL
                    AND (
                        ?1 IS NULL
                        OR CAST(id AS string) LIKE CONCAT('%', ?1, '%')
                        OR CAST(totalPrice AS string) LIKE CONCAT('%', ?1, '%')
                    )
                """;
        var panacheQuery = find(query, keyword).page(page, size);
        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    public Uni<PagedResult<Order>> findOrdersByMerchant(String keyword, Long merchantId, int page, int size) {
        var query = """
                    deletedAt IS NULL
                    AND (
                        ?1 IS NULL
                        OR CAST(id AS string) LIKE CONCAT('%', ?1, '%')
                        OR CAST(totalPrice AS string) LIKE CONCAT('%', ?1, '%')
                    )
                    AND (?2 IS NULL OR merchantId = ?2)
                """;
        var panacheQuery = find(query, keyword, merchantId).page(page, size);
        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    public Uni<Optional<Order>> findOrderById(Long orderId) {
        return find("id = ?1 AND deletedAt IS NULL", orderId).firstResult().map(Optional::ofNullable);
    }
}
