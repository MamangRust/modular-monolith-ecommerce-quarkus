package com.sanedge.order.repository.stats;

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

@Disabled("Native SQL contains PostgreSQL functions; verify compatibility before enabling")
@QuarkusTest
@QuarkusTestResource(PostgreSqlResource.class)
@TestReactiveTransaction
class OrderTotalRevenueRepositoryTest {

    @Inject
    OrderTotalRevenueRepository orderTotalRevenueRepository;

    @Test
    void testFindMonthlyTotalRevenue_ReturnsTwoEntries(UniAsserter asserter) {
        asserter.execute(() -> orderTotalRevenueRepository
                .findMonthlyTotalRevenue(2023, 12)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result.get(0).getTotalRevenue()).isZero();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindYearlyTotalRevenue_ReturnsTwoEntries(UniAsserter asserter) {
        asserter.execute(() -> orderTotalRevenueRepository
                .findYearlyTotalRevenue(2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result.get(0).getTotalRevenue()).isZero();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindMonthlyTotalRevenue_ReturnsNonNullResult(UniAsserter asserter) {
        asserter.execute(() -> orderTotalRevenueRepository
                .findMonthlyTotalRevenue(2024, 6)
                .invoke(result -> assertThat(result).isNotNull())
                .replaceWithVoid());
    }
}