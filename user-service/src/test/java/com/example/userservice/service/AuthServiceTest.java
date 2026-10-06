package com.example.userservice.service;

import com.example.common.exception.BusinessException;
import com.example.common.exception.UnauthorizedException;
import com.example.userservice.dto.request.LoginRequest;
import com.example.userservice.dto.request.LogoutRequest;
import com.example.userservice.dto.request.RefreshTokenRequest;
import com.example.userservice.dto.response.AuthResponse;
import com.example.userservice.entity.RefreshToken;
import com.example.userservice.entity.Role;
import com.example.userservice.entity.User;
import com.example.userservice.entity.UserStatus;
import com.example.userservice.repository.RefreshTokenRepository;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.security.JwtService;
import com.example.userservice.security.LoginAttemptService;
import com.example.userservice.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho AuthServiceImpl (Login, Token Rotation, Logout, Brute-force & Account Status)
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private final JwtService jwtService = new JwtService(
            "test_super_secret_jwt_key_that_is_at_least_256_bits_long_for_security_testing_purpose",
            900000L,
            604800000L
    );

    @Mock
    private LoginAttemptService loginAttemptService;

    private AuthServiceImpl authService;

    private User sampleUser;
    private final UUID sampleUserId = UUID.randomUUID();
    private final String rawPassword = "Password123@";
    private final String encodedPassword = "bcrypt_encoded_password";
    private final String userEmail = "test@example.com";

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtService,
                loginAttemptService
        );

        sampleUser = new User();
        sampleUser.setId(sampleUserId);
        sampleUser.setEmail(userEmail);
        sampleUser.setPasswordHash(encodedPassword);
        sampleUser.setFullName("Nguyen Van Test");
        sampleUser.setStatus(UserStatus.ACTIVE);
        sampleUser.setRoles(Set.of(new Role(1, "ROLE_USER")));
    }

    // =========================================================================
    // 1. KIỂM THỬ ĐĂNG NHẬP (LOGIN)
    // =========================================================================

    @Test
    @DisplayName("Đăng nhập thành công: Cấp phát Access Token và lưu băm Refresh Token vào DB")
    void login_Success() {
        LoginRequest request = new LoginRequest(userEmail, rawPassword);

        when(loginAttemptService.isBlocked(userEmail)).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(jwtService.validateToken(response.getAccessToken())).isTrue();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getUserId()).isEqualTo(sampleUserId);
        assertThat(response.getEmail()).isEqualTo(userEmail);
        assertThat(response.getRoles()).contains("ROLE_USER");

        verify(loginAttemptService).resetAttempts(userEmail);

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        RefreshToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getUser()).isEqualTo(sampleUser);
        assertThat(savedToken.getTokenHash()).isEqualTo(JwtService.hashToken(response.getRefreshToken()));
        assertThat(savedToken.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("Đăng nhập thất bại do sai mật khẩu: Ghi nhận lần sai vào Redis, ném 401 Unauthorized")
    void login_WrongPassword_ThrowsUnauthorizedException() {
        LoginRequest request = new LoginRequest(userEmail, "WrongPass");

        when(loginAttemptService.isBlocked(userEmail)).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("WrongPass", encodedPassword)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Email hoặc mật khẩu không chính xác");

        verify(loginAttemptService).recordFailedAttempt(userEmail);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Đăng nhập thất bại do email không tồn tại: Ném lỗi 401 với thông báo chung (Zero Enumeration)")
    void login_UserNotFound_ThrowsUnauthorizedException() {
        LoginRequest request = new LoginRequest("nonexistent@example.com", rawPassword);

        when(loginAttemptService.isBlocked("nonexistent@example.com")).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase("nonexistent@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Email hoặc mật khẩu không chính xác");

        verify(loginAttemptService).recordFailedAttempt("nonexistent@example.com");
    }

    @Test
    @DisplayName("Đăng nhập thất bại do tài khoản bị khóa (LOCKED): Ném BusinessException mã 423")
    void login_AccountLocked_ThrowsLockedException() {
        sampleUser.setStatus(UserStatus.LOCKED);
        LoginRequest request = new LoginRequest(userEmail, rawPassword);

        when(loginAttemptService.isBlocked(userEmail)).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(423);
                    assertThat(be.getCode()).isEqualTo("ACCOUNT_LOCKED");
                });
    }

    @Test
    @DisplayName("Đăng nhập thất bại do tài khoản bị xóa mềm (DELETED): Ném lỗi 401 Unauthorized")
    void login_AccountDeleted_ThrowsUnauthorized() {
        sampleUser.setStatus(UserStatus.DELETED);
        LoginRequest request = new LoginRequest(userEmail, rawPassword);

        when(loginAttemptService.isBlocked(userEmail)).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("Đăng nhập thất bại do nhập sai >= 5 lần (Brute-force): Ném BusinessException mã 429 Too Many Requests")
    void login_BruteForceBlocked_ThrowsTooManyRequests() {
        LoginRequest request = new LoginRequest(userEmail, rawPassword);
        when(loginAttemptService.isBlocked(userEmail)).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(429);
                    assertThat(be.getCode()).isEqualTo("TOO_MANY_REQUESTS");
                });

        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
    }

    // =========================================================================
    // 2. KIỂM THỬ LÀM MỚI TOKEN (REFRESH TOKEN ROTATION)
    // =========================================================================

    @Test
    @DisplayName("Xoay vòng token thành công: Thu hồi token cũ, lưu token mới và trả cặp token mới")
    void refresh_Success_RotatesToken() {
        String oldRawToken = "valid-old-raw-token";
        String oldHash = JwtService.hashToken(oldRawToken);
        RefreshToken storedOldToken = new RefreshToken(sampleUser, oldHash, Instant.now().plusSeconds(3600));

        when(refreshTokenRepository.findByTokenHash(oldHash)).thenReturn(Optional.of(storedOldToken));

        AuthResponse response = authService.refresh(new RefreshTokenRequest(oldRawToken));

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(jwtService.validateToken(response.getAccessToken())).isTrue();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotEqualTo(oldRawToken);

        // Kiểm tra token cũ đã bị thu hồi và gắn hash token mới
        assertThat(storedOldToken.isRevoked()).isTrue();
        assertThat(storedOldToken.getReplacedBy()).isEqualTo(JwtService.hashToken(response.getRefreshToken()));

        // Lưu cả token cũ (đã thu hồi) và token mới
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Xoay vòng thất bại khi token cũ đã từng bị thu hồi trước đó (Tấn công tái sử dụng): Ném 401")
    void refresh_RevokedToken_ThrowsUnauthorized() {
        String rawToken = "already-revoked-token";
        String tokenHash = JwtService.hashToken(rawToken);
        RefreshToken storedToken = new RefreshToken(sampleUser, tokenHash, Instant.now().plusSeconds(3600));
        storedToken.revoke("some-other-hash");

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(storedToken));

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(rawToken)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("thu hồi");
    }

    @Test
    @DisplayName("Xoay vòng thất bại khi refresh token đã hết hạn: Ném 401 Unauthorized")
    void refresh_ExpiredToken_ThrowsUnauthorized() {
        String rawToken = "expired-token";
        String tokenHash = JwtService.hashToken(rawToken);
        // Hết hạn từ 10 giây trước
        RefreshToken storedToken = new RefreshToken(sampleUser, tokenHash, Instant.now().minusSeconds(10));

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(storedToken));

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(rawToken)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("hết hạn");
    }

    @Test
    @DisplayName("Xoay vòng thất bại khi không tìm thấy token trong DB: Ném 401 Unauthorized")
    void refresh_TokenNotFound_ThrowsUnauthorized() {
        String rawToken = "nonexistent-token";
        String tokenHash = JwtService.hashToken(rawToken);

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(rawToken)))
                .isInstanceOf(UnauthorizedException.class);
    }

    // =========================================================================
    // 3. KIỂM THỬ ĐĂNG XUẤT (LOGOUT)
    // =========================================================================

    @Test
    @DisplayName("Đăng xuất thành công: Thu hồi Refresh Token trong DB")
    void logout_Success() {
        String rawToken = "token-to-logout";
        String tokenHash = JwtService.hashToken(rawToken);
        RefreshToken storedToken = new RefreshToken(sampleUser, tokenHash, Instant.now().plusSeconds(3600));

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(storedToken));

        authService.logout(new LogoutRequest(rawToken));

        assertThat(storedToken.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(storedToken);
    }
}
