package com.example.userservice.controller;

import com.example.common.exception.BusinessException;
import com.example.common.exception.UnauthorizedException;
import com.example.userservice.config.SecurityConfig;
import com.example.userservice.dto.request.LoginRequest;
import com.example.userservice.dto.request.LogoutRequest;
import com.example.userservice.dto.request.RefreshTokenRequest;
import com.example.userservice.dto.response.AuthResponse;
import com.example.userservice.exception.GlobalExceptionHandler;
import com.example.userservice.security.CustomAccessDeniedHandler;
import com.example.userservice.security.CustomAuthenticationEntryPoint;
import com.example.userservice.security.JwtAuthenticationFilter;
import com.example.userservice.security.JwtService;
import com.example.userservice.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web Slice Test cho AuthController (Kiểm tra routing, HTTP code và serialize JSON cho Auth API)
 */
@WebMvcTest(AuthController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        JwtAuthenticationFilter.class,
        JwtService.class,
        CustomAuthenticationEntryPoint.class,
        CustomAccessDeniedHandler.class
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    private AuthResponse sampleAuthResponse;

    @BeforeEach
    void setUp() {
        sampleAuthResponse = AuthResponse.builder()
                .accessToken("sample.access.token")
                .refreshToken("sample-raw-refresh-token")
                .tokenType("Bearer")
                .expiresIn(900)
                .userId(UUID.randomUUID())
                .email("user@example.com")
                .roles(Set.of("ROLE_USER"))
                .build();
    }

    // =========================================================================
    // 1. KIỂM THỬ ĐĂNG NHẬP (POST /api/v1/auth/login)
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/auth/login: Đăng nhập thành công trả về HTTP 200 OK kèm cặp token")
    void login_Success() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "Password123@");

        when(authService.login(any(LoginRequest.class))).thenReturn(sampleAuthResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("sample.access.token"))
                .andExpect(jsonPath("$.refreshToken").value("sample-raw-refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login: Sai email/mật khẩu trả về HTTP 401 Unauthorized")
    void login_BadCredentials_Returns401() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "WrongPassword");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new UnauthorizedException("BAD_CREDENTIALS", "Email hoặc mật khẩu không chính xác"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login: Tài khoản bị khóa trả về HTTP 423 Locked")
    void login_AccountLocked_Returns423() throws Exception {
        LoginRequest request = new LoginRequest("locked@example.com", "Password123@");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BusinessException(HttpStatus.LOCKED.value(), "ACCOUNT_LOCKED", "Tài khoản của bạn đã bị khóa"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.status").value(423))
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login: Bị chặn brute-force trả về HTTP 429 Too Many Requests")
    void login_BruteForceBlocked_Returns429() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "Password123@");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BusinessException(HttpStatus.TOO_MANY_REQUESTS.value(), "TOO_MANY_REQUESTS", "Đăng nhập sai quá 5 lần"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login: Dữ liệu gửi lên để trống trả về HTTP 400 Bad Request")
    void login_ValidationFailed_Returns400() throws Exception {
        LoginRequest request = new LoginRequest("invalid-email", "");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // =========================================================================
    // 2. KIỂM THỬ LÀM MỚI TOKEN (POST /api/v1/auth/refresh)
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/auth/refresh: Làm mới token thành công trả về HTTP 200 OK")
    void refresh_Success() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");

        when(authService.refresh(any(RefreshTokenRequest.class))).thenReturn(sampleAuthResponse);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("sample.access.token"))
                .andExpect(jsonPath("$.refreshToken").value("sample-raw-refresh-token"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh: Token không hợp lệ/đã bị thu hồi trả về HTTP 401 Unauthorized")
    void refresh_InvalidToken_Returns401() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("revoked-token");

        when(authService.refresh(any(RefreshTokenRequest.class)))
                .thenThrow(new UnauthorizedException("REVOKED_REFRESH_TOKEN", "Refresh token này đã bị thu hồi"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("REVOKED_REFRESH_TOKEN"));
    }

    // =========================================================================
    // 3. KIỂM THỬ ĐĂNG XUẤT (POST /api/v1/auth/logout)
    // =========================================================================

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("POST /api/v1/auth/logout: Đăng xuất thành công trả về HTTP 204 No Content")
    void logout_Success() throws Exception {
        LogoutRequest request = new LogoutRequest("some-refresh-token");
        doNothing().when(authService).logout(any(LogoutRequest.class));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(authService).logout(any(LogoutRequest.class));
    }
}
