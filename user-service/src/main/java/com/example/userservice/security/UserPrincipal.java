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

    public static UserPrincipal create(UUID id, String email, Set<String> roles) {
        Collection<GrantedAuthority> authorities = roles == null ? Collections.emptyList() :
                roles.stream()
                        .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
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

    @Override
    public String getUsername() {
        return id != null ? id.toString() : email;
    }

    @Override
    public String getPassword() {
        return null; // Không lưu password hash trong context của JWT stateless
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
