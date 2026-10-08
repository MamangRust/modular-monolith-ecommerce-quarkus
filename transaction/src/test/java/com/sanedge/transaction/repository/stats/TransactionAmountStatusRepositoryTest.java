package com.sanedge.transaction.repository.stats;

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

@Disabled("Stats SQL uses H2-only functions; production rewrite to PostgreSQL is pending")
@QuarkusTest
@QuarkusTestResource(PostgreSqlResource.class)
@TestReactiveTransaction
class TransactionAmountStatusRepositoryTest {

    @Inject
    TransactionAmountStatusRepository transactionAmountStatusRepository;

    @Test
    void testFindMonthlyTransactionSuccess_ReturnsTwoPlaceholders(UniAsserter asserter) {
        asserter.execute(() -> transactionAmountStatusRepository
                .findMonthlyTransactionSuccess(2023, 12)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result.get(0).getTotalSuccess()).isZero();
                    assertThat(result.get(0).getTotalAmount()).isZero();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindYearlyTransactionSuccess_ReturnsTwoPlaceholders(UniAsserter asserter) {
        asserter.execute(() -> transactionAmountStatusRepository
                .findYearlyTransactionSuccess(2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result.get(0).getTotalSuccess()).isZero();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindMonthlyTransactionFailed_ReturnsTwoPlaceholders(UniAsserter asserter) {
        asserter.execute(() -> transactionAmountStatusRepository
                .findMonthlyTransactionFailed(2023, 12)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result.get(0).getTotalFailed()).isZero();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindYearlyTransactionFailed_ReturnsTwoPlaceholders(UniAsserter asserter) {
        asserter.execute(() -> transactionAmountStatusRepository
                .findYearlyTransactionFailed(2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result.get(0).getTotalFailed()).isZero();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindMonthlyTransactionSuccess_ReturnsNonNullResult(UniAsserter asserter) {
        asserter.execute(() -> transactionAmountStatusRepository
                .findMonthlyTransactionSuccess(2024, 6)
                .invoke(result -> assertThat(result).isNotNull())
                .replaceWithVoid());
    }
}