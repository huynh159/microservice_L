package com.example.userservice.repository;

import com.example.userservice.entity.User;
import com.example.userservice.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("SELECT u FROM User u WHERE " +
            "(:keyword IS NULL OR :keyword = '' OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.phone) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
            "(:status IS NULL OR u.status = :status)")
    Page<User> searchUsers(
            @Param("keyword") String keyword,
            @Param("status") UserStatus status,
            Pageable pageable);

    // Tìm User theo Status và tạo sau một thời điểm
    List<User> findByStatusAndCreatedAtAfter(UserStatus status, Instant fromDate);

    // Tìm User theo số điện thoại bắt đầu bằng
    List<User> findByPhoneStartingWith(String prefix);

    // Kiểm tra số điện thoại tồn tại
    boolean existsByPhone(String phone);

    // Tìm User theo tên Role (quan hệ Many-to-Many)
    List<User> findByRoles_Name(String roleName);

    // Tìm User theo tên Role bằng JPQL
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = :roleName")
    List<User> findUsersByRoleName(@Param("roleName") String roleName);

    // Tìm kiếm theo họ tên chính xác
    List<User> findByFullName(String fullName);

    // Tìm User có trạng thái khác (Not)
    List<User> findByStatusNot(UserStatus status);

    // Tìm theo Email HOẶC Số điện thoại
    Optional<User> findByEmailOrPhone(String email, String phone);

    // Tìm theo họ tên chứa từ khóa (Containing, không phân biệt hoa thường)
    List<User> findByFullNameContainingIgnoreCase(String keyword);

    // Tìm email kết thúc bằng (EndingWith)
    List<User> findByEmailEndingWith(String domain);

    // Tìm User chưa có Avatar (IsNull)
    List<User> findByAvatarUrlIsNull();

    // Tìm User có Avatar (IsNotNull)
    List<User> findByAvatarUrlIsNotNull();

    // Lấy User mới tạo gần đây nhất
    Optional<User> findFirstByOrderByCreatedAtDesc();

    // Lấy Top 5 User ACTIVE sắp xếp theo tên từ A-Z
    List<User> findTop5ByStatusOrderByFullNameAsc(UserStatus status);

    // Tìm User tạo trong khoảng thời gian (Between)
    List<User> findByCreatedAtBetween(Instant startDate, Instant endDate);
}
