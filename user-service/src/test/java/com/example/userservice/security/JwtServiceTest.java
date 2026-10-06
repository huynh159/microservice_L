package com.example.userservice.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit Test cho JwtService (Tạo token, xác minh chữ ký, băm SHA-256)
 */
class JwtServiceTest {

    private JwtService jwtService;
    private final String secret = "test_super_secret_jwt_key_that_is_at_least_256_bits_long_for_security_testing_purpose";
    private final long expirationMs = 900000; // 15 phút
    private final long refreshExpirationMs = 604800000; // 7 ngày

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(secret, expirationMs, refreshExpirationMs);
    }

    @Test
    @DisplayName("Tạo Access Token thành công và chứa đầy đủ Claims: sub, email, roles, jti")
    void generateAccessToken_ContainsRequiredClaims() {
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        Set<String> roles = Set.of("ROLE_USER", "ROLE_ADMIN");

        String token = jwtService.generateAccessToken(userId, email, roles);

        assertThat(token).isNotBlank();
        assertThat(jwtService.validateToken(token)).isTrue();

        Claims claims = jwtService.extractAllClaims(token);
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("email")).isEqualTo(email);
        assertThat(claims.getId()).isNotBlank(); // jti claim
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getIssuedAt()).isNotNull();

        assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
        assertThat(jwtService.extractEmail(token)).isEqualTo(email);
        assertThat(jwtService.extractRoles(token)).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("Token bị sửa đổi hoặc giả mạo chữ ký -> validateToken trả về false")
    void validateToken_TamperedToken_ReturnsFalse() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(userId, "user@example.com", Set.of("ROLE_USER"));
        String tamperedToken = token + "corrupted";

        assertThat(jwtService.validateToken(tamperedToken)).isFalse();
    }

    @Test
    @DisplayName("Token hết hạn -> validateToken trả về false")
    void validateToken_ExpiredToken_ReturnsFalse() {
        // Khởi tạo JwtService với thời gian hết hạn âm (-1 giây) để token hết hạn ngay lập tức
        JwtService expiredJwtService = new JwtService(secret, -1000, refreshExpirationMs);
        String token = expiredJwtService.generateAccessToken(UUID.randomUUID(), "expired@example.com", Set.of("ROLE_USER"));

        assertThat(expiredJwtService.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("Sinh chuỗi Refresh Token ngẫu nhiên độ dài bảo mật cao")
    void generateRefreshToken_ProducesSecureRandomString() {
        String token1 = jwtService.generateRefreshToken();
        String token2 = jwtService.generateRefreshToken();

        assertThat(token1).isNotBlank().hasSizeGreaterThanOrEqualTo(60);
        assertThat(token2).isNotBlank();
        assertThat(token1).isNotEqualTo(token2);
    }

    @Test
    @DisplayName("Băm SHA-256 Refresh Token cho kết quả xác định và chuẩn 64 ký tự hex")
    void hashToken_ProducesConsistent64CharHex() {
        String rawToken = "my-secure-refresh-token-123456";
        String hash1 = JwtService.hashToken(rawToken);
        String hash2 = JwtService.hashToken(rawToken);

        assertThat(hash1).isNotBlank().hasSize(64);
        assertThat(hash1).isEqualTo(hash2); // Băm xác định cùng đầu vào ra cùng mã hash

        String differentHash = JwtService.hashToken("different-token");
        assertThat(hash1).isNotEqualTo(differentHash);
    }

    @Test
    @DisplayName("Băm token rỗng -> ném IllegalArgumentException")
    void hashToken_BlankToken_ThrowsException() {
        assertThatThrownBy(() -> JwtService.hashToken(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
