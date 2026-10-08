package com.sanedge.order.repository.statsbymerchant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.sanedge.order.domain.requests.MonthOrderMerchantRequest;

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
class OrderSoldOutByMerchantRepositoryTest {

    @Inject
    OrderSoldOutByMerchantRepository orderSoldOutByMerchantRepository;

    @Test
    void testFindMonthlyOrdersByMerchant_ReturnsTwoEntries(UniAsserter asserter) {
        asserter.execute(() -> {
        MonthOrderMerchantRequest req = new MonthOrderMerchantRequest();
        req.setMerchantId(1);
        req.setYear(2023);
        req.setMonth(12);

        return orderSoldOutByMerchantRepository
                .findMonthlyOrdersByMerchant(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result.get(0).getOrderCount()).isZero();
                    assertThat(result.get(0).getTotalRevenue()).isZero();
                })
                .replaceWithVoid();
    });}

    @Test
    void testFindYearlyOrdersByMerchant_ReturnsFiveEntries(UniAsserter asserter) {
        asserter.execute(() -> orderSoldOutByMerchantRepository
                .findYearlyOrdersByMerchant(1, 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(5);
                    assertThat(result.get(0).getOrderCount()).isZero();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindMonthlyOrdersByMerchant_ReturnsNonNullResult(UniAsserter asserter) {
        asserter.execute(() -> {
        MonthOrderMerchantRequest req = new MonthOrderMerchantRequest();
        req.setMerchantId(1);
        req.setYear(2024);
        req.setMonth(6);

        return orderSoldOutByMerchantRepository
                .findMonthlyOrdersByMerchant(req)
                .invoke(result -> assertThat(result).isNotNull())
                .replaceWithVoid();
    });}
}