package com.example.userservice.controller;

import com.example.common.dto.response.PageResponse;
import com.example.common.exception.BadRequestException;
import com.example.common.exception.ConflictException;
import com.example.common.exception.ResourceNotFoundException;
import com.example.userservice.config.SecurityConfig;
import com.example.userservice.dto.request.ChangePasswordRequest;
import com.example.userservice.dto.request.UserRegistrationRequest;
import com.example.userservice.dto.request.UserStatusUpdateRequest;
import com.example.userservice.dto.request.UserUpdateRequest;
import com.example.userservice.dto.response.UserProfileResponse;
import com.example.userservice.entity.UserStatus;
import com.example.userservice.exception.GlobalExceptionHandler;
import com.example.userservice.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ==============================================================================
 * KIẾN THỨC VỀ KIỂM THỬ TẦNG WEB CONTROLLER VỚI @WebMvcTest:
 * ------------------------------------------------------------------------------
 * 1. @WebMvcTest(UserController.class):
 *    - Chỉ nạp các Bean liên quan đến tầng Web (Controller, Filter, Advice, Jackson),
 *      KHÔNG nạp toàn bộ ứng dụng, KHÔNG kết nối cơ sở dữ liệu thật hay Kafka.
 *    - Tốc độ chạy rất nhanh, giúp tập trung kiểm thử:
 *      + Routing URL (Đường dẫn có khớp không?)
 *      + HTTP Method (GET, POST, PUT, DELETE, PATCH)
 *      + Bean Validation (@Valid có bắt đúng lỗi không?)
 *      + HTTP Status Code (200, 201, 204, 400, 404, 409)
 *      + Định dạng JSON đầu ra (JSON structure & serialization)
 *
 * 2. MockMvc:
 *    - Đối tượng giả lập các cuộc gọi HTTP Request (GET/POST/...) mà không cần phải
 *      bật một Web Server thực thụ (như Tomcat).
 *
 * 3. @MockitoBean (hoặc @MockBean):
 *    - Tiêm mock UserService vào Spring Web Application Context.
 * ==============================================================================
 */
