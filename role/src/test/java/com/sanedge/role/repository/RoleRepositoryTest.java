package com.sanedge.role.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.sanedge.role.entity.Role;
import com.sanedge.role.domain.requests.FindAllRoles;
import com.sanedge.common.domain.response.PagedResult;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestReactiveTransaction;

@QuarkusTest
@TestReactiveTransaction
class RoleRepositoryTest {

    @Inject
    RoleRepository roleRepository;

    // --- Helper Method untuk membuat role secara instan ---
    private Role createAndPersistRole(String roleName) {
        Role role = new Role();
        role.setRoleName(roleName);
        return roleRepository.persist(role).await().indefinitely();
    }

    // ==========================================
    // 1. BASIC CRUD & FINDER TESTS
    // ==========================================

    @Test
    void testCreateAndFindById() {
        Role saved = createAndPersistRole("Admin");

        assertThat(saved).isNotNull();
        assertThat(saved.id).isNotNull();
        assertThat(saved.getRoleName()).isEqualTo("Admin");

        Role found = roleRepository.findById(saved.id)
                .await().indefinitely();

        assertThat(found).isNotNull();
        assertThat(found.getRoleName()).isEqualTo("Admin");
    }

    @Test
    void testFindByRoleName() {
        createAndPersistRole("Editor");

        Role found = roleRepository.findByRoleName("Editor")
                .await().indefinitely();

        assertThat(found).isNotNull();
        assertThat(found.getRoleName()).isEqualTo("Editor");
    }

    @Test
    void testFindByRoleNameReturnsNullWhenNotFound() {
        Role notFound = roleRepository.findByRoleName("NonExistentRole")
                .await().indefinitely();

        assertThat(notFound).isNull();
    }

    @Test
    void testFindByIdReturnsNullWhenNotFound() {
        Role notFound = roleRepository.findById(99999L)
                .await().indefinitely();

        assertThat(notFound).isNull();
    }

    // ==========================================
    // 2. SOFT DELETE (TRASH) TESTS
    // ==========================================

    @Test
    void testTrashRole() {
        Role saved = createAndPersistRole("TrashMe");
        assertThat(saved.getDeletedAt()).isNull();

        Role trashed = roleRepository.trash(saved.id).await().indefinitely();

        assertThat(trashed).isNotNull();
        assertThat(trashed.getDeletedAt()).isNotNull();
    }

    @Test
    void testTrashRoleReturnsNullIfAlreadyTrashed() {
        Role saved = createAndPersistRole("TrashMe2");
        roleRepository.trash(saved.id).await().indefinitely(); // Trash pertama

        // Trash kedua harusnya return null
        Role trashedAgain = roleRepository.trash(saved.id).await().indefinitely();
        assertThat(trashedAgain).isNull();
    }

    @Test
    void testTrashRoleReturnsNullIfNotFound() {
        Role trashed = roleRepository.trash(99999L).await().indefinitely();
        assertThat(trashed).isNull();
    }

    // ==========================================
    // 3. RESTORE TESTS
    // ==========================================

    @Test
    void testRestoreRole() {
        Role saved = createAndPersistRole("RestoreMe");
        roleRepository.trash(saved.id).await().indefinitely();

        Role restored = roleRepository.restore(saved.id).await().indefinitely();

        assertThat(restored).isNotNull();
        assertThat(restored.getDeletedAt()).isNull();
    }

    @Test
    void testRestoreRoleReturnsNullIfNotTrashed() {
        Role saved = createAndPersistRole("RestoreMe2");
        // Tidak di-trash langsung restore

        Role restored = roleRepository.restore(saved.id).await().indefinitely();
        assertThat(restored).isNull();
    }

    // ==========================================
    // 4. PERMANENT DELETE TESTS
    // ==========================================

    @Test
    void testDeletePermanent() {
        Role saved = createAndPersistRole("DeleteMe");
        roleRepository.trash(saved.id).await().indefinitely();

        Role deleted = roleRepository.deletePermanent(saved.id).await().indefinitely();
        assertThat(deleted).isNotNull();

        // Pastikan benar-benar hilang dari database
        Role checkDb = roleRepository.findById(saved.id).await().indefinitely();
        assertThat(checkDb).isNull();
    }

