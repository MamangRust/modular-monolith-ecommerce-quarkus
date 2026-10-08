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
class TransactionMethodRepositoryTest {

    @Inject
    TransactionMethodRepository transactionMethodRepository;

    @Test
    void testFindMonthlyMethodsSuccess_ReturnsNonNullResult(UniAsserter asserter) {
        asserter.execute(() -> transactionMethodRepository.findMonthlyMethodsSuccess(2024, 6)
                .invoke(result -> assertThat(result).isNotNull())
                .replaceWithVoid());
    }

    @Test
    void testFindMonthlyMethodsFailed_ReturnsNonNullResult(UniAsserter asserter) {
        asserter.execute(() -> transactionMethodRepository.findMonthlyMethodsFailed(2024, 6)
                .invoke(result -> assertThat(result).isNotNull())
                .replaceWithVoid());
    }

    @Test
    void testFindYearlyMethodsSuccess_ReturnsNonNullResult(UniAsserter asserter) {
        asserter.execute(() -> transactionMethodRepository.findYearlyMethodsSuccess(2024)
                .invoke(result -> assertThat(result).isNotNull())
                .replaceWithVoid());
    }

    @Test
    void testFindYearlyMethodsFailed_ReturnsNonNullResult(UniAsserter asserter) {
        asserter.execute(() -> transactionMethodRepository.findYearlyMethodsFailed(2024)
                .invoke(result -> assertThat(result).isNotNull())
                .replaceWithVoid());
    }

    @Test
    void testFindMonthlyMethodsSuccess_PastYearReturnsEmpty(UniAsserter asserter) {
        asserter.execute(() -> transactionMethodRepository.findMonthlyMethodsSuccess(1990, 6)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid());
    }
}