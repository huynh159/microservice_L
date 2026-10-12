package com.example.chatservice.security;

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
import java.util.*;

/**
 * ==============================================================================
 * DỊCH VỤ XÁC THỰC JWT TẠI RESOURCE SERVER (CHAT-SERVICE)
 * ------------------------------------------------------------------------------
 * KIẾN THỨC DOANH NGHIỆP:
 * 1. user-service là Identity Provider (nơi cấp phát token).
 * 2. chat-service là Resource Server (nơi tiêu thụ và xác thực token).
 * 3. chat-service KHÔNG CẦN gọi HTTP sang user-service để kiểm tra token!
 *    Nó sử dụng cùng khóa bí mật jwt.secret để kiểm tra chữ ký số HMAC-SHA256.
 *    Nếu chữ ký khớp và token chưa hết hạn -> Token 100% tin cậy (Stateless).
 * ==============================================================================
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey signingKey;

    public JwtService(@Value("${jwt.secret}") String secret) {
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
     * Rút trích tất cả Claims từ chuỗi JWT Token sau khi đã xác minh chữ ký toàn vẹn
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Lấy ID người dùng (UUID) từ Subject claim ("sub")
     */
    public UUID extractUserId(String token) {
        String sub = extractAllClaims(token).getSubject();
        return UUID.fromString(sub);
    }

    /**
     * Lấy email từ claim "email"
     */
    public String extractEmail(String token) {
        Claims claims = extractAllClaims(token);
        String email = claims.get("email", String.class);
        return email != null ? email : claims.getSubject();
    }

    /**
     * Lấy danh sách Roles từ claim "roles"
     */
    @SuppressWarnings("unchecked")
    public Set<String> extractRoles(String token) {
        Claims claims = extractAllClaims(token);
        Object rolesObj = claims.get("roles");
        if (rolesObj instanceof List<?> list) {
            Set<String> roles = new HashSet<>();
            for (Object item : list) {
                if (item != null) {
                    roles.add(item.toString());
                }
            }
            return roles;
        }
        return Collections.emptySet();
    }

    /**
     * Kiểm tra tính hợp lệ của Token (chữ ký đúng và chưa hết hạn)
     */
    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            Date expiration = claims.getExpiration();
            return expiration != null && expiration.after(new Date());
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("JWT token không hợp lệ hoặc đã hết hạn: {}", ex.getMessage());
            return false;
        }
    }
}
