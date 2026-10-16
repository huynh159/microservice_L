package com.example.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Kiểm thử Dịch vụ JWT tại Gateway (JwtServiceTest)")
class JwtServiceTest {

    private static final String SECRET = "this_is_a_very_secure_and_long_secret_key_for_testing_purposes_only_32_bytes";
    private JwtService jwtService;
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET);
        signingKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private String createTestToken(String subject, String email, List<String> roles, long expirationOffsetMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationOffsetMs);
        return Jwts.builder()
                .subject(subject)
                .claim("email", email)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    @Test
    @DisplayName("Xác thực token hợp lệ và giải mã đúng Claims (userId, email, roles)")
    void shouldValidateAndExtractClaimsSuccessfully() {
        String userId = UUID.randomUUID().toString();
        String email = "alice@example.com";
        List<String> roles = List.of("ROLE_USER", "ROLE_ADMIN");

        String token = createTestToken(userId, email, roles, 60000); // Còn hạn 1 phút

        assertThat(jwtService.validateToken(token)).isTrue();
        assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
        assertThat(jwtService.extractEmail(token)).isEqualTo(email);
        assertThat(jwtService.extractRoles(token)).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("Từ chối token đã hết hạn")
    void shouldRejectExpiredToken() {
        String token = createTestToken("123", "expired@example.com", List.of("ROLE_USER"), -1000); // Hết hạn 1 giây trước

        assertThat(jwtService.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("Từ chối token bị can thiệp sai chữ ký")
    void shouldRejectTamperedToken() {
        String validToken = createTestToken("123", "alice@example.com", List.of("ROLE_USER"), 60000);
        String tamperedToken = validToken.substring(0, validToken.length() - 5) + "abcde";

        assertThat(jwtService.validateToken(tamperedToken)).isFalse();
    }
}
