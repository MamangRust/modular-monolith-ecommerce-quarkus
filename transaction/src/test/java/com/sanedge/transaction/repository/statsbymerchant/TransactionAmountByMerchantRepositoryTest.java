package com.sanedge.transaction.repository.statsbymerchant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.sanedge.transaction.domain.requests.FindMonthlyAmountByMerchantRequest;

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
class TransactionAmountByMerchantRepositoryTest {

    @Inject
    TransactionAmountByMerchantRepository transactionAmountByMerchantRepository;

    private FindMonthlyAmountByMerchantRequest monthlyReq(Long merchantId, Integer year, Integer month) {
        FindMonthlyAmountByMerchantRequest req = new FindMonthlyAmountByMerchantRequest();
        req.setMerchantId(merchantId);
        req.setYear(year);
        req.setMonth(month);
        return req;
    }

    @Test
    void testFindMonthlySuccessByMerchant_ReturnsTwoPlaceholders(UniAsserter asserter) {
        asserter.execute(() -> transactionAmountByMerchantRepository
                .findMonthlySuccessByMerchant(monthlyReq(1L, 2023, 12))
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result.get(0).getTotalSuccess()).isZero();
                    assertThat(result.get(0).getTotalAmount()).isZero();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindYearlySuccessByMerchant_ReturnsTwoPlaceholders(UniAsserter asserter) {
        asserter.execute(() -> transactionAmountByMerchantRepository
                .findYearlySuccessByMerchant(1L, 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result.get(0).getTotalSuccess()).isZero();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindMonthlyFailedByMerchant_ReturnsTwoPlaceholders(UniAsserter asserter) {
        asserter.execute(() -> transactionAmountByMerchantRepository
                .findMonthlyFailedByMerchant(monthlyReq(1L, 2023, 12))
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result.get(0).getTotalFailed()).isZero();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindYearlyFailedByMerchant_ReturnsTwoPlaceholders(UniAsserter asserter) {
        asserter.execute(() -> transactionAmountByMerchantRepository
                .findYearlyFailedByMerchant(1L, 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result.get(0).getTotalFailed()).isZero();
                })
                .replaceWithVoid());
    }
}