@WebMvcTest(UserController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        com.example.userservice.security.JwtAuthenticationFilter.class,
        com.example.userservice.security.JwtService.class,
        com.example.userservice.security.CustomAuthenticationEntryPoint.class,
        com.example.userservice.security.CustomAccessDeniedHandler.class
})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    private static final String USER_ID_STR = "11111111-1111-1111-1111-111111111111";
    private UUID sampleUserId;
    private UserProfileResponse sampleProfile;

    @BeforeEach
    void setUp() {
        sampleUserId = UUID.fromString(USER_ID_STR);
        sampleProfile = UserProfileResponse.builder()
                .id(sampleUserId)
                .email("test@example.com")
                .fullName("Nguyen Van A")
                .phone("0987654321")
                .avatarUrl("https://example.com/avatar.jpg")
                .status(UserStatus.ACTIVE)
                .roles(Set.of("ROLE_USER"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // =========================================================================
    // 1. KIỂM THỬ ĐĂNG KÝ (POST /api/v1/users)
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/users: Đăng ký thành công trả về HTTP 201 Created kèm header Location")
    void register_Success() throws Exception {
        UserRegistrationRequest request = new UserRegistrationRequest(
                "newuser@example.com",
                "SecurePassword123@",
                "Nguyen Van New",
                "0912345678"
        );

        when(userService.register(any(UserRegistrationRequest.class))).thenReturn(sampleProfile);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/users/" + sampleUserId)))
                .andExpect(jsonPath("$.id").value(sampleUserId.toString()))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.fullName").value("Nguyen Van A"));
    }

    @Test
    @DisplayName("POST /api/v1/users: Lỗi validation (email sai, mật khẩu ngắn) trả về HTTP 400 Bad Request")
    void register_ValidationFailed() throws Exception {
        // Email không hợp lệ, password dưới 8 ký tự, fullName để trống
        UserRegistrationRequest invalidRequest = new UserRegistrationRequest(
                "invalid-email",
                "short",
                "",
                "123"
        );

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("POST /api/v1/users: Email đã tồn tại trả về HTTP 409 Conflict")
    void register_EmailConflict() throws Exception {
        UserRegistrationRequest request = new UserRegistrationRequest(
                "exist@example.com",
                "SecurePassword123@",
                "Nguyen Van Exist",
                null
        );

        when(userService.register(any(UserRegistrationRequest.class)))
                .thenThrow(new ConflictException("USER_EMAIL_EXISTS", "Email 'exist@example.com' đã được sử dụng"));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("USER_EMAIL_EXISTS"))
                .andExpect(jsonPath("$.message").value(containsString("đã được sử dụng")));
    }

    // =========================================================================
    // 2. KIỂM THỬ XEM / SỬA PROFILE BẢN THÂN (/api/v1/users/me)
    // =========================================================================

    @Test
    @WithMockUser(username = USER_ID_STR, roles = "USER")
    @DisplayName("GET /api/v1/users/me: Thành công khi đã xác thực trả về HTTP 200 OK")
    void getCurrentUserProfile_Success() throws Exception {
        when(userService.getProfile(sampleUserId)).thenReturn(sampleProfile);

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sampleUserId.toString()))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    @DisplayName("GET /api/v1/users/me: Thiếu thông tin định danh trả về HTTP 401 Unauthorized")
    void getCurrentUserProfile_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(username = USER_ID_STR, roles = "USER")
    @DisplayName("PUT /api/v1/users/me: Cập nhật profile thành công trả về HTTP 200 OK")
    void updateCurrentUserProfile_Success() throws Exception {
        UserUpdateRequest updateRequest = new UserUpdateRequest("Nguyen Van Updated", "0999888777", "https://img.com/a.png");

        when(userService.updateProfile(eq(sampleUserId), any(UserUpdateRequest.class))).thenReturn(sampleProfile);

        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sampleUserId.toString()));
    }

    // =========================================================================
    // 3. KIỂM THỬ ĐỔI MẬT KHẨU (PUT /api/v1/users/me/password)
    // =========================================================================

    @Test
    @WithMockUser(username = USER_ID_STR, roles = "USER")
    @DisplayName("PUT /api/v1/users/me/password: Đổi mật khẩu thành công trả về HTTP 204 No Content")
    void changePassword_Success() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("OldPassword123@", "NewPassword456@");

        doNothing().when(userService).changePassword(eq(sampleUserId), any(ChangePasswordRequest.class));

        mockMvc.perform(put("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = USER_ID_STR, roles = "USER")
    @DisplayName("PUT /api/v1/users/me/password: Mật khẩu cũ sai trả về HTTP 400 Bad Request")
    void changePassword_WrongOldPassword() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("WrongPassword123@", "NewPassword456@");

        doThrow(new BadRequestException("INVALID_OLD_PASSWORD", "Mật khẩu cũ không chính xác"))
                .when(userService).changePassword(eq(sampleUserId), any(ChangePasswordRequest.class));

        mockMvc.perform(put("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_OLD_PASSWORD"));
    }

    // =========================================================================
    // 4. KIỂM THỬ ADMIN: TÌM KIẾM, CHI TIẾT, STATUS, XÓA MỀM & PHÂN QUYỀN RBAC
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/users: Không có Token/chưa xác thực trả về HTTP 401 Unauthorized")
    void searchUsers_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(username = USER_ID_STR, roles = "USER")
    @DisplayName("GET /api/v1/users: USER thường gọi API của ADMIN trả về HTTP 403 Forbidden")
    void searchUsers_ForbiddenForUserRole_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/users: Tìm kiếm người dùng phân trang trả về HTTP 200 OK với PageResponse")
    void searchUsers_Success() throws Exception {
        PageResponse<UserProfileResponse> pageResponse = PageResponse.<UserProfileResponse>builder()
                .content(List.of(sampleProfile))
                .page(0)
                .size(20)
                .totalElements(1L)
                .totalPages(1)
                .build();

        when(userService.searchUsers(any(), any(), eq(0), eq(20), anyString())).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/users")
                        .param("page", "0")
                        .param("size", "20")
                        .param("keyword", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].email").value("test@example.com"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/users/{id}: Lấy thông tin user theo ID thành công")
    void getUserById_Success() throws Exception {
        when(userService.getUserById(sampleUserId)).thenReturn(sampleProfile);

        mockMvc.perform(get("/api/v1/users/{id}", sampleUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sampleUserId.toString()))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/users/{id}: Không tìm thấy user trả về HTTP 404 Not Found")
    void getUserById_NotFound() throws Exception {
        when(userService.getUserById(sampleUserId))
                .thenThrow(new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy người dùng"));

        mockMvc.perform(get("/api/v1/users/{id}", sampleUserId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PATCH /api/v1/users/{id}/status: Cập nhật trạng thái người dùng thành công")
    void updateStatus_Success() throws Exception {
        UserStatusUpdateRequest request = new UserStatusUpdateRequest(UserStatus.LOCKED);

        UserProfileResponse lockedProfile = UserProfileResponse.builder()
                .id(sampleUserId)
                .email("test@example.com")
                .fullName("Nguyen Van A")
                .status(UserStatus.LOCKED)
                .roles(Set.of("ROLE_USER"))
                .build();

        when(userService.updateStatus(eq(sampleUserId), any(UserStatusUpdateRequest.class)))
                .thenReturn(lockedProfile);

        mockMvc.perform(patch("/api/v1/users/{id}/status", sampleUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("DELETE /api/v1/users/{id}: Xóa mềm người dùng trả về HTTP 204 No Content")
    void deleteUser_Success() throws Exception {
        doNothing().when(userService).deleteUser(sampleUserId);

        mockMvc.perform(delete("/api/v1/users/{id}", sampleUserId))
                .andExpect(status().isNoContent());

        verify(userService, times(1)).deleteUser(sampleUserId);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/users/{id}: Truyền chuỗi không phải UUID vào path trả về HTTP 400 TYPE_MISMATCH")
    void getUserById_TypeMismatch() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", "not-a-valid-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("TYPE_MISMATCH"));
    }
}
