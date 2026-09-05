package com.sanedge.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.sanedge.user.entity.User;
import com.sanedge.user.domain.requests.FindAllUsers;
import com.sanedge.common.domain.response.PagedResult;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestReactiveTransaction;

@QuarkusTest
@TestReactiveTransaction
class UserRepositoryTest {

    @Inject
    UserRepository userRepository;

    // --- Helper Method untuk membuat user secara instan ---
    private User createAndPersistUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setFirstname("First_" + username);
        user.setLastname("Last_" + username);
        user.setEmail(email);
        user.setPassword("password123");
        return userRepository.persist(user).await().indefinitely();
    }

    // ==========================================
    // 1. BASIC CRUD & FINDER TESTS
    // ==========================================

    @Test
    void testCreateAndFindById() {
        User saved = createAndPersistUser("johndoe", "john.doe@example.com");

        assertThat(saved).isNotNull();
        assertThat(saved.id).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("john.doe@example.com");

        User found = userRepository.findById(Math.toIntExact(saved.id))
                .await().indefinitely();

        assertThat(found).isNotNull();
        assertThat(found.getUsername()).isEqualTo("johndoe");
        assertThat(found.getFirstname()).isEqualTo("First_johndoe");
    }

    @Test
    void testFindByEmail() {
        createAndPersistUser("janedoe", "jane.doe@example.com");

        User found = userRepository.findByEmail("jane.doe@example.com")
                .await().indefinitely();

        assertThat(found).isNotNull();
        assertThat(found.getUsername()).isEqualTo("janedoe");
    }

    @Test
    void testFindByEmailReturnsNullWhenNotFound() {
        User notFound = userRepository.findByEmail("nonexistent@example.com")
                .await().indefinitely();

        assertThat(notFound).isNull();
    }

    @Test
    void testFindByIdReturnsNullWhenNotFound() {
        User notFound = userRepository.findById(Integer.MAX_VALUE)
                .await().indefinitely();

        assertThat(notFound).isNull();
    }

    @Test
    void testFindByUsername() {
        createAndPersistUser("alice", "alice@example.com");

        User found = userRepository.findByUsername("alice")
                .await().indefinitely();

        assertThat(found).isNotNull();
        assertThat(found.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void testExistsByUsernameAndEmail() {
        createAndPersistUser("bob", "bob@example.com");

        assertThat(userRepository.existsByUsername("bob").await().indefinitely()).isTrue();
        assertThat(userRepository.existsByEmail("bob@example.com").await().indefinitely()).isTrue();

        assertThat(userRepository.existsByUsername("notbob").await().indefinitely()).isFalse();
        assertThat(userRepository.existsByEmail("notbob@example.com").await().indefinitely()).isFalse();
    }

    // ==========================================
    // 2. SOFT DELETE (TRASH) TESTS
    // ==========================================

    @Test
    void testTrashUser() {
        User saved = createAndPersistUser("trashme", "trash@example.com");
        assertThat(saved.getDeletedAt()).isNull();

        User trashed = userRepository.trash(saved.id).await().indefinitely();

        assertThat(trashed).isNotNull();
        assertThat(trashed.getDeletedAt()).isNotNull();
    }

    @Test
    void testTrashUserReturnsNullIfAlreadyTrashed() {
        User saved = createAndPersistUser("trashme2", "trash2@example.com");
        userRepository.trash(saved.id).await().indefinitely(); // Trash pertama

        // Trash kedua harusnya return null
        User trashedAgain = userRepository.trash(saved.id).await().indefinitely();
        assertThat(trashedAgain).isNull();
    }

    @Test
    void testTrashUserReturnsNullIfNotFound() {
        User trashed = userRepository.trash(99999L).await().indefinitely();
        assertThat(trashed).isNull();
    }

    // ==========================================
    // 3. RESTORE TESTS
    // ==========================================

    @Test
    void testRestoreUser() {
        User saved = createAndPersistUser("restoreme", "restore@example.com");
        userRepository.trash(saved.id).await().indefinitely();

        User restored = userRepository.restore(saved.id).await().indefinitely();

        assertThat(restored).isNotNull();
        assertThat(restored.getDeletedAt()).isNull();
    }

    @Test
    void testRestoreUserReturnsNullIfNotTrashed() {
        User saved = createAndPersistUser("restoreme2", "restore2@example.com");
        // Tidak di-trash langsung restore

        User restored = userRepository.restore(saved.id).await().indefinitely();
        assertThat(restored).isNull();
    }

    // ==========================================
    // 4. PERMANENT DELETE TESTS
    // ==========================================

    @Test
    void testDeletePermanent() {
        User saved = createAndPersistUser("deleteme", "delete@example.com");
        userRepository.trash(saved.id).await().indefinitely();

        User deleted = userRepository.deletePermanent(saved.id).await().indefinitely();
        assertThat(deleted).isNotNull();

        // Pastikan benar-benar hilang dari database
        User checkDb = userRepository.findById(Math.toIntExact(saved.id)).await().indefinitely();
        assertThat(checkDb).isNull();
    }

    @Test
    void testDeletePermanentFailsIfNotTrashed() {
        User saved = createAndPersistUser("deleteme2", "delete2@example.com");
        // Tidak di-trash, langsung permanent delete

        User deleted = userRepository.deletePermanent(saved.id).await().indefinitely();
        assertThat(deleted).isNull();

        // User masih harus ada di database
        User checkDb = userRepository.findById(Math.toIntExact(saved.id)).await().indefinitely();
        assertThat(checkDb).isNotNull();
    }

    // ==========================================
    // 5. BULK ACTIONS (RESTORE ALL & DELETE ALL)
    // ==========================================

    @Test
    void testRestoreAllDeleted() {
        User u1 = createAndPersistUser("bulk1", "bulk1@example.com");
        User u2 = createAndPersistUser("bulk2", "bulk2@example.com");

        userRepository.trash(u1.id).await().indefinitely();
        userRepository.trash(u2.id).await().indefinitely();
        // u3 tidak di-trash

        Boolean result = userRepository.restoreAllDeleted().await().indefinitely();
        assertThat(result).isTrue();

        assertThat(userRepository.findById(Math.toIntExact(u1.id)).await().indefinitely().getDeletedAt()).isNull();
        assertThat(userRepository.findById(Math.toIntExact(u2.id)).await().indefinitely().getDeletedAt()).isNull();
    }

    @Test
    void testDeleteAllDeleted() {
        User u1 = createAndPersistUser("bulkdel1", "bulkdel1@example.com");
        User u2 = createAndPersistUser("bulkdel2", "bulkdel2@example.com");
        User u3 = createAndPersistUser("bulkdel3", "bulkdel3@example.com");

        userRepository.trash(u1.id).await().indefinitely();
        userRepository.trash(u2.id).await().indefinitely();
        // u3 tidak di-trash

        Boolean result = userRepository.deleteAllDeleted().await().indefinitely();
        assertThat(result).isTrue();

        assertThat(userRepository.findById(Math.toIntExact(u1.id)).await().indefinitely()).isNull();
        assertThat(userRepository.findById(Math.toIntExact(u2.id)).await().indefinitely()).isNull();
        assertThat(userRepository.findById(Math.toIntExact(u3.id)).await().indefinitely()).isNotNull(); // Masih aman
    }

    // ==========================================
    // 6. PAGED & FILTERED QUERIES TESTS
    // ==========================================

    @Test
    void testFindActiveUsers() {
        createAndPersistUser("active1", "active1@example.com");
        User trashed = createAndPersistUser("trashed1", "trashed1@example.com");
        userRepository.trash(trashed.id).await().indefinitely();

        FindAllUsers req = new FindAllUsers();
        req.setPage(1);
        req.setPageSize(10);

        PagedResult<User> result = userRepository.findActiveUsers(req).await().indefinitely();

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getUsername()).isEqualTo("active1");
        assertThat(result.getTotalRecords()).isEqualTo(1);
    }

    @Test
    void testFindTrashedUsers() {
        User trashed = createAndPersistUser("trashed2", "trashed2@example.com");
        createAndPersistUser("active2", "active2@example.com");
        userRepository.trash(trashed.id).await().indefinitely();

        FindAllUsers req = new FindAllUsers();
        req.setPage(1);
        req.setPageSize(10);

        PagedResult<User> result = userRepository.findTrashedUsers(req).await().indefinitely();

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getUsername()).isEqualTo("trashed2");
    }

    @Test
    void testFindUsersWithSearchKeyword() {
        createAndPersistUser("superman", "superman@dc.com");
        createAndPersistUser("spiderman", "spiderman@marvel.com");
        createAndPersistUser("batman", "batman@dc.com");

        FindAllUsers req = new FindAllUsers();
        req.setPage(1);
        req.setPageSize(10);
        req.setSearch("man"); // Harusnya menemukan superman dan spiderman

        PagedResult<User> result = userRepository.findUsers(req).await().indefinitely();

        assertThat(result.getData()).hasSize(2);
        assertThat(result.getTotalRecords()).isEqualTo(2);
    }

    @Test
    void testFindUsersWithPagination() {
        // Membuat 5 user
        for (int i = 1; i <= 5; i++) {
            createAndPersistUser("pageuser" + i, "pageuser" + i + "@example.com");
        }

        FindAllUsers reqPage1 = new FindAllUsers();
        reqPage1.setPage(1);
        reqPage1.setPageSize(2);

        PagedResult<User> page1 = userRepository.findUsers(reqPage1).await().indefinitely();

        assertThat(page1.getData()).hasSize(2);
        assertThat(page1.getTotalRecords()).isEqualTo(5);

        FindAllUsers reqPage2 = new FindAllUsers();
        reqPage2.setPage(2);
        reqPage2.setPageSize(2);

        PagedResult<User> page2 = userRepository.findUsers(reqPage2).await().indefinitely();

        assertThat(page2.getData()).hasSize(2);
        // Memastikan ini halaman berikutnya (bukan data yang sama dengan halaman 1)
        assertThat(page2.getData().get(0).getUsername()).isNotIn(page1.getData().get(0).getUsername(),
                page1.getData().get(1).getUsername());
    }
}