    @Test
    void testDeletePermanentFailsIfNotTrashed() {
        Role saved = createAndPersistRole("DeleteMe2");
        // Tidak di-trash, langsung permanent delete

        Role deleted = roleRepository.deletePermanent(saved.id).await().indefinitely();
        assertThat(deleted).isNull();

        // Role masih harus ada di database
        Role checkDb = roleRepository.findById(saved.id).await().indefinitely();
        assertThat(checkDb).isNotNull();
    }

    // ==========================================
    // 5. BULK ACTIONS (RESTORE ALL & DELETE ALL)
    // ==========================================

    @Test
    void testRestoreAllDeleted() {
        Role r1 = createAndPersistRole("BulkRole1");
        Role r2 = createAndPersistRole("BulkRole2");
        Role r3 = createAndPersistRole("BulkRole3");

        roleRepository.trash(r1.id).await().indefinitely();
        roleRepository.trash(r2.id).await().indefinitely();
        // r3 tidak di-trash

        Boolean result = roleRepository.restoreAllDeleted().await().indefinitely();
        assertThat(result).isTrue();

        assertThat(roleRepository.findById(r1.id).await().indefinitely().getDeletedAt()).isNull();
        assertThat(roleRepository.findById(r2.id).await().indefinitely().getDeletedAt()).isNull();
        assertThat(roleRepository.findById(r3.id).await().indefinitely().getDeletedAt()).isNull(); // Tetap null
    }

    @Test
    void testDeleteAllDeleted() {
        Role r1 = createAndPersistRole("BulkDelRole1");
        Role r2 = createAndPersistRole("BulkDelRole2");
        Role r3 = createAndPersistRole("BulkDelRole3");

        roleRepository.trash(r1.id).await().indefinitely();
        roleRepository.trash(r2.id).await().indefinitely();
        // r3 tidak di-trash

        Boolean result = roleRepository.deleteAllDeleted().await().indefinitely();
        assertThat(result).isTrue();

        assertThat(roleRepository.findById(r1.id).await().indefinitely()).isNull();
        assertThat(roleRepository.findById(r2.id).await().indefinitely()).isNull();
        assertThat(roleRepository.findById(r3.id).await().indefinitely()).isNotNull(); // Masih aman
    }

    // ==========================================
    // 6. PAGED & FILTERED QUERIES TESTS
    // ==========================================

