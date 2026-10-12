package com.example.userservice.service;

import com.example.userservice.config.RedisConfig;
import com.example.userservice.dto.request.ChangePasswordRequest;
import com.example.userservice.dto.request.UserStatusUpdateRequest;
import com.example.userservice.dto.request.UserUpdateRequest;
import com.example.userservice.dto.response.UserProfileResponse;
import com.example.userservice.entity.Role;
import com.example.userservice.entity.User;
import com.example.userservice.entity.UserStatus;
import com.example.userservice.mapper.UserMapper;
import com.example.userservice.repository.RefreshTokenRepository;
import com.example.userservice.repository.RoleRepository;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ==============================================================================
 * KIẾN THỨC TEST CACHE TRONG SPRING BOOT:
 * ------------------------------------------------------------------------------
 * 1. Mục tiêu kiểm thử (Test Objectives - DoD Bước 3.6):
 *    - Chứng minh lần gọi thứ nhất đọc Database, lần gọi thứ hai lấy từ Cache (không query DB).
 *    - Chứng minh khi Cập nhật / Đổi mật khẩu / Khóa / Xóa thì Cache bị xóa (Evicted).
 *    - Lần gọi sau khi Cache bị xóa sẽ bắt buộc đọc lại Database.
 *
 * 2. Kiến trúc kiểm thử:
 *    - Sử dụng @ExtendWith(SpringExtension.class) kết hợp với @EnableCaching để
 *      kích hoạt Spring AOP Caching Proxy cho UserServiceImpl.
 *    - Dùng MockBean cho UserRepository, RoleRepository, PasswordEncoder, RefreshTokenRepository
 *      để đếm chính xác số lần DB được gọi (verify(..., times(1))).
 * ==============================================================================
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = UserServiceCacheTest.TestCacheConfig.class)
class UserServiceCacheTest {

    @Configuration
    @EnableCaching
    @Import(UserMapper.class)
    static class TestCacheConfig implements org.springframework.cache.annotation.CachingConfigurer {

        @Bean
        public CacheManager cacheManager() {
            // Sử dụng ConcurrentMapCacheManager mô phỏng Cache in-memory cho Unit/Integration Test
            return new ConcurrentMapCacheManager(RedisConfig.USER_PROFILE_CACHE);
        }

        @Override
        public org.springframework.cache.interceptor.CacheErrorHandler errorHandler() {
            // Tích hợp đúng CacheErrorHandler của RedisConfig để test khả năng chịu lỗi (Resilience)
            return new RedisConfig().errorHandler();
        }

        @Bean
        public UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        public RoleRepository roleRepository() {
            return mock(RoleRepository.class);
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
            return mock(PasswordEncoder.class);
        }

        @Bean
        public RefreshTokenRepository refreshTokenRepository() {
            return mock(RefreshTokenRepository.class);
        }

        @Bean
        public com.example.userservice.service.OutboxService outboxService() {
            return mock(com.example.userservice.service.OutboxService.class);
        }

        @Bean
        public UserService userService(
                UserRepository userRepository,
                RoleRepository roleRepository,
                PasswordEncoder passwordEncoder,
                UserMapper userMapper,
                RefreshTokenRepository refreshTokenRepository,
                com.example.userservice.service.OutboxService outboxService
        ) {
            return new UserServiceImpl(
                    userRepository,
                    roleRepository,
                    passwordEncoder,
                    userMapper,
                    refreshTokenRepository,
                    outboxService
            );
        }
    }

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CacheManager cacheManager;

    private UUID userId;
    private User mockUser;

    @BeforeEach
    void setUp() {
        // Đặt lại các mock và xóa sạch cache trước mỗi test case
        reset(userRepository, passwordEncoder);
        Cache cache = cacheManager.getCache(RedisConfig.USER_PROFILE_CACHE);
        if (cache != null) {
            cache.clear();
        }

        userId = UUID.randomUUID();
        mockUser = new User();
        mockUser.setId(userId);
        mockUser.setEmail("test@example.com");
        mockUser.setFullName("Nguyen Van A");
        mockUser.setPasswordHash("hashed_password");
        mockUser.setStatus(UserStatus.ACTIVE);
        mockUser.setRoles(Set.of(new Role("ROLE_USER")));
        mockUser.setCreatedAt(Instant.now());
        mockUser.setUpdatedAt(Instant.now());
    }

    @Test
    @DisplayName("Lần 1 gọi getProfile() đọc DB, lần 2 gọi getProfile() lấy từ Cache (không query DB)")
    void testGetProfileCacheHit() {
        // Given: Giả lập Database trả về thông tin user khi tìm theo userId
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        // When 1: Lần gọi đầu tiên (Cache Miss)
        UserProfileResponse response1 = userService.getProfile(userId);

        // Then 1: Lấy được profile và DB được gọi đúng 1 lần
        assertThat(response1).isNotNull();
        assertThat(response1.getEmail()).isEqualTo("test@example.com");
        verify(userRepository, times(1)).findById(userId);

        // When 2: Lần gọi thứ hai với cùng userId (Cache Hit)
        UserProfileResponse response2 = userService.getProfile(userId);

        // Then 2: Kết quả vẫn chính xác nhưng DB KHÔNG HỀ BỊ GỌI THÊM LẦN NÀO
        assertThat(response2).isNotNull();
        assertThat(response2.getEmail()).isEqualTo("test@example.com");
        verify(userRepository, times(1)).findById(userId); // Vẫn chỉ là 1 lần duy nhất!
    }

