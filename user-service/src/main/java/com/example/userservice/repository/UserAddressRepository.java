package com.example.userservice.repository;

import com.example.userservice.entity.UserAddress;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserAddressRepository extends JpaRepository<UserAddress, UUID> {

    // ==============================================================
    // 1. DERIVED QUERY (Spring tự dịch tên hàm thành câu SQL)
    // ==============================================================

    /**
     * Lấy toàn bộ danh sách địa chỉ của một user theo userId.
     * SQL tự sinh: SELECT * FROM user_addresses WHERE user_id = ?
     */
    List<UserAddress> findByUserId(UUID userId);

    /**
     * Tìm địa chỉ đang được đặt làm mặc định của một user.
     * SQL tự sinh: SELECT * FROM user_addresses WHERE user_id = ? AND is_default = true
     */
    Optional<UserAddress> findByUserIdAndIsDefaultTrue(UUID userId);

    /**
     * Kiểm tra xem user đã có địa chỉ mặc định nào chưa.
     * SQL tự sinh: SELECT count(*) > 0 FROM user_addresses WHERE user_id = ? AND is_default = true
     */
    boolean existsByUserIdAndIsDefaultTrue(UUID userId);

    /**
     * Đếm tổng số địa chỉ mà user đang có.
     * SQL tự sinh: SELECT count(*) FROM user_addresses WHERE user_id = ?
     */
    long countByUserId(UUID userId);

    /**
     * Lấy danh sách địa chỉ của User theo danh sách thành phố có phân trang.
     */
    Page<UserAddress> findByUserIdAndCityIn(UUID userId, List<String> cities, Pageable pageable);


    // ==============================================================
    // 2. JPQL QUERY (Dùng cú pháp Java Object Query)
    // ==============================================================

    /**
     * Tìm danh sách địa chỉ theo thành phố của một user (không phân biệt hoa thường).
     */
    @Query("SELECT a FROM UserAddress a " +
           "WHERE a.user.id = :userId " +
           "AND LOWER(a.city) = LOWER(:city) " +
           "ORDER BY a.createdAt DESC")
    List<UserAddress> findAddressesByCity(
            @Param("userId") UUID userId,
            @Param("city") String city);

    /**
     * Thống kê số lượng địa chỉ theo từng thành phố (GROUP BY).
     */
    @Query("SELECT a.city, COUNT(a) FROM UserAddress a GROUP BY a.city")
    List<Object[]> countAddressesByCity();


    // ==============================================================
    // 3. MODIFYING QUERY (Cập nhật / Xóa trực tiếp)
    // ==============================================================

    /**
     * Đặt tất cả địa chỉ của user về isDefault = false.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserAddress a SET a.isDefault = false WHERE a.user.id = :userId")
    int resetDefaultAddresses(@Param("userId") UUID userId);
}
