package com.sanedge.category.repository.statsbyid;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.sanedge.category.domain.requests.FindMonthlyCategoryTotalPriceByIdRequest;

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
class CategoryTotalPriceByIdRepositoryTest {

    @Inject
    CategoryTotalPriceByIdRepository categoryTotalPriceByIdRepository;

    private FindMonthlyCategoryTotalPriceByIdRequest request(int categoryId, int year, int month) {
        FindMonthlyCategoryTotalPriceByIdRequest req = new FindMonthlyCategoryTotalPriceByIdRequest();
        req.setCategoryId((long) categoryId);
        req.setYear(year);
        req.setMonth(month);
        return req;
    }

    @Test
    void testFindMonthlyTotalPriceByCategory_ReturnsTwoPlaceholders(UniAsserter asserter) {
        asserter.execute(() -> categoryTotalPriceByIdRepository
                .findMonthlyTotalPriceByCategory(request(1, 2023, 12))
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result.get(0).getTotalRevenue()).isZero();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindYearlyTotalPriceByCategory_ReturnsTwoPlaceholders(UniAsserter asserter) {
        asserter.execute(() -> categoryTotalPriceByIdRepository
                .findYearlyTotalPriceByCategory(1, 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result.get(0).getTotalRevenue()).isZero();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindMonthlyTotalPriceByCategory_ReturnsNonNullResult(UniAsserter asserter) {
        asserter.execute(() -> categoryTotalPriceByIdRepository
                .findMonthlyTotalPriceByCategory(request(1, 2024, 6))
                .invoke(result -> assertThat(result).isNotNull())
                .replaceWithVoid());
    }
}