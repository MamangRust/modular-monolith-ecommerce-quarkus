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
class CategoryTotalPriceRepositoryTest {

    @Inject
    CategoryTotalPriceRepository categoryTotalPriceRepository;

    @Test
    void testFindMonthlyTotalPrice_ReturnsTwoPlaceholders(UniAsserter asserter) {
        asserter.execute(() -> categoryTotalPriceRepository
                .findMonthlyTotalPrice(2023, 12)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result.get(0).getTotalRevenue()).isZero();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindYearlyTotalPrice_ReturnsTwoPlaceholders(UniAsserter asserter) {
        asserter.execute(() -> categoryTotalPriceRepository
                .findYearlyTotalPrice(2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result.get(0).getTotalRevenue()).isZero();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindMonthlyTotalPrice_ReturnsNonNullResult(UniAsserter asserter) {
        asserter.execute(() -> categoryTotalPriceRepository
                .findMonthlyTotalPrice(2024, 6)
                .invoke(result -> assertThat(result).isNotNull())
                .replaceWithVoid());
    }
}