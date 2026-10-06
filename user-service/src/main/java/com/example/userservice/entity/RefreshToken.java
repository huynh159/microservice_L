package com.example.userservice.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * ==============================================================================
 * THỰC THỂ REFRESH TOKEN (REFRESH TOKEN ENTITY) - BƯỚC 3.5
 * ------------------------------------------------------------------------------
 * 1. Vai trò của Refresh Token trong hệ thống xác thực Stateless:
 *    - Access Token (JWT) có hạn rất ngắn (15 phút) để nếu bị lộ cũng nhanh hết hạn.
 *    - Refresh Token có hạn dài (7 ngày) dùng để xin cấp Access Token mới mà không
 *      bắt người dùng phải nhập lại tài khoản/mật khẩu liên tục.
 *
 * 2. Kiến trúc bảo mật tối cao - Lưu HASH thay vì token thô:
 *    - Nguyên tắc Zero-Knowledge: Giống như mật khẩu, Refresh Token thô KHÔNG BAO GIỜ
 *      được lưu dạng rõ (plain-text) trong cơ sở dữ liệu.
 *    - Ta chỉ lưu chuỗi băm SHA-256 (token_hash). Nếu Database bị rò rỉ, kẻ tấn công
 *      không thể dùng giá trị băm này để tạo ra Refresh Token hợp lệ!
 *
 * 3. Cơ chế Xoay vòng Refresh Token (Refresh Token Rotation):
 *    - Mỗi khi client dùng refresh token để lấy token mới, token cũ lập tức bị thu hồi
 *      (đặt revokedAt = now()) và tạo ra token mới.
 *    - Trường replacedBy ghi nhận id/hash của token thay thế, giúp phát hiện hành vi
 *      tái sử dụng token đã thu hồi (dấu hiệu tài khoản bị chiếm đoạt).
 * ==============================================================================
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_tokens_hash", columnList = "token_hash"),
        @Index(name = "idx_refresh_tokens_user_id", columnList = "user_id")
})
public class RefreshToken {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "replaced_by", length = 255)
    private String replacedBy;

    public RefreshToken() {
    }

    public RefreshToken(User user, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * Kiểm tra token đã hết hạn hay chưa
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Kiểm tra token đã bị thu hồi (revoked) hay chưa
     */
    public boolean isRevoked() {
        return revokedAt != null;
    }

    /**
     * Kiểm tra token còn hiệu lực hay không (chưa bị thu hồi và chưa hết hạn)
     */
    public boolean isActive() {
        return !isRevoked() && !isExpired();
    }

    /**
     * Thu hồi token này (dùng khi logout hoặc khi xoay vòng token)
     */
    public void revoke(String replacedByTokenHash) {
        this.revokedAt = Instant.now();
        this.replacedBy = replacedByTokenHash;
    }

    // =========================================================================
    // GETTERS & SETTERS
    // =========================================================================

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getReplacedBy() {
        return replacedBy;
    }

    public void setReplacedBy(String replacedBy) {
        this.replacedBy = replacedBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RefreshToken that = (RefreshToken) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