    @Test
    void testFindActiveRoles() {
        createAndPersistRole("ActiveRole1");
        Role trashed = createAndPersistRole("TrashedRole1");
        roleRepository.trash(trashed.id).await().indefinitely();

        FindAllRoles req = new FindAllRoles();
        req.setPage(1);
        req.setPageSize(10);

        PagedResult<Role> result = roleRepository.findActiveRoles(req).await().indefinitely();

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getRoleName()).isEqualTo("ActiveRole1");
        assertThat(result.getTotalRecords()).isEqualTo(1);
    }

    @Test
    void testFindTrashedRoles() {
        Role trashed = createAndPersistRole("TrashedRole2");
        createAndPersistRole("ActiveRole2");
        roleRepository.trash(trashed.id).await().indefinitely();

        FindAllRoles req = new FindAllRoles();
        req.setPage(1);
        req.setPageSize(10);

        PagedResult<Role> result = roleRepository.findTrashedRoles(req).await().indefinitely();

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getRoleName()).isEqualTo("TrashedRole2");
    }

    @Test
    void testFindRolesWithSearchKeyword() {
        createAndPersistRole("SuperAdmin");
        createAndPersistRole("SuperEditor");
        createAndPersistRole("Viewer");

        FindAllRoles req = new FindAllRoles();
        req.setPage(1);
        req.setPageSize(10);
        req.setSearch("Super"); // Harusnya menemukan SuperAdmin dan SuperEditor

        PagedResult<Role> result = roleRepository.findRoles(req).await().indefinitely();

        assertThat(result.getData()).hasSize(2);
        assertThat(result.getTotalRecords()).isEqualTo(2);
    }

    @Test
    void testFindActiveRolesWithSearchKeyword() {
        createAndPersistRole("AdminPanel");
        Role trashedAdmin = createAndPersistRole("AdminTrashed");
        roleRepository.trash(trashedAdmin.id).await().indefinitely();
        createAndPersistRole("ViewerPanel");

        FindAllRoles req = new FindAllRoles();
        req.setPage(1);
        req.setPageSize(10);
        req.setSearch("Admin"); // Hanya AdminPanel yang aktif, AdminTrashed sudah di-trash

        PagedResult<Role> result = roleRepository.findActiveRoles(req).await().indefinitely();

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getRoleName()).isEqualTo("AdminPanel");
    }

    @Test
    void testFindTrashedRolesWithSearchKeyword() {
        Role trashedAdmin = createAndPersistRole("TrashedAdmin");
        createAndPersistRole("ActiveAdmin");
        roleRepository.trash(trashedAdmin.id).await().indefinitely();

        FindAllRoles req = new FindAllRoles();
        req.setPage(1);
        req.setPageSize(10);
        req.setSearch("Admin"); // Hanya TrashedAdmin yang di-trash

        PagedResult<Role> result = roleRepository.findTrashedRoles(req).await().indefinitely();

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getRoleName()).isEqualTo("TrashedAdmin");
    }

    @Test
    void testFindRolesWithPagination() {
        // Membuat 5 role
        for (int i = 1; i <= 5; i++) {
            createAndPersistRole("PageRole" + i);
        }

        FindAllRoles reqPage1 = new FindAllRoles();
        reqPage1.setPage(1);
        reqPage1.setPageSize(2);

        PagedResult<Role> page1 = roleRepository.findRoles(reqPage1).await().indefinitely();

        assertThat(page1.getData()).hasSize(2);
        assertThat(page1.getTotalRecords()).isEqualTo(5);

        FindAllRoles reqPage2 = new FindAllRoles();
        reqPage2.setPage(2);
        reqPage2.setPageSize(2);

        PagedResult<Role> page2 = roleRepository.findRoles(reqPage2).await().indefinitely();

        assertThat(page2.getData()).hasSize(2);
        // Memastikan ini halaman berikutnya (bukan data yang sama dengan halaman 1)
        assertThat(page2.getData().get(0).getRoleName()).isNotIn(page1.getData().get(0).getRoleName(),
                page1.getData().get(1).getRoleName());
    }

    @Test
    void testFindRolesWithEmptySearchReturnsAll() {
        createAndPersistRole("RoleA");
        createAndPersistRole("RoleB");

        FindAllRoles req = new FindAllRoles();
        req.setPage(1);
        req.setPageSize(10);
        req.setSearch("");

        PagedResult<Role> result = roleRepository.findRoles(req).await().indefinitely();

        assertThat(result.getData()).hasSize(2);
    }

    @Test
    void testFindRolesWithNullSearchReturnsAll() {
        createAndPersistRole("RoleC");
        createAndPersistRole("RoleD");

        FindAllRoles req = new FindAllRoles();
        req.setPage(1);
        req.setPageSize(10);
        req.setSearch(null);

        PagedResult<Role> result = roleRepository.findRoles(req).await().indefinitely();

        assertThat(result.getData()).hasSize(2);
    }

    @Test
    void testFindRolesSearchCaseInsensitive() {
        createAndPersistRole("ManagerRole");

        FindAllRoles reqLower = new FindAllRoles();
        reqLower.setPage(1);
        reqLower.setPageSize(10);
        reqLower.setSearch("manager"); // lowercase

        PagedResult<Role> result = roleRepository.findRoles(reqLower).await().indefinitely();

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getRoleName()).isEqualTo("ManagerRole");
    }

    @Test
    void testFindUserRolesReturnsEmptyWhenNoMapping() {
        // Tidak ada user-role mapping untuk userId 99999
        var result = roleRepository.findUserRoles(99999L).await().indefinitely();

        assertThat(result).isEmpty();
    }
}