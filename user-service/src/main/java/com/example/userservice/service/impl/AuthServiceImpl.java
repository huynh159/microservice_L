package com.example.userservice.service.impl;

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
import com.example.userservice.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ==============================================================================
 * TRIỂN KHAI DỊCH VỤ XÁC THỰC (AUTH SERVICE IMPLEMENTATION) - BƯỚC 3.5
 * ------------------------------------------------------------------------------
 * 1. Đăng nhập an toàn (Secure Login Flow):
 *    - Chống Brute-force: Kiểm tra Redis xem email có bị khóa tạm thời do sai ≥ 5 lần không.
 *    - Che giấu danh tính (Zero User Enumeration): Dù không tìm thấy email hay sai mật khẩu,
 *      luôn trả về thông báo chung: "Email hoặc mật khẩu không chính xác" (tránh hacker dò email).
 *    - Kiểm tra trạng thái tài khoản: LOCKED (HTTP 423) hoặc DELETED (bị vô hiệu hóa).
 *
 * 2. Cơ chế Xoay vòng Refresh Token (Refresh Token Rotation):
 *    - Refresh token cũ lập tức bị đánh dấu đã thu hồi (revoked_at = now()) và ghi nhận
 *      replaced_by = hash của token mới.
 *    - Nếu kẻ gian đánh cắp được refresh token cũ và cố tình gọi /refresh lại, hệ thống
 *      sẽ phát hiện token này đã bị revoke và từ chối cấp token mới.
 * ==============================================================================
 */
