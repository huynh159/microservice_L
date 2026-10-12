package com.example.chatservice.security;

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
 * THỰC THỂ ĐỊNH DANH NGƯỜI DÙNG TRONG CHAT SERVICE (USER PRINCIPAL)
 * ------------------------------------------------------------------------------
 * Trong kiến trúc Stateless Microservice, Chat Service không lưu bảng users.
 * Đối tượng này được tạo trực tiếp từ CLAIMS giải mã được trong JWT Token:
 * - id: UUID của người dùng (từ claim "sub")
 * - email: Email của người dùng (từ claim "email")
 * - authorities: Danh sách vai trò (ROLE_USER, ROLE_ADMIN) (từ claim "roles")
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
        return null; // Stateless JWT không giữ password
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

    public boolean isAdmin() {
        return authorities.stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
