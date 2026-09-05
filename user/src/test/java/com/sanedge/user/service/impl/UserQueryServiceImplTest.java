package com.sanedge.user.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.domain.response.ApiResponsePagination;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.user.domain.requests.FindAllUsers;
import com.sanedge.user.domain.response.UserResponse;
import com.sanedge.user.domain.response.UserResponseDeleteAt;
import com.sanedge.user.entity.User;
import com.sanedge.user.repository.UserRepository;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.NotFoundException;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private UserQueryServiceImpl userQueryService;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @BeforeEach
    void setUp() {
        userQueryService = new UserQueryServiceImpl(
                userRepository,
                redisService,
                tracingMetrics,
                objectMapper);

        // Use lenient() so cache-hit tests that don't invoke tracing don't fail with UnnecessaryStubbing
        lenient().when(tracingMetrics.traceAndMeasure(anyString(), anyString(), any(Attributes.class), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(3);
                    return supplier.get();
                });
    }

    // ==========================================
    // HELPER METHODS (Digunakan agar kode tidak berulang dan serialization
    // berhasil)
    // ==========================================

    private User createMockUser(Long id, String username, String email) {
        User user = new User();
        user.id = id;
        user.setUsername(username);
        user.setFirstname("First_" + username);
        user.setLastname("Last_" + username);
        user.setEmail(email);
        return user;
    }

    private FindAllUsers createDefaultRequest() {
        FindAllUsers req = new FindAllUsers();
        req.setPage(1);
        req.setPageSize(10);
        req.setSearch(null);
        return req;
    }

    // Helper khusus untuk mensimulasikan String JSON yang disimpan di Redis
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize in test helper", e);
        }
    }

    // ==========================================
    // 1. TESTS FIND BY ID
    // ==========================================

    @Test
    void findById_cacheHit_returnsCachedUserWithoutHittingDb() {
        User user = createMockUser(1L, "johndoe", "john@example.com");
        UserResponse userResponse = UserResponse.from(user);
        String cachedJson = toJson(userResponse);

        when(redisService.getReactive("user:1")).thenReturn(Uni.createFrom().item(cachedJson));

        Uni<ApiResponse<UserResponse>> resultUni = userQueryService.findById(1L);
        ApiResponse<UserResponse> response = resultUni.await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).isNotNull();
        assertThat(response.data().getUsername()).isEqualTo("johndoe");
        assertThat(response.data().getEmail()).isEqualTo("john@example.com");

        // Verifikasi bahwa DB tidak pernah dipanggil karena data ada di cache
        verify(userRepository, never()).findById(anyLong());
        // Verifikasi bahwa Redis tidak menimpa cache yang sudah ada
        verify(redisService, never()).setReactive(anyString(), anyString());
    }

    @Test
    void findById_cacheMiss_fetchesFromDbAndSavesToCache() {
        User user = createMockUser(2L, "janedoe", "jane@example.com");

        when(redisService.getReactive("user:2")).thenReturn(Uni.createFrom().nullItem());
        when(userRepository.findById(2L)).thenReturn(Uni.createFrom().item(user));
        when(redisService.setReactive(eq("user:2"), anyString())).thenReturn(Uni.createFrom().voidItem());

        Uni<ApiResponse<UserResponse>> resultUni = userQueryService.findById(2L);
        ApiResponse<UserResponse> response = resultUni.await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.message()).isEqualTo("User found");
        assertThat(response.data().getUsername()).isEqualTo("janedoe");

        // Verifikasi DB dipanggil
        verify(userRepository).findById(2L);
        // Verifikasi hasil DB disimpan ke Redis
        verify(redisService).setReactive(eq("user:2"), anyString());
    }

    @Test
    void findById_cacheMiss_userNotFoundInDb_throwsNotFoundException() {
        when(redisService.getReactive("user:999")).thenReturn(Uni.createFrom().nullItem());
        when(userRepository.findById(999L)).thenReturn(Uni.createFrom().nullItem());

        Uni<ApiResponse<UserResponse>> resultUni = userQueryService.findById(999L);

        assertThrows(NotFoundException.class, () -> resultUni.await().indefinitely());

        // Verifikasi tidak mencoba menyimpan data NULL ke cache
        verify(redisService, never()).setReactive(anyString(), anyString());
    }

    // ==========================================
    // 2. TESTS FIND ALL PAGINATED (USERS)
    // ==========================================

    @Test
    void findAllPaginated_cacheHit_returnsCachedList() {
        FindAllUsers req = createDefaultRequest();
        String cacheKey = "users:all:1:10:null";

        UserResponse res1 = UserResponse.from(createMockUser(1L, "user1", "u1@example.com"));
        UserResponse res2 = UserResponse.from(createMockUser(2L, "user2", "u2@example.com"));

        ApiResponsePagination<List<UserResponse>> mockResponse = new ApiResponsePagination<>(
                "success", "Users retrieved successfully", List.of(res1, res2), null);

        String cachedJson = toJson(mockResponse);

        when(redisService.getReactive(cacheKey)).thenReturn(Uni.createFrom().item(cachedJson));

        ApiResponsePagination<List<UserResponse>> response = userQueryService.findAllPaginated(req)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).hasSize(2);
        assertThat(response.data().get(0).getUsername()).isEqualTo("user1");

        // Verifikasi DB tidak dipanggil
        verify(userRepository, never()).findUsers(any(FindAllUsers.class));
    }

    @Test
    void findAllPaginated_cacheMiss_fetchesFromDbAndCachesResult() {
        FindAllUsers req = createDefaultRequest();
        String cacheKey = "users:all:1:10:null";

        User user1 = createMockUser(1L, "user1", "u1@example.com");
        User user2 = createMockUser(2L, "user2", "u2@example.com");

        PagedResult<User> pagedResult = new PagedResult<>(List.of(user1, user2), 2);

        when(redisService.getReactive(cacheKey)).thenReturn(Uni.createFrom().nullItem());
        when(userRepository.findUsers(req)).thenReturn(Uni.createFrom().item(pagedResult));
        when(redisService.setWithExpirationReactive(eq(cacheKey), anyString(), eq(300L)))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponsePagination<List<UserResponse>> response = userQueryService.findAllPaginated(req)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.message()).isEqualTo("Users retrieved successfully");
        assertThat(response.data()).hasSize(2);
        assertThat(response.pagination()).isNotNull();
        assertThat(response.pagination().totalRecords()).isEqualTo(2);
        assertThat(response.pagination().totalPages()).isEqualTo(1); // 2 records / size 10

        verify(userRepository).findUsers(req);
        verify(redisService).setWithExpirationReactive(eq(cacheKey), anyString(), eq(300L));
    }

    // ==========================================
    // 3. TESTS FIND ACTIVE PAGINATED
    // ==========================================

    @Test
    void findActivePaginated_cacheHit_returnsCachedList() {
        FindAllUsers req = createDefaultRequest();
        String cacheKey = "users:active:1:10:null";

        UserResponseDeleteAt res1 = UserResponseDeleteAt.from(createMockUser(1L, "active1", "active1@example.com"));
        ApiResponsePagination<List<UserResponseDeleteAt>> mockResponse = new ApiResponsePagination<>(
                "success", "Active users retrieved successfully", List.of(res1), null);

        String cachedJson = toJson(mockResponse);

        when(redisService.getReactive(cacheKey)).thenReturn(Uni.createFrom().item(cachedJson));

        ApiResponsePagination<List<UserResponseDeleteAt>> response = userQueryService.findActivePaginated(req)
                .await().indefinitely();

        assertThat(response.data()).hasSize(1);
        verify(userRepository, never()).findActiveUsers(any(FindAllUsers.class));
    }

    @Test
    void findActivePaginated_cacheMiss_fetchesFromDbAndCachesResult() {
        FindAllUsers req = createDefaultRequest();
        String cacheKey = "users:active:1:10:null";

        User activeUser = createMockUser(1L, "active_user", "active@example.com");
        PagedResult<User> pagedResult = new PagedResult<>(List.of(activeUser), 1);

        when(redisService.getReactive(cacheKey)).thenReturn(Uni.createFrom().nullItem());
        when(userRepository.findActiveUsers(req)).thenReturn(Uni.createFrom().item(pagedResult));
        when(redisService.setWithExpirationReactive(eq(cacheKey), anyString(), eq(300L)))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponsePagination<List<UserResponseDeleteAt>> response = userQueryService.findActivePaginated(req)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.message()).isEqualTo("Active users retrieved successfully");
        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).getUsername()).isEqualTo("active_user");

        verify(userRepository).findActiveUsers(req);
        verify(redisService).setWithExpirationReactive(eq(cacheKey), anyString(), eq(300L));
    }

    // ==========================================
    // 4. TESTS FIND TRASHED PAGINATED
    // ==========================================

    @Test
    void findTrashedPaginated_cacheHit_returnsCachedList() {
        FindAllUsers req = createDefaultRequest();
        String cacheKey = "users:trashed:1:10:null";

        UserResponseDeleteAt res1 = UserResponseDeleteAt.from(createMockUser(2L, "trashed1", "trashed1@example.com"));
        ApiResponsePagination<List<UserResponseDeleteAt>> mockResponse = new ApiResponsePagination<>(
                "success", "Trashed users retrieved successfully", List.of(res1), null);

        String cachedJson = toJson(mockResponse);

        when(redisService.getReactive(cacheKey)).thenReturn(Uni.createFrom().item(cachedJson));

        ApiResponsePagination<List<UserResponseDeleteAt>> response = userQueryService.findTrashedPaginated(req)
                .await().indefinitely();

        assertThat(response.data()).hasSize(1);
        verify(userRepository, never()).findTrashedUsers(any(FindAllUsers.class));
    }

    @Test
    void findTrashedPaginated_cacheMiss_fetchesFromDbAndCachesResult() {
        FindAllUsers req = createDefaultRequest();
        String cacheKey = "users:trashed:1:10:null";

        User trashedUser = createMockUser(2L, "trashed_user", "trashed@example.com");
        PagedResult<User> pagedResult = new PagedResult<>(List.of(trashedUser), 1);

        when(redisService.getReactive(cacheKey)).thenReturn(Uni.createFrom().nullItem());
        when(userRepository.findTrashedUsers(req)).thenReturn(Uni.createFrom().item(pagedResult));
        when(redisService.setWithExpirationReactive(eq(cacheKey), anyString(), eq(300L)))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponsePagination<List<UserResponseDeleteAt>> response = userQueryService.findTrashedPaginated(req)
                .await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.message()).isEqualTo("Trashed users retrieved successfully");
        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).getUsername()).isEqualTo("trashed_user");

        verify(userRepository).findTrashedUsers(req);
        verify(redisService).setWithExpirationReactive(eq(cacheKey), anyString(), eq(300L));
    }

    // ==========================================
    // 5. EDGE CASES: PAGINATION MATH & DYNAMIC CACHE KEYS
    // ==========================================

    @Test
    void findAllPaginated_calculatesTotalPagesCorrectlyWhenNotPerfectlyDivisible() {
        FindAllUsers req = new FindAllUsers();
        req.setPage(1);
        req.setPageSize(2); // Size 2
        req.setSearch(null);
        String cacheKey = "users:all:1:2:null";

        // Simulasikan ada 5 data total, tapi yang di-fetch di halaman 1 cuma 2
        List<User> users = List.of(
                createMockUser(1L, "u1", "u1@example.com"),
                createMockUser(2L, "u2", "u2@example.com"));
        PagedResult<User> pagedResult = new PagedResult<>(users, 5);

        when(redisService.getReactive(cacheKey)).thenReturn(Uni.createFrom().nullItem());
        when(userRepository.findUsers(req)).thenReturn(Uni.createFrom().item(pagedResult));
        when(redisService.setWithExpirationReactive(eq(cacheKey), anyString(), eq(300L)))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponsePagination<List<UserResponse>> response = userQueryService.findAllPaginated(req)
                .await().indefinitely();

        // 5 records / 2 size = 2.5 -> Math.ceil -> 3 halaman
        assertThat(response.pagination()).isNotNull();
        assertThat(response.pagination().totalPages()).isEqualTo(3);
        assertThat(response.pagination().totalRecords()).isEqualTo(5);
        assertThat(response.pagination().pageSize()).isEqualTo(2);
        assertThat(response.pagination().currentPage()).isEqualTo(1);
    }

    @Test
    void findAllPaginated_withSearchKeyword_usesCorrectDynamicCacheKey() {
        FindAllUsers req = new FindAllUsers();
        req.setPage(2);
        req.setPageSize(5);
        req.setSearch("john doe");

        // Key yang diharapkan berubah mengikuti input request
        String expectedCacheKey = "users:all:2:5:john doe";

        PagedResult<User> emptyResult = new PagedResult<>(List.of(), 0);

        when(redisService.getReactive(expectedCacheKey)).thenReturn(Uni.createFrom().nullItem());
        when(userRepository.findUsers(req)).thenReturn(Uni.createFrom().item(emptyResult));
        when(redisService.setWithExpirationReactive(eq(expectedCacheKey), anyString(), eq(300L)))
                .thenReturn(Uni.createFrom().voidItem());

        userQueryService.findAllPaginated(req).await().indefinitely();

        // Membuktikan bahwa logic String.format di Service berjalan benar untuk kasus
        // apapun
        verify(redisService).getReactive(expectedCacheKey);
        verify(redisService).setWithExpirationReactive(eq(expectedCacheKey), anyString(), eq(300L));
    }

    @Test
    void findActivePaginated_withSearchKeyword_usesCorrectDynamicCacheKey() {
        FindAllUsers req = new FindAllUsers();
        req.setPage(1);
        req.setPageSize(15);
        req.setSearch("admin");

        String expectedCacheKey = "users:active:1:15:admin";

        PagedResult<User> emptyResult = new PagedResult<>(List.of(), 0);

        when(redisService.getReactive(expectedCacheKey)).thenReturn(Uni.createFrom().nullItem());
        when(userRepository.findActiveUsers(req)).thenReturn(Uni.createFrom().item(emptyResult));
        when(redisService.setWithExpirationReactive(eq(expectedCacheKey), anyString(), eq(300L)))
                .thenReturn(Uni.createFrom().voidItem());

        userQueryService.findActivePaginated(req).await().indefinitely();

        verify(redisService).getReactive(expectedCacheKey);
    }

    @Test
    void findTrashedPaginated_withSearchKeyword_usesCorrectDynamicCacheKey() {
        FindAllUsers req = new FindAllUsers();
        req.setPage(3);
        req.setPageSize(20);
        req.setSearch("banned");

        String expectedCacheKey = "users:trashed:3:20:banned";

        PagedResult<User> emptyResult = new PagedResult<>(List.of(), 0);

        when(redisService.getReactive(expectedCacheKey)).thenReturn(Uni.createFrom().nullItem());
        when(userRepository.findTrashedUsers(req)).thenReturn(Uni.createFrom().item(emptyResult));
        when(redisService.setWithExpirationReactive(eq(expectedCacheKey), anyString(), eq(300L)))
                .thenReturn(Uni.createFrom().voidItem());

        userQueryService.findTrashedPaginated(req).await().indefinitely();

        verify(redisService).getReactive(expectedCacheKey);
    }
}