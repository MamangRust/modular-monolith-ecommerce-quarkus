package com.sanedge.common.repository;

import com.sanedge.common.domain.response.PagedResult;

import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.smallrye.mutiny.Uni;

/**
 * Shared helper for building a {@link PagedResult} from a Panache query.
 *
 * <p>
 * A Hibernate Reactive session is not thread-safe, so the page query and the
 * count query must be executed sequentially. Combining them with
 * {@code Uni.combine().all().unis(query.list(), query.count())} subscribes to
 * both in parallel on the same session, which corrupts the action queue.
 */
public final class PagedQuery {

    private PagedQuery() {
    }

    public static <T> Uni<PagedResult<T>> fetch(PanacheQuery<T> query) {
        return query.list()
                .chain(list -> query.count()
                        .map(count -> new PagedResult<>(list, count.intValue())));
    }
}
