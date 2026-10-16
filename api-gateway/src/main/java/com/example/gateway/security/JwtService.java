package com.example.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ==============================================================================
 * DỊCH VỤ XÁC THỰC VÀ GIẢI MÃ JWT TẠI API GATEWAY (EDGE VALIDATION)
 * ==============================================================================
 * TƯ DUY KIẾN TRÚC:
 * 1. Tại sao Gateway cần giải mã JWT?
 *    - Chặn đứng các request giả mạo, token hết hạn ngay từ "cửa khẩu" (Edge).
 *    - Giảm tải CPU cho các service phía sau: Backend services không cần lãng phí
 *      luồng xử lý (thread pool) cho các request không hợp lệ.
 * 2. Header Enrichment (Làm giàu Header):
 *    - Gateway trích xuất các thông tin quan trọng từ JWT (User ID, Email, Roles).
 *    - Đính kèm trực tiếp vào Request Header gửi tiếp cho downstream service:
 *      + X-User-Id: <UUID>
 *      + X-User-Email: <email>
 *      + X-User-Roles: <ROLE_USER,ROLE_ADMIN>
 *    - Giúp downstream service có thể đọc thông tin người dùng ngay lập tức từ header
 *      mà không bắt buộc phải giải mã lại JWT (hoặc vẫn giải mã nếu áp dụng Zero-Trust).
 * 3. Đồng bộ thuật toán ký số:
 *    - Sử dụng cùng thuật toán HMAC-SHA256 và Secret Key dùng chung với user-service.
 * ==============================================================================
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey signingKey;

    public JwtService(
            @Value("${jwt.secret:super_secret_jwt_key_that_is_at_least_256_bits_long_for_security_reasons}") String secret
    ) {
        // Thuật toán HMAC-SHA256 yêu cầu khóa bí mật (Secret Key) có độ dài tối thiểu 256 bits (32 bytes).
        // Tự động đệm byte 0 nếu chuỗi cấu hình ngắn hơn để tương thích hoàn toàn với user-service.
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            this.signingKey = Keys.hmacShaKeyFor(padded);
        } else {
            this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        }
    }

    /**
     * Xác minh tính hợp lệ của Token:
     * - Chữ ký số đúng (nguyên vẹn, không bị chỉnh sửa).
     * - Token chưa hết hạn (exp > thời điểm hiện tại).
     *
     * @param token Chuỗi Access Token nhận từ Client
     * @return true nếu hợp lệ, false nếu hết hạn hoặc sai chữ ký
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT không hợp lệ hoặc đã hết hạn tại Gateway: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Giải mã toàn bộ Claims từ Token đã được xác minh tính toàn vẹn
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Trích xuất User ID từ Claim 'sub' (Subject)
     */
    public String extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        return claims.getSubject();
    }

    /**
     * Trích xuất Email từ Claim 'email'
     */
    public String extractEmail(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("email", String.class);
    }

    /**
     * Trích xuất danh sách vai trò người dùng từ Claim 'roles'
     */
    @SuppressWarnings("unchecked")
    public Set<String> extractRoles(String token) {
        Claims claims = extractAllClaims(token);
        List<String> rolesList = claims.get("roles", List.class);
        if (rolesList == null) {
            return Collections.emptySet();
        }
        return new HashSet<>(rolesList);
    }
}
