package com.example.userservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO nhận Refresh Token cần thu hồi khi người dùng đăng xuất
 */
@Schema(description = "Yêu cầu đăng xuất tài khoản và thu hồi Refresh Token")
public class LogoutRequest {

    @NotBlank(message = "Refresh token không được để trống")
    @Schema(description = "Refresh token cần thu hồi để kết thúc phiên làm việc", example = "d9f8e7d6-c5b4-4a3b-8a1e-2f3a4b5c6d7e")
    private String refreshToken;

    public LogoutRequest() {
    }

    public LogoutRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
