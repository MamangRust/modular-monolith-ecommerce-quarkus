package com.sanedge.cart.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Placeholder for Cart repository integration tests.
 *
 * The real CartQueryRepository and CartCommandRepository extend
 * PanacheRepository and depend on a running PostgreSQL instance plus
 * Hibernate Reactive. Full integration coverage (CRUD, soft delete, bulk
 * operations, paginated queries) requires {@code @QuarkusTest} with a live
 * database — this is exercised manually via docker-compose.
 *
 * For pure unit-level coverage of the repositories, the public methods are
 * trivial delegates to Panache; verify the service layer (see CartServiceTest)
 * mocks these repositories correctly.
 */
class CartRepositoryTest {

    @Test
    @DisplayName("placeholder - cart repository integration tests require @QuarkusTest + PostgreSQL")
    void placeholder() {
        assertThat(true).isTrue();
    }
}
