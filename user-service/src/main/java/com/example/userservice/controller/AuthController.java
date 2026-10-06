package com.example.userservice.controller;

import com.example.userservice.dto.request.LoginRequest;
import com.example.userservice.dto.request.LogoutRequest;
import com.example.userservice.dto.request.RefreshTokenRequest;
import com.example.userservice.dto.response.AuthResponse;
import com.example.userservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * ==============================================================================
 * CONTROLLER XÁC THỰC (AUTH CONTROLLER) - BƯỚC 3.5
 * ------------------------------------------------------------------------------
 * Tiếp nhận các yêu cầu xác thực người dùng:
 * 1. POST /api/v1/auth/login: Đăng nhập cấp cặp token Access & Refresh.
 * 2. POST /api/v1/auth/refresh: Đổi Refresh Token lấy cặp token mới (Token Rotation).
 * 3. POST /api/v1/auth/logout: Đăng xuất và thu hồi Refresh Token (HTTP 204).
 * ==============================================================================
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication Management", description = "Các API xác thực tài khoản: Đăng nhập, Làm mới token và Đăng xuất")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * ĐĂNG NHẬP HỆ THỐNG (Public)
     */
    @PostMapping("/login")
    @Operation(summary = "Đăng nhập hệ thống (Public)",
            description = "Xác thực email và mật khẩu. Trả về Access Token (15 phút) và Refresh Token (7 ngày).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đăng nhập thành công, trả về cặp token"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu gửi lên không đúng định dạng"),
            @ApiResponse(responseCode = "401", description = "Email hoặc mật khẩu không chính xác"),
            @ApiResponse(responseCode = "423", description = "Tài khoản người dùng đã bị khóa (LOCKED)"),
            @ApiResponse(responseCode = "429", description = "Đăng nhập sai quá 5 lần, tạm thời bị từ chối")
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * LÀM MỚI TOKEN (Public / Rotation)
     */
    @PostMapping("/refresh")
    @Operation(summary = "Làm mới Access Token (Token Rotation)",
            description = "Gửi Refresh Token để nhận lại cặp Access Token và Refresh Token mới. Token cũ sẽ bị thu hồi ngay lập tức.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Làm mới token thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu yêu cầu thiếu refresh token"),
            @ApiResponse(responseCode = "401", description = "Refresh token không hợp lệ, đã hết hạn hoặc đã bị thu hồi"),
            @ApiResponse(responseCode = "423", description = "Tài khoản người dùng đã bị khóa")
    })
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }

    /**
     * ĐĂNG XUẤT (USER / Authenticated)
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Đăng xuất tài khoản và thu hồi Refresh Token",
            description = "Thu hồi Refresh Token được chỉ định trong cơ sở dữ liệu để vô hiệu hóa phiên làm việc.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đăng xuất thành công (Không có nội dung trả về)"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực")
    })
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
}
