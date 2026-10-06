package com.example.userservice.service;

import com.example.userservice.dto.request.LoginRequest;
import com.example.userservice.dto.request.LogoutRequest;
import com.example.userservice.dto.request.RefreshTokenRequest;
import com.example.userservice.dto.response.AuthResponse;

/**
 * ==============================================================================
 * DỊCH VỤ XÁC THỰC NGƯỜI DÙNG (AUTH SERVICE INTERFACE) - BƯỚC 3.5
 * ------------------------------------------------------------------------------
 * Cung cấp hợp đồng nghiệp vụ cho:
 * 1. Đăng nhập (login): Cấp phát cặp Access Token và Refresh Token.
 * 2. Làm mới Token (refresh): Cơ chế xoay vòng Refresh Token (Token Rotation).
 * 3. Đăng xuất (logout): Thu hồi Refresh Token để kết thúc phiên làm việc.
 * ==============================================================================
 */
public interface AuthService {

    /**
     * Xác thực thông tin đăng nhập và cấp cặp token mới
     *
     * @param request chứa email và mật khẩu
     * @return AuthResponse chứa access token (15p) và refresh token (7 ngày)
     */
    AuthResponse login(LoginRequest request);

    /**
     * Đổi Refresh Token cũ lấy cặp Access Token + Refresh Token mới (Xoay vòng token)
     *
     * @param request chứa refresh token thô
     * @return AuthResponse cặp token mới
     */
    AuthResponse refresh(RefreshTokenRequest request);

    /**
     * Thu hồi Refresh Token khi người dùng đăng xuất
     *
     * @param request chứa refresh token cần thu hồi
     */
    void logout(LogoutRequest request);
}
