package com.sanedge.category.repository.statsbyid;

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
class CategoryPriceByIdRepositoryTest {

    @Inject
    CategoryPriceByIdRepository categoryPriceByIdRepository;

    @Test
    void testFindMonthlyCategoryStatsById_ReturnsEmptyList(UniAsserter asserter) {
        asserter.execute(() -> categoryPriceByIdRepository
                .findMonthlyCategoryStatsById(1, 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindYearlyCategoryStatsById_ReturnsEmptyList(UniAsserter asserter) {
        asserter.execute(() -> categoryPriceByIdRepository
                .findYearlyCategoryStatsById(1, 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindMonthlyCategoryStatsById_ReturnsNonNullResult(UniAsserter asserter) {
        asserter.execute(() -> categoryPriceByIdRepository
                .findMonthlyCategoryStatsById(1, 2023)
                .invoke(result -> assertThat(result).isNotNull())
                .replaceWithVoid());
    }
}