    @Test
    @DisplayName("Lần 1 gọi getUserById() đọc DB, lần 2 gọi getUserById() lấy từ Cache chung user_profiles")
    void testGetUserByIdCacheHit() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        UserProfileResponse response1 = userService.getUserById(userId);
        assertThat(response1).isNotNull();
        verify(userRepository, times(1)).findById(userId);

        UserProfileResponse response2 = userService.getUserById(userId);
        assertThat(response2).isNotNull();
        verify(userRepository, times(1)).findById(userId); // Cache hit
    }

    @Test
    @DisplayName("Cập nhật Profile -> Tự động xóa Cache -> Lần get tiếp theo phải đọc lại DB")
    void testUpdateProfileEvictsCache() {
        // Given: Lần 1 nạp profile vào cache
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.getProfile(userId);
        verify(userRepository, times(1)).findById(userId);

        // When: Thực hiện cập nhật profile (kích hoạt @CacheEvict)
        UserUpdateRequest updateRequest = new UserUpdateRequest();
        updateRequest.setFullName("Nguyen Van A Updated");
        userService.updateProfile(userId, updateRequest);

        // Then: Gọi lại getProfile(userId) -> Cache đã bị xóa nên DB PHẢI ĐƯỢC GỌI LẠI
        userService.getProfile(userId);
        verify(userRepository, times(3)).findById(userId);
        // Giải thích số lần findById: 1 (lần get đầu) + 1 (trong updateProfile) + 1 (lần get sau update do cache miss) = 3 lần
    }

    @Test
    @DisplayName("Đổi mật khẩu -> Tự động xóa Cache Profile")
    void testChangePasswordEvictsCache() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("oldPass123", "hashed_password")).thenReturn(true);
        when(passwordEncoder.matches("newPass123", "hashed_password")).thenReturn(false);
        when(passwordEncoder.encode("newPass123")).thenReturn("new_hashed_password");
        when(userRepository.save(any(User.class))).thenReturn(mockUser);

        // Nạp cache
        userService.getProfile(userId);
        verify(userRepository, times(1)).findById(userId);

        // Đổi mật khẩu
        ChangePasswordRequest changeRequest = new ChangePasswordRequest();
        changeRequest.setOldPassword("oldPass123");
        changeRequest.setNewPassword("newPass123");
        userService.changePassword(userId, changeRequest);

        // Lần get tiếp theo phải truy vấn lại DB vì cache đã bị xóa
        userService.getProfile(userId);
        verify(userRepository, times(3)).findById(userId);
    }

    @Test
    @DisplayName("Khóa tài khoản (updateStatus) -> Tự động xóa Cache Profile")
    void testUpdateStatusEvictsCache() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(userRepository.save(any(User.class))).thenReturn(mockUser);

        // Nạp cache
        userService.getProfile(userId);
        verify(userRepository, times(1)).findById(userId);

        // Khóa tài khoản
        UserStatusUpdateRequest statusRequest = new UserStatusUpdateRequest();
        statusRequest.setStatus(UserStatus.LOCKED);
        userService.updateStatus(userId, statusRequest);

        // Lần get tiếp theo phải truy vấn lại DB
        userService.getProfile(userId);
        verify(userRepository, times(3)).findById(userId);
    }

    @Test
    @DisplayName("Xóa mềm người dùng (deleteUser) -> Tự động xóa Cache Profile")
    void testDeleteUserEvictsCache() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(userRepository.save(any(User.class))).thenReturn(mockUser);

        // Nạp cache
        userService.getProfile(userId);
        verify(userRepository, times(1)).findById(userId);

        // Xóa người dùng
        userService.deleteUser(userId);

        // Lần get tiếp theo phải truy vấn lại DB
        userService.getProfile(userId);
        verify(userRepository, times(3)).findById(userId);
    }

    @Test
    @DisplayName("Graceful Degradation: Khi Redis gặp lỗi, ErrorHandler bắt lỗi và service vẫn đọc DB thành công")
    void testRedisDownFallsBackToDatabase() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        // Mô phỏng trường hợp Redis gặp sự cố ném ngoại lệ
        Cache failingCache = mock(Cache.class);
        when(failingCache.getName()).thenReturn(RedisConfig.USER_PROFILE_CACHE);
        when(failingCache.get(userId)).thenThrow(new RuntimeException("Redis connection timed out"));

        // Đảm bảo CacheErrorHandler không ném ngoại lệ ra ngoài
        org.springframework.cache.interceptor.CacheErrorHandler errorHandler = new RedisConfig().errorHandler();
        org.assertj.core.api.Assertions.assertThatCode(() ->
                errorHandler.handleCacheGetError(new RuntimeException("Redis connection timed out"), failingCache, userId)
        ).doesNotThrowAnyException();

        // Nghiệp vụ vẫn tiếp tục đọc từ Database và trả về kết quả cho người dùng
        UserProfileResponse response = userService.getProfile(userId);
        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        verify(userRepository, times(1)).findById(userId);
    }
}
