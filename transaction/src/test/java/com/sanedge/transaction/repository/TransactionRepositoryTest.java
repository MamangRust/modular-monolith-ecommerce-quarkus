package com.sanedge.transaction.repository;

/**
 * Placeholder repository test.
 *
 * The {@link TransactionCommandRepository} and {@link TransactionQueryRepository}
 * rely on Hibernate Reactive / Panache which require a real Quarkus context
 * with a running PostgreSQL database. A pure-Mockito unit test is not meaningful
 * for the repository layer (the methods are thin delegations to
 * {@code PanacheRepository} APIs), so full coverage is provided by the service
 * tests under {@code com.sanedge.transaction.service} which mock these
 * repositories.
 *
 * For end-to-end coverage, run the integration tests in
 * {@code transaction/src/integrationTest/...} with a real database.
 */
class TransactionRepositoryTest {
    // Intentionally empty: see Javadoc above.
}
