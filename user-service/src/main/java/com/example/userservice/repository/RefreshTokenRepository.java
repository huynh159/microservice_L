package com.example.userservice.repository;

import com.example.userservice.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ==============================================================================
 * REPOSITORY QUẢN LÝ REFRESH TOKEN
 * ------------------------------------------------------------------------------
 * Cung cấp các thao tác truy vấn và cập nhật trạng thái Refresh Token trong DB.
 * ==============================================================================
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Tìm kiếm bản ghi Refresh Token theo chuỗi băm SHA-256.
     * Sử dụng khi client gửi Refresh Token lên endpoint /api/v1/auth/refresh hoặc /logout.
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Tìm tất cả các Refresh Token còn hoạt động (chưa bị thu hồi) của một người dùng.
     */
    List<RefreshToken> findAllByUser_IdAndRevokedAtIsNull(UUID userId);

    /**
     * Thu hồi toàn bộ Refresh Token đang hoạt động của người dùng.
     * Cực kỳ quan trọng: Gọi khi người dùng đổi mật khẩu hoặc bị quản trị viên khóa tài khoản,
     * buộc tất cả các phiên đăng nhập khác của người dùng đó phải đăng xuất ngay lập tức.
     */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = CURRENT_TIMESTAMP WHERE r.user.id = :userId AND r.revokedAt IS NULL")
    void revokeAllByUserId(@Param("userId") UUID userId);
}
