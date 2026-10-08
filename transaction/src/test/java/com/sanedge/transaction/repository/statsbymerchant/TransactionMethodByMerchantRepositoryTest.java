package com.sanedge.transaction.repository.statsbymerchant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.sanedge.transaction.domain.requests.FindMonthlyMethodsByMerchantRequest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.vertx.UniAsserter;
import com.sanedge.common.test.PostgreSqlResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@Disabled("Stats SQL uses H2-only functions; production rewrite to PostgreSQL is pending")
@QuarkusTest
@QuarkusTestResource(PostgreSqlResource.class)
@TestReactiveTransaction
class TransactionMethodByMerchantRepositoryTest {

    @Inject
    TransactionMethodByMerchantRepository transactionMethodByMerchantRepository;

    private FindMonthlyMethodsByMerchantRequest monthlyReq(Long merchantId, Integer year, Integer month) {
        FindMonthlyMethodsByMerchantRequest req = new FindMonthlyMethodsByMerchantRequest();
        req.setMerchantId(merchantId);
        req.setYear(year);
        req.setMonth(month);
        return req;
    }

    @Test
    void testFindMonthlyMethodsSuccessByMerchant_ReturnsNonNullResult(UniAsserter asserter) {
        asserter.execute(() -> transactionMethodByMerchantRepository
                .findMonthlyTransactionMethodsSuccessByMerchant(monthlyReq(1L, 2024, 6))
                .invoke(result -> assertThat(result).isNotNull())
                .replaceWithVoid());
    }

    @Test
    void testFindMonthlyMethodsFailedByMerchant_ReturnsNonNullResult(UniAsserter asserter) {
        asserter.execute(() -> transactionMethodByMerchantRepository
                .findMonthlyTransactionMethodsFailedByMerchant(monthlyReq(1L, 2024, 6))
                .invoke(result -> assertThat(result).isNotNull())
                .replaceWithVoid());
    }

    @Test
    void testFindYearlyMethodsSuccessByMerchant_ReturnsNonNullResult(UniAsserter asserter) {
        asserter.execute(() -> transactionMethodByMerchantRepository
                .findYearlyTransactionMethodsSuccessByMerchant(1L, 2024)
                .invoke(result -> assertThat(result).isNotNull())
                .replaceWithVoid());
    }

    @Test
    void testFindYearlyMethodsFailedByMerchant_ReturnsNonNullResult(UniAsserter asserter) {
        asserter.execute(() -> transactionMethodByMerchantRepository
                .findYearlyTransactionMethodsFailedByMerchant(1L, 2024)
                .invoke(result -> assertThat(result).isNotNull())
                .replaceWithVoid());
    }

    @Test
    void testFindMonthlyMethodsSuccessByMerchant_PastYearReturnsEmpty(UniAsserter asserter) {
        asserter.execute(() -> transactionMethodByMerchantRepository
                .findMonthlyTransactionMethodsSuccessByMerchant(monthlyReq(99999L, 1990, 6))
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid());
    }
}