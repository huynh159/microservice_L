package com.example.userservice.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * ==============================================================================
 * DỊCH VỤ XỬ LÝ JSON WEB TOKEN (JWT SERVICE) - BƯỚC 3.5
 * ------------------------------------------------------------------------------
 * 1. Khái niệm JWT (RFC 7519):
 *    - JWT là một chuỗi ký tự được mã hóa URL-safe bao gồm 3 phần ngăn cách bởi dấu chấm (.):
 *      Header.Payload.Signature
 *      + Header: Chứa thuật toán mã hóa (ví dụ HS256) và kiểu token (JWT).
 *      + Payload: Chứa các Claims (thông tin định danh như sub, email, roles, exp, iat, jti).
 *      + Signature: Chữ ký số tạo từ Header + Payload + Khóa bí mật (Secret Key).
 *
 * 2. Ưu điểm trong Kiến trúc Microservices (Stateless Authentication):
 *    - Service không cần lưu trữ Access Token trong bộ nhớ hay cơ sở dữ liệu.
 *    - Khi client gửi request kèm Bearer Token trong header Authorization, service chỉ
 *      cần giải mã chữ ký bằng Secret Key là ngay lập tức xác thực được ai đang gọi API.
 *
 * 3. Chuẩn Claims theo tài liệu Kế hoạch Mục 4.1:
 *    - sub (Subject): UUID của người dùng.
 *    - email: Địa chỉ email của người dùng.
 *    - roles: Danh sách vai trò quyền hạn (ROLE_USER, ROLE_ADMIN).
 *    - iat (Issued At): Thời điểm phát hành token.
 *    - exp (Expiration): Thời điểm hết hạn token (15 phút).
 *    - jti (JWT ID): Mã định danh duy nhất của token (chống tấn công Replay Attack).
 *
 * 4. Cơ chế băm SHA-256 cho Refresh Token:
 *    - Refresh Token thô gửi về cho client, còn trong Database chỉ lưu giá trị băm SHA-256.
 * ==============================================================================
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey signingKey;
    private final long expirationMs;
    private final long refreshExpirationMs;

    public JwtService(
            @Value("${jwt.secret:super_secret_jwt_key_that_is_at_least_256_bits_long_for_security_reasons}") String secret,
            @Value("${jwt.expiration-ms:900000}") long expirationMs,
            @Value("${jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs
    ) {
        // Thuật toán HMAC-SHA256 yêu cầu khóa bí mật (Secret Key) có độ dài tối thiểu 256 bits (32 bytes).
        // Nếu chuỗi cấu hình ngắn hơn 32 bytes, tự động đệm (pad) thêm byte 0 để tránh lỗi WeakKeyException khi khởi động.
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            this.signingKey = Keys.hmacShaKeyFor(padded);
        } else {
            this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        }
        this.expirationMs = expirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    /**
     * TẠO ACCESS TOKEN (JWT) NGẮN HẠN:
     * - Hạn dùng mặc định: 15 phút (900.000 ms).
     * - Đóng gói các Claims thiết yếu:
     *   + sub (Subject): UUID của người dùng.
     *   + email: Địa chỉ email.
     *   + roles: Danh sách vai trò quyền hạn (ví dụ ["ROLE_USER"]).
     *   + jti (JWT ID): Chuỗi UUID ngẫu nhiên để chống tấn công phát lại (Replay Attack).
     *   + iat & exp: Thời gian phát hành và thời gian hết hạn.
     * - Ký số bằng khóa bí mật (signingKey) với thuật toán HMAC-SHA256.
     */
    public String generateAccessToken(UUID userId, String email, Set<String> roles) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);
        String jti = UUID.randomUUID().toString(); // Mã định danh duy nhất cho từng token

        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("roles", roles != null ? new ArrayList<>(roles) : Collections.emptyList())
                .id(jti)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey) // Ký số toàn vẹn dữ liệu
                .compact();           // Đóng gói thành chuỗi Base64URL: Header.Payload.Signature
    }

    /**
     * SINH REFRESH TOKEN NGẪU NHIÊN:
     * - Ghép 2 chuỗi UUID ngẫu nhiên và bỏ dấu gạch nối để tạo chuỗi Hex dài 64 ký tự.
     * - Độ hỗn loạn (Entropy) cực cao, không thể đoán mò hay tấn công vét cạn.
     * - Chuỗi thô này được gửi về cho Client giữ an toàn.
     */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * BĂM CHUỖI REFRESH TOKEN BẰNG THUẬT TOÁN SHA-256 ĐỂ LƯU DATABASE:
     * - Tại sao dùng SHA-256?
     *   1. Tốc độ tra cứu nhanh: Client liên tục gọi refresh token, SHA-256 giúp DB tìm theo index cực nhanh.
     *   2. Bảo mật: Token là chuỗi ngẫu nhiên 64 ký tự hex nên không sợ bị vét cạn như mật khẩu ngắn.
     *   3. Chống rò rỉ: Nếu Database bị lộ, hacker chỉ thấy chuỗi băm chứ không thể lấy chuỗi này gọi API được.
     */
    public static String hashToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Token không được để trống khi băm");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Lỗi hệ thống: không tìm thấy thuật toán băm SHA-256", e);
        }
    }

    /**
     * Giải mã và xác minh toàn vẹn chữ ký của JWT Access Token
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Trích xuất User ID (UUID) từ claim "sub" của JWT
     */
    public UUID extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        return UUID.fromString(claims.getSubject());
    }

    /**
     * Trích xuất Email từ claim "email" của JWT
     */
    public String extractEmail(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("email", String.class);
    }

    /**
     * Trích xuất danh sách vai trò từ claim "roles" của JWT
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

    /**
     * Kiểm tra tính hợp lệ của Token (chữ ký đúng và chưa hết hạn)
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT không hợp lệ hoặc đã hết hạn: {}", e.getMessage());
            return false;
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }
}
