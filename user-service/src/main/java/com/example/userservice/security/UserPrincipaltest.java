package com.example.userservice.security;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class UserPrincipaltest implements UserDetails {
    private final UUID id;
    private final String email;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipaltest(UUID id, String email, Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.authorities = authorities != null ? authorities : Collections.emptyList();

    }

    /**
     * Hàm factory tạo nhanh user principal từ claims của token jwt:
     * - Nhận vào User Id, email và set các chuỗi role ( ví dụ: )
     */
    public static UserPrincipal create(UUID id, String email, Set<String> roles) {
        Collection<GrantedAuthority> authorities = roles == null ? Collections.emptyList()
                : roles.stream()
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

    /**
     * Trả về định danh người dùng: Ở đây trả về UUID dạng String (hoặc Email nếu id
     * null)
     * Giúp khi gọi authentication.getName() sẽ nhận được chính xác User ID.
     */
    @Override
    public String getUsername() {
        return id != null ? id.toString() : email;
    }

    /**
     * Trong kiến trúc JWT Stateless, password hash không được lưu trong Principal
     * trên RAM
     * nhằm tăng cường bảo mật và tiết kiệm tài nguyên.
     */
    @Override
    public String getPassword() {
        return null;
    }

    /**
     * Trả về danh sách vai trò / quyền hạn để Spring Security dùng khi kiểm
     * tra @PreAuthorize / hasRole()
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
