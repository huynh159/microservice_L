package com.example.userservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import java.util.UUID;

/**
 * DTO trả về kết quả xác thực thành công gồm Access Token và Refresh Token
 */
@Schema(description = "Kết quả xác thực chứa cặp token JWT và thông tin tài khoản")
public class AuthResponse {

    @Schema(description = "Access Token (JWT) dùng để xác thực các API nghiệp vụ (Hạn 15 phút)")
    private String accessToken;

    @Schema(description = "Refresh Token dùng để đổi cặp token mới (Hạn 7 ngày)")
    private String refreshToken;

    @Schema(description = "Loại token", example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "Thời gian hết hạn của Access Token tính theo giây (15 phút = 900 giây)", example = "900")
    private long expiresIn;

    @Schema(description = "UUID của người dùng")
    private UUID userId;

    @Schema(description = "Email của người dùng", example = "user@example.com")
    private String email;

    @Schema(description = "Danh sách vai trò quyền hạn của người dùng", example = "[\"ROLE_USER\"]")
    private Set<String> roles;

    public AuthResponse() {
    }

    public AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresIn, UUID userId, String email, Set<String> roles) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
        this.userId = userId;
        this.email = email;
        this.roles = roles;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String accessToken;
        private String refreshToken;
        private String tokenType = "Bearer";
        private long expiresIn = 900;
        private UUID userId;
        private String email;
        private Set<String> roles;

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder refreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
            return this;
        }

        public Builder tokenType(String tokenType) {
            this.tokenType = tokenType;
            return this;
        }

        public Builder expiresIn(long expiresIn) {
            this.expiresIn = expiresIn;
            return this;
        }

        public Builder userId(UUID userId) {
            this.userId = userId;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder roles(Set<String> roles) {
            this.roles = roles;
            return this;
        }

        public AuthResponse build() {
            return new AuthResponse(accessToken, refreshToken, tokenType, expiresIn, userId, email, roles);
        }
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }
}
