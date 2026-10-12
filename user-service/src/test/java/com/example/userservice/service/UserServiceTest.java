package com.example.userservice.service;

import com.example.common.dto.response.PageResponse;
import com.example.common.exception.BadRequestException;
import com.example.common.exception.ConflictException;
import com.example.common.exception.ResourceNotFoundException;
import com.example.userservice.dto.request.ChangePasswordRequest;
import com.example.userservice.dto.request.UserRegistrationRequest;
import com.example.userservice.dto.request.UserStatusUpdateRequest;
import com.example.userservice.dto.request.UserUpdateRequest;
import com.example.userservice.dto.response.UserProfileResponse;
import com.example.userservice.entity.Role;
import com.example.userservice.entity.User;
import com.example.userservice.entity.UserStatus;
import com.example.userservice.mapper.UserMapper;
import com.example.userservice.repository.RoleRepository;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ==============================================================================
 * KIẾN THỨC VỀ UNIT TESTING VỚI MOCKITO TRONG SPRING BOOT:
 * ------------------------------------------------------------------------------
 * 1. @ExtendWith(MockitoExtension.class):
 *    - Bật hỗ trợ Mockito trong JUnit 5.
 *    - KHÔNG KHỞI ĐỘNG SPRING CONTEXT (không chạy DB thật, không tốn RAM, chạy siêu nhanh tính bằng mili-giây).
 * 2. @Mock:
 *    - Tạo ra các đối tượng GIẢ LẬP (UserRepository, PasswordEncoder, RoleRepository).
 *    - Ta toàn quyền kiểm soát kết quả trả về của các hàm giả lập bằng `when(...).thenReturn(...)`.
 * 3. UserMapper:
 *    - Dùng đối tượng UserMapper thật (khởi tạo trực tiếp bằng new) để test chính xác
 *      kết quả chuyển đổi dữ liệu mà không cần can thiệp bytecode qua @Spy.
 * ==============================================================================
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private com.example.userservice.repository.RefreshTokenRepository refreshTokenRepository;

    @Mock
    private com.example.userservice.service.OutboxService outboxService;

    private final UserMapper userMapper = new UserMapper();

    private UserServiceImpl userService;

    private User sampleUser;
    private Role userRole;
    private final UUID sampleUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, roleRepository, passwordEncoder, userMapper, refreshTokenRepository, outboxService);

        userRole = new Role(1, "ROLE_USER");

        sampleUser = new User();
        sampleUser.setId(sampleUserId);
        sampleUser.setEmail("test@example.com");
        sampleUser.setPasswordHash("hashed_password_123");
        sampleUser.setFullName("Nguyen Van Test");
        sampleUser.setPhone("0987654321");
        sampleUser.setStatus(UserStatus.ACTIVE);
        sampleUser.setCreatedAt(Instant.now());
        sampleUser.setUpdatedAt(Instant.now());
        sampleUser.setVersion(0L);
        sampleUser.setRoles(Set.of(userRole));
    }

    // =========================================================================
    // 1. KIỂM THỬ ĐĂNG KÝ (REGISTER)
    // =========================================================================

    @Test
    @DisplayName("Đăng ký thành công: Lưu user với mật khẩu băm và role mặc định")
    void register_Success() {
        UserRegistrationRequest request = new UserRegistrationRequest(
                "NewUser@example.com",
                "Password123",
                "Tran Thi B",
                "0912345678"
        );

        when(userRepository.existsByEmailIgnoreCase("newuser@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("encoded_bcrypt_pw");
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(userRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            u.setCreatedAt(Instant.now());
            u.setUpdatedAt(Instant.now());
            return u;
        });

        UserProfileResponse response = userService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("newuser@example.com");
        assertThat(response.getFullName()).isEqualTo("Tran Thi B");
        assertThat(response.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.getRoles()).contains("ROLE_USER");

        verify(passwordEncoder).encode("Password123");
        verify(userRepository, times(1)).save(any(User.class));
        verify(outboxService, times(1)).saveEvent(eq("USER"), any(), eq("UserCreatedEvent"), any());
    }

    @Test
    @DisplayName("Đăng ký thất bại khi email đã tồn tại: Ném lỗi ConflictException (409)")
    void register_DuplicateEmail_ThrowsConflictException() {
        UserRegistrationRequest request = new UserRegistrationRequest(
                "test@example.com",
                "Password123",
                "Nguyen Van Test",
                null
        );

        when(userRepository.existsByEmailIgnoreCase("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email 'test@example.com' đã được sử dụng");

        verify(userRepository, never()).save(any(User.class));
    }

    // =========================================================================
    // 2. KIỂM THỬ XEM PROFILE (GET PROFILE)
    // =========================================================================

    @Test
    @DisplayName("Lấy profile thành công khi User tồn tại")
    void getProfile_Success() {
        when(userRepository.findById(sampleUserId)).thenReturn(Optional.of(sampleUser));

        UserProfileResponse response = userService.getProfile(sampleUserId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(sampleUserId);
        assertThat(response.getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("Lấy profile thất bại khi không tìm thấy User: Ném lỗi ResourceNotFoundException (404)")
    void getProfile_UserNotFound_ThrowsResourceNotFoundException() {
        UUID nonExistentId = UUID.randomUUID();
        when(userRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(nonExistentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy người dùng");
    }

    // =========================================================================
    // 3. KIỂM THỬ CẬP NHẬT PROFILE (UPDATE PROFILE)
    // =========================================================================

    @Test
    @DisplayName("Cập nhật thông tin cá nhân thành công")
    void updateProfile_Success() {
        UserUpdateRequest request = new UserUpdateRequest("Nguyen Van Updated", "0999999999", "https://avatar.png");
        when(userRepository.findById(sampleUserId)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileResponse response = userService.updateProfile(sampleUserId, request);

        assertThat(response.getFullName()).isEqualTo("Nguyen Van Updated");
        assertThat(response.getPhone()).isEqualTo("0999999999");
        assertThat(response.getAvatarUrl()).isEqualTo("https://avatar.png");
        verify(outboxService, times(1)).saveEvent(eq("USER"), eq(sampleUserId.toString()), eq("UserUpdatedEvent"), any());
    }

    // =========================================================================
    // 4. KIỂM THỬ ĐỔI MẬT KHẨU (CHANGE PASSWORD)
    // =========================================================================

    @Test
    @DisplayName("Đổi mật khẩu thành công khi mật khẩu cũ chính xác")
    void changePassword_Success() {
        ChangePasswordRequest request = new ChangePasswordRequest("OldPassword123", "NewPassword456");

        when(userRepository.findById(sampleUserId)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("OldPassword123", "hashed_password_123")).thenReturn(true);
        when(passwordEncoder.matches("NewPassword456", "hashed_password_123")).thenReturn(false);
        when(passwordEncoder.encode("NewPassword456")).thenReturn("new_hashed_pw");

        userService.changePassword(sampleUserId, request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("new_hashed_pw");
        verify(refreshTokenRepository).revokeAllByUserId(sampleUserId);
    }

    @Test
    @DisplayName("Đổi mật khẩu thất bại khi nhập sai mật khẩu cũ: Ném BadRequestException (400)")
    void changePassword_WrongOldPassword_ThrowsBadRequestException() {
        ChangePasswordRequest request = new ChangePasswordRequest("WrongPassword", "NewPassword456");

        when(userRepository.findById(sampleUserId)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("WrongPassword", "hashed_password_123")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(sampleUserId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Mật khẩu cũ không chính xác");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Đổi mật khẩu thất bại khi mật khẩu mới trùng mật khẩu cũ: Ném BadRequestException (400)")
    void changePassword_NewPasswordSameAsOld_ThrowsBadRequestException() {
        ChangePasswordRequest request = new ChangePasswordRequest("OldPassword123", "OldPassword123");

        when(userRepository.findById(sampleUserId)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("OldPassword123", "hashed_password_123")).thenReturn(true);

        assertThatThrownBy(() -> userService.changePassword(sampleUserId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Mật khẩu mới không được trùng với mật khẩu cũ");

        verify(userRepository, never()).save(any(User.class));
    }

    // =========================================================================
    // 5. KIỂM THỬ QUẢN TRỊ ADMIN (SEARCH, UPDATE STATUS, SOFT DELETE)
    // =========================================================================

    @Test
    @DisplayName("ADMIN: Lấy thông tin user theo ID thành công")
    void getUserById_Success() {
        when(userRepository.findById(sampleUserId)).thenReturn(Optional.of(sampleUser));
        UserProfileResponse response = userService.getUserById(sampleUserId);
        assertThat(response.getId()).isEqualTo(sampleUserId);
    }

    @Test
    @DisplayName("ADMIN: Tìm kiếm người dùng phân trang thành công")
    void searchUsers_Success() {
        Page<User> page = new PageImpl<>(List.of(sampleUser));
        when(userRepository.searchUsers(eq("test"), eq(UserStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(page);

        PageResponse<UserProfileResponse> result = userService.searchUsers(
                "test",
                UserStatus.ACTIVE,
                0,
                10,
                "createdAt,desc"
        );

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("ADMIN: Khóa tài khoản (Cập nhật trạng thái) thành công")
    void updateStatus_Success() {
        UserStatusUpdateRequest request = new UserStatusUpdateRequest(UserStatus.LOCKED);
        when(userRepository.findById(sampleUserId)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileResponse response = userService.updateStatus(sampleUserId, request);

        assertThat(response.getStatus()).isEqualTo(UserStatus.LOCKED);
    }

    @Test
    @DisplayName("ADMIN: Xóa mềm tài khoản (chuyển status sang DELETED)")
    void deleteUser_Success() {
        when(userRepository.findById(sampleUserId)).thenReturn(Optional.of(sampleUser));

        userService.deleteUser(sampleUserId);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(UserStatus.DELETED);
        verify(outboxService, times(1)).saveEvent(eq("USER"), eq(sampleUserId.toString()), eq("UserDeletedEvent"), any());
    }
}
