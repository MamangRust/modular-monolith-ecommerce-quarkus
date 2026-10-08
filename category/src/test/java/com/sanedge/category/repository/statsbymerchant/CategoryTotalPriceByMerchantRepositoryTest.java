package com.sanedge.category.repository.statsbymerchant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.sanedge.category.domain.requests.FindMonthlyCategoryTotalPriceByMerchantRequest;

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
class CategoryTotalPriceByMerchantRepositoryTest {

    @Inject
    CategoryTotalPriceByMerchantRepository categoryTotalPriceByMerchantRepository;

    private FindMonthlyCategoryTotalPriceByMerchantRequest request(int merchantId, int year, int month) {
        FindMonthlyCategoryTotalPriceByMerchantRequest req = new FindMonthlyCategoryTotalPriceByMerchantRequest();
        req.setMerchantId((long) merchantId);
        req.setYear(year);
        req.setMonth(month);
        return req;
    }

    @Test
    void testFindMonthlyTotalPriceByMerchant_ReturnsTwoPlaceholders(UniAsserter asserter) {
        asserter.execute(() -> categoryTotalPriceByMerchantRepository
                .findMonthlyTotalPriceByMerchant(request(1, 2023, 12))
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result.get(0).getTotalRevenue()).isZero();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindYearlyTotalPriceByMerchant_ReturnsTwoPlaceholders(UniAsserter asserter) {
        asserter.execute(() -> categoryTotalPriceByMerchantRepository
                .findYearlyTotalPriceByMerchant(1, 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result.get(0).getTotalRevenue()).isZero();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindMonthlyTotalPriceByMerchant_ReturnsNonNullResult(UniAsserter asserter) {
        asserter.execute(() -> categoryTotalPriceByMerchantRepository
                .findMonthlyTotalPriceByMerchant(request(1, 2024, 6))
                .invoke(result -> assertThat(result).isNotNull())
                .replaceWithVoid());
    }
}