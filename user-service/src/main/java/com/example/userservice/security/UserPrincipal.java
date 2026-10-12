package com.example.userservice.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ==============================================================================
 * USER PRINCIPAL - ĐẠI DIỆN DANH TÍNH NGƯỜI DÙNG ĐÃ XÁC THỰC TRONG SPRING SECURITY
 * ------------------------------------------------------------------------------
 * 1. UserPrincipal là gì?
 *    - Trong Spring Security, sau khi xác thực thành công từ JWT, danh tính của người
 *      dùng được lưu vào SecurityContextHolder dưới dạng đối tượng Authentication.
 *    - UserPrincipal triển khai giao diện UserDetails của Spring Security, đóng gói:
 *      + Định danh người dùng (UUID id)
 *      + Email người dùng (email)
 *      + Danh sách quyền hạn / vai trò (Collection<GrantedAuthority> như ROLE_USER, ROLE_ADMIN)
 *
 * 2. Ưu điểm thiết kế:
 *    - Có thể inject trực tiếp @AuthenticationPrincipal UserPrincipal vào bất kỳ
 *      phương thức Controller nào để lấy ID và Email của người đang đăng nhập cực kỳ tiện lợi.
 *    - authentication.getName() trả về chuỗi UUID của người dùng, tương thích hoàn toàn
 *      với logic trích xuất User ID của hệ thống.
 * ==============================================================================
 */
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String email;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(UUID id, String email, Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.authorities = authorities != null ? authorities : Collections.emptyList();
    }

    /**
     * HÀM FACTORY TẠO NHANH USER PRINCIPAL TỪ CLAIMS CỦA TOKEN JWT:
     * - Nhận vào User ID, Email và Set các chuỗi role (ví dụ: ["ROLE_USER", "ROLE_ADMIN"] hoặc ["USER", "ADMIN"]).
     * - Chuẩn hóa đảm bảo mọi role đều có tiền tố "ROLE_" theo đúng chuẩn Spring Security quy định.
     * - Chuyển đổi mỗi chuỗi role thành một đối tượng SimpleGrantedAuthority.
     */
    public static UserPrincipal create(UUID id, String email, Set<String> roles) {
        Collection<GrantedAuthority> authorities = roles == null ? Collections.emptyList() :
                roles.stream()
                        // Đảm bảo tên vai trò luôn bắt đầu bằng "ROLE_" để tương thích với hasRole()
                        .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                        // Bọc thành đối tượng SimpleGrantedAuthority của Spring Security
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toSet());
        return new UserPrincipal(id, email, authorities);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    /**
     * Trả về định danh người dùng: Ở đây trả về UUID dạng String (hoặc Email nếu id null)
     * Giúp khi gọi authentication.getName() sẽ nhận được chính xác User ID.
     */
    @Override
    public String getUsername() {
        return id != null ? id.toString() : email;
    }

    /**
     * Trong kiến trúc JWT Stateless, password hash không được lưu trong Principal trên RAM
     * nhằm tăng cường bảo mật và tiết kiệm tài nguyên.
     */
    @Override
    public String getPassword() {
        return null;
    }

    /**
     * Trả về danh sách vai trò / quyền hạn để Spring Security dùng khi kiểm tra @PreAuthorize / hasRole()
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // Tài khoản không bị hết hạn
    }

    @Override
    public boolean isAccountNonLocked() {
        return true; // Trạng thái khóa được kiểm tra chủ động lúc đăng nhập / gọi service
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // Mật khẩu không hết hạn
    }

    @Override
    public boolean isEnabled() {
        return true; // Tài khoản đang được kích hoạt
    }
}
