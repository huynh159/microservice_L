package com.example.userservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO nhận Refresh Token để xin cấp Access Token mới
 */
@Schema(description = "Yêu cầu làm mới Access Token bằng Refresh Token")
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token không được để trống")
    @Schema(description = "Refresh token hợp lệ đã nhận từ phiên đăng nhập", example = "d9f8e7d6-c5b4-4a3b-8a1e-2f3a4b5c6d7e")
    private String refreshToken;

    public RefreshTokenRequest() {
    }

    public RefreshTokenRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
