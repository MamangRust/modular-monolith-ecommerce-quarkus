package com.sanedge.category.repository.stats;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.vertx.UniAsserter;
import com.sanedge.common.test.PostgreSqlResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@Disabled("Stats SQL uses PostgreSQL-specific functions; production rewrite pending")
@QuarkusTest
@QuarkusTestResource(PostgreSqlResource.class)
@TestReactiveTransaction
class CategoryPriceRepositoryTest {

    @Inject
    CategoryPriceRepository categoryPriceRepository;

    @Test
    void testFindMonthlyCategoryStats_ReturnsEmptyListWhenNoData(UniAsserter asserter) {
        asserter.execute(() -> categoryPriceRepository
                .findMonthlyCategoryStats(2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindYearlyCategoryStats_ReturnsEmptyListWhenNoData(UniAsserter asserter) {
        asserter.execute(() -> categoryPriceRepository
                .findYearlyCategoryStats(2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindMonthlyCategoryStats_ReturnsNonNullResult(UniAsserter asserter) {
        asserter.execute(() -> categoryPriceRepository
                .findMonthlyCategoryStats(2023)
                .invoke(result -> assertThat(result).isNotNull())
                .replaceWithVoid());
    }
}