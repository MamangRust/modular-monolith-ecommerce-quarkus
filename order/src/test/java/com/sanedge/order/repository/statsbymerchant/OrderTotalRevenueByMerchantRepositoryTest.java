package com.sanedge.order.repository.statsbymerchant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.sanedge.order.domain.requests.MonthTotalRevenueMerchantRequest;

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
class OrderTotalRevenueByMerchantRepositoryTest {

    @Inject
    OrderTotalRevenueByMerchantRepository orderTotalRevenueByMerchantRepository;

    @Test
    void testFindMonthlyTotalRevenueByMerchant_ReturnsTwoEntries(UniAsserter asserter) {
        asserter.execute(() -> {
        MonthTotalRevenueMerchantRequest req = new MonthTotalRevenueMerchantRequest();
        req.setMerchantId(1);
        req.setYear(2023);
        req.setMonth(12);

        return orderTotalRevenueByMerchantRepository
                .findMonthlyTotalRevenueByMerchant(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result.get(0).getTotalRevenue()).isZero();
                })
                .replaceWithVoid();
    });}

    @Test
    void testFindYearlyTotalRevenueByMerchant_ReturnsTwoEntries(UniAsserter asserter) {
        asserter.execute(() -> orderTotalRevenueByMerchantRepository
                .findYearlyTotalRevenueByMerchant(1L, 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result.get(0).getTotalRevenue()).isZero();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindMonthlyTotalRevenueByMerchant_ReturnsNonNullResult(UniAsserter asserter) {
        asserter.execute(() -> {
        MonthTotalRevenueMerchantRequest req = new MonthTotalRevenueMerchantRequest();
        req.setMerchantId(1);
        req.setYear(2024);
        req.setMonth(6);

        return orderTotalRevenueByMerchantRepository
                .findMonthlyTotalRevenueByMerchant(req)
                .invoke(result -> assertThat(result).isNotNull())
                .replaceWithVoid();
    });}
}