@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;

    public AuthServiceImpl(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginAttemptService loginAttemptService
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
    }

    /**
     * ĐĂNG NHẬP (LOGIN)
     */
    @Override
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        // 1. Kiểm tra giới hạn tần suất nhập sai (Brute-force Attack Protection)
        if (loginAttemptService.isBlocked(email)) {
            log.warn("Tài khoản email [{}] bị tạm khóa do nhập sai mật khẩu quá 5 lần", email);
            throw new BusinessException(
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "TOO_MANY_REQUESTS",
                    "Đăng nhập sai quá nhiều lần (≥ 5 lần). Vui lòng thử lại sau 15 phút."
            );
        }

        // 2. Tìm người dùng theo Email (không phân biệt hoa thường)
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);

        // 3. Kiểm tra sự tồn tại của User và khớp mật khẩu BCrypt
        if (userOpt.isEmpty() || !passwordEncoder.matches(request.getPassword(), userOpt.get().getPasswordHash())) {
            // Ghi nhận lần nhập sai vào Redis
            loginAttemptService.recordFailedAttempt(email);

            // BẢO MẬT: Tuyệt đối không nói "Email không tồn tại" hay "Mật khẩu sai" riêng biệt!
            throw new UnauthorizedException("BAD_CREDENTIALS", "Email hoặc mật khẩu không chính xác");
        }

        User user = userOpt.get();

        // 4. Kiểm tra trạng thái tài khoản
        if (user.getStatus() == UserStatus.LOCKED) {
            log.warn("Người dùng [{}] cố gắng đăng nhập nhưng tài khoản đang ở trạng thái LOCKED", user.getId());
            throw new BusinessException(
                    HttpStatus.LOCKED.value(),
                    "ACCOUNT_LOCKED",
                    "Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên."
            );
        }

        if (user.getStatus() == UserStatus.DELETED) {
            log.warn("Người dùng [{}] cố gắng đăng nhập nhưng tài khoản đã bị xóa mềm (DELETED)", user.getId());
            throw new UnauthorizedException("BAD_CREDENTIALS", "Email hoặc mật khẩu không chính xác");
        }

        // 5. Đăng nhập thành công -> Xóa bộ đếm số lần nhập sai trên Redis
        loginAttemptService.resetAttempts(email);

        // 6. Tạo cặp Access Token và Refresh Token
        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), roles);
        String rawRefreshToken = jwtService.generateRefreshToken();

        // 7. Băm SHA-256 chuỗi refresh token và lưu vào Database
        String tokenHash = JwtService.hashToken(rawRefreshToken);
        Instant expiresAt = Instant.now().plusMillis(jwtService.getRefreshExpirationMs());

        RefreshToken refreshTokenEntity = new RefreshToken(user, tokenHash, expiresAt);
        refreshTokenRepository.save(refreshTokenEntity);

        log.info("Người dùng [{}] đăng nhập thành công. Cấp token mới với hạn Access Token là 15 phút", user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationMs() / 1000)
                .userId(user.getId())
                .email(user.getEmail())
                .roles(roles)
                .build();
    }

    /**
     * XOAY VÒNG REFRESH TOKEN (REFRESH TOKEN ROTATION)
     */
    @Override
    public AuthResponse refresh(RefreshTokenRequest request) {
        String rawRefreshToken = request.getRefreshToken();
        String tokenHash = JwtService.hashToken(rawRefreshToken);

        // 1. Tìm token trong Database theo chuỗi băm
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("INVALID_REFRESH_TOKEN", "Refresh token không hợp lệ"));

        // 2. Kiểm tra xem token này đã từng bị thu hồi trước đó chưa (Phát hiện tấn công giả mạo)
        if (storedToken.isRevoked()) {
            log.warn("Phát hiện cố gắng tái sử dụng Refresh Token đã bị thu hồi của người dùng [{}]",
                    storedToken.getUser().getId());
            throw new UnauthorizedException("REVOKED_REFRESH_TOKEN", "Refresh token này đã bị thu hồi hoặc đã từng được sử dụng");
        }

        // 3. Kiểm tra hạn sử dụng của Refresh Token
        if (storedToken.isExpired()) {
            throw new UnauthorizedException("EXPIRED_REFRESH_TOKEN", "Refresh token đã hết hạn sử dụng. Vui lòng đăng nhập lại.");
        }

        User user = storedToken.getUser();

        // 4. Kiểm tra trạng thái tài khoản người dùng
        if (user.getStatus() == UserStatus.LOCKED || user.getStatus() == UserStatus.DELETED) {
            throw new BusinessException(
                    HttpStatus.LOCKED.value(),
                    "ACCOUNT_LOCKED",
                    "Tài khoản người dùng đã bị khóa hoặc không còn hoạt động"
            );
        }

        // 5. Sinh cặp Refresh Token mới
        String newRawRefreshToken = jwtService.generateRefreshToken();
        String newTokenHash = JwtService.hashToken(newRawRefreshToken);
        Instant newExpiresAt = Instant.now().plusMillis(jwtService.getRefreshExpirationMs());

        // 6. Xoay vòng token: Đánh dấu token cũ là ĐÃ THU HỒI và liên kết với token mới
        storedToken.revoke(newTokenHash);
        refreshTokenRepository.save(storedToken);

        // 7. Lưu token mới vào Database
        RefreshToken newRefreshTokenEntity = new RefreshToken(user, newTokenHash, newExpiresAt);
        refreshTokenRepository.save(newRefreshTokenEntity);

        // 8. Tạo Access Token mới
        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
        String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), roles);

        log.info("Xoay vòng token thành công cho người dùng [{}]. Token cũ đã bị thu hồi.", user.getId());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationMs() / 1000)
                .userId(user.getId())
                .email(user.getEmail())
                .roles(roles)
                .build();
    }

    /**
     * ĐĂNG XUẤT (LOGOUT)
     */
    @Override
    public void logout(LogoutRequest request) {
        String rawRefreshToken = request.getRefreshToken();
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String tokenHash = JwtService.hashToken(rawRefreshToken);
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
                if (token.isActive()) {
                    token.revoke(null);
                    refreshTokenRepository.save(token);
                    log.info("Thu hồi Refresh Token thành công cho phiên làm việc của user [{}]", token.getUser().getId());
                }
            });
        }
    }
}
