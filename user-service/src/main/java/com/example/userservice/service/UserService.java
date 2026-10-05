package com.example.userservice.service;

import com.example.common.dto.response.PageResponse;
import com.example.userservice.dto.request.ChangePasswordRequest;
import com.example.userservice.dto.request.UserRegistrationRequest;
import com.example.userservice.dto.request.UserStatusUpdateRequest;
import com.example.userservice.dto.request.UserUpdateRequest;
import com.example.userservice.dto.response.UserProfileResponse;
import com.example.userservice.entity.UserStatus;

import java.util.UUID;

/**
 * ==============================================================================
 * KIẾN THỨC SERVICE LAYER TRONG MICROSERVICES:
 * ------------------------------------------------------------------------------
 * 1. Interface-based programming: Controller chỉ giao tiếp thông qua Interface
 *    này, không phụ thuộc trực tiếp vào UserServiceImpl. Điều này giúp:
 *    - Dễ dàng thay thế / mock khi viết Unit Test.
 *    - Tách biệt hoàn toàn tầng nhận request (Controller) và tầng nghiệp vụ (Service).
 * 2. Mọi phương thức ở tầng Service đều nhận vào DTO và trả ra DTO (hoặc void).
 *    Tuyệt đối không để rò rỉ Entity ra ngoài tầng Service.
 * ==============================================================================
 */
public interface UserService {

    /**
     * Đăng ký người dùng mới:
     * - Kiểm tra email trùng lặp (không phân biệt hoa thường).
     * - Mã hóa mật khẩu bằng thuật toán BCrypt.
     * - Gán vai trò mặc định ROLE_USER.
     * - Lưu vào database và trả về DTO thông tin user (không kèm mật khẩu).
     */
    UserProfileResponse register(UserRegistrationRequest request);

    /**
     * Lấy thông tin tài khoản của chính mình (Profile cá nhân).
     */
    UserProfileResponse getProfile(UUID userId);

    /**
     * Cập nhật thông tin tài khoản (Họ tên, số điện thoại, avatar).
     */
    UserProfileResponse updateProfile(UUID userId, UserUpdateRequest request);

    /**
     * Đổi mật khẩu:
     * - Xác thực mật khẩu cũ có khớp với mã băm trong DB hay không.
     * - Kiểm tra mật khẩu mới không được trùng mật khẩu cũ.
     * - Mã hóa mật khẩu mới và lưu lại.
     */
    void changePassword(UUID userId, ChangePasswordRequest request);

    /**
     * Quản trị viên (ADMIN): Lấy chi tiết thông tin một người dùng theo ID.
     */
    UserProfileResponse getUserById(UUID userId);

    /**
     * Quản trị viên (ADMIN): Tìm kiếm danh sách người dùng có phân trang và sắp xếp.
     * @param keyword Từ khóa tìm kiếm (email, họ tên hoặc số điện thoại)
     * @param status Lọc theo trạng thái tài khoản (ACTIVE, LOCKED, DELETED)
     * @param page Số thứ tự trang (bắt đầu từ 0)
     * @param size Số lượng bản ghi mỗi trang
     * @param sort Cột và chiều sắp xếp (ví dụ: createdAt,desc)
     */
    PageResponse<UserProfileResponse> searchUsers(String keyword, UserStatus status, int page, int size, String sort);

    /**
     * Quản trị viên (ADMIN): Khóa hoặc mở khóa tài khoản (cập nhật status).
     */
    UserProfileResponse updateStatus(UUID userId, UserStatusUpdateRequest request);

    /**
     * Quản trị viên (ADMIN): Xóa mềm người dùng (đặt status = DELETED).
     * Không xóa hẳn bản ghi khỏi database để giữ toàn vẹn dữ liệu lịch sử.
     */
    void deleteUser(UUID userId);
}
