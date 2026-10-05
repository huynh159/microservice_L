package com.example.userservice.service.impl;

import com.example.common.dto.response.PageResponse;
import com.example.common.exception.BadRequestException;
import com.example.common.exception.ConflictException;
import com.example.common.exception.ResourceNotFoundException;
import com.example.userservice.dto.request.ChangePasswordRequest;
import com.example.userservice.dto.request.UserRegistrationRequest;
import com.example.userservice.dto.request.UserStatusUpdateRequest;
import com.example.userservice.dto.request.UserUpdateRequest;
import com.example.userservice.dto.response.UserProfileResponse;
import com.example.userservice.entity.Role;
import com.example.userservice.entity.User;
import com.example.userservice.entity.UserStatus;
import com.example.userservice.mapper.UserMapper;
import com.example.userservice.repository.RoleRepository;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * ==============================================================================
 * KIẾN THỨC CỐT LÕI VỀ SERVICE LAYER & DATABASE TRANSACTION:
 * ------------------------------------------------------------------------------
 * 1. @Service: Đánh dấu class này là Spring Bean thuộc tầng nghiệp vụ (Business Logic).
 * 2. @Transactional(readOnly = true) ở cấp Class:
 *    - Báo cho Hibernate biết các hàm chỉ đọc dữ liệu (SELECT), không sửa đổi.
 *    - Hibernate sẽ TẮT tính năng Dirty Checking (so sánh đối tượng xem có đổi không),
 *      giúp TIẾT KIỆM BỘ NHỚ và TĂNG TỐC ĐỘ truy vấn đáng kể!
 * 3. @Transactional ở từng hàm ghi (Ghi/Sửa/Xóa):
 *    - Đảm bảo tính toàn vẹn ACID.
 *    - Nếu có lỗi xảy ra giữa chừng (ví dụ ném RuntimeException), Spring sẽ tự động
 *      ROLLBACK (hoàn tác) toàn bộ câu lệnh SQL đã chạy trước đó, không để lại dữ liệu rác.
 * 4. Constructor Injection (Tiêm phụ thuộc qua Hàm khởi tạo):
 *    - Khuyên dùng thay vì @Autowired trên field vì giúp class không bị dính chặt vào
 *      Spring container, dễ dàng truyền mock object vào khi viết Unit Test.
 * ==============================================================================
 */
@Service
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserServiceImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            UserMapper userMapper
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    /**
     * ĐĂNG KÝ TÀI KHOẢN MỚI
     */
    @Override
    @Transactional // Bật transaction để ghi dữ liệu (INSERT)
    public UserProfileResponse register(UserRegistrationRequest request) {
        // KIẾN THỨC 1: Chuẩn hóa email về chữ thường để tránh tình trạng "User@gmail.com" và "user@gmail.com"
        // được xem là 2 tài khoản khác nhau.
        String normalizedEmail = request.getEmail().toLowerCase().trim();

        // KIẾN THỨC 2: Kiểm tra tính duy nhất (Unique) trước khi lưu.
        // Tránh ném lỗi DataIntegrityViolationException từ DB mà chủ động bắt và ném ConflictException (HTTP 409).
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("USER_EMAIL_EXISTS", "Email '" + normalizedEmail + "' đã được sử dụng");
        }

        // KIẾN THỨC 3: Mã hóa mật khẩu bằng thuật toán BCrypt.
        // BCrypt tự động sinh Salt ngẫu nhiên mỗi lần băm (Hashing), chống tấn công Rainbow Table.
        // Tuyệt đối KHÔNG BAO GIỜ lưu mật khẩu dưới dạng thô (Plain Text) vào DB!
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // Lấy vai trò mặc định ROLE_USER từ bảng roles (đã seed ở Migration V1)
        Role defaultRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_USER")));

        // Khởi tạo Entity User
        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(encodedPassword);
        user.setFullName(request.getFullName().trim());
        user.setPhone(request.getPhone() != null ? request.getPhone().trim() : null);
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(defaultRole));

        // Lưu vào DB (lúc này @PrePersist trong User Entity sẽ tự tạo UUID nếu chưa có)
        User savedUser = userRepository.save(user);

        // Chuyển sang DTO an toàn thông qua UserMapper và trả về
        return userMapper.toProfileResponse(savedUser);
    }

    /**
     * XEM THÔNG TIN CÁ NHÂN (PROFILE)
     */
    @Override
    public UserProfileResponse getProfile(UUID userId) {
        User user = findUserByIdOrThrow(userId);
        return userMapper.toProfileResponse(user);
    }

    /**
     * CẬP NHẬT THÔNG TIN CÁ NHÂN (Họ tên, SĐT, Avatar)
     */
    @Override
    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UserUpdateRequest request) {
        User user = findUserByIdOrThrow(userId);

        // Cập nhật các trường được phép sửa
        user.setFullName(request.getFullName().trim());
        user.setPhone(request.getPhone() != null ? request.getPhone().trim() : null);
        user.setAvatarUrl(request.getAvatarUrl() != null ? request.getAvatarUrl().trim() : null);

        // Lưu lại (JPA dirty checking hoặc gọi save rõ ràng)
        User updatedUser = userRepository.save(user);
        return userMapper.toProfileResponse(updatedUser);
    }

    /**
     * ĐỔI MẬT KHẨU
     */
    @Override
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = findUserByIdOrThrow(userId);

        // KIẾN THỨC 4: Cách so khớp mật khẩu BCrypt:
        // Do BCrypt có salt ngẫu nhiên nên mỗi lần encode cùng 1 chuỗi ký tự sẽ ra kết quả khác nhau.
        // Vì vậy KHÔNG DÙNG equals(), mà bắt buộc phải dùng passwordEncoder.matches(mật_khẩu_gõ_vào, hash_trong_db).
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BadRequestException("INVALID_OLD_PASSWORD", "Mật khẩu cũ không chính xác");
        }

        // Kiểm tra mật khẩu mới không được giống mật khẩu cũ
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new BadRequestException("PASSWORD_SAME_AS_OLD", "Mật khẩu mới không được trùng với mật khẩu cũ");
        }

        // Mã hóa mật khẩu mới và cập nhật
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    /**
     * ADMIN: LẤY THÔNG TIN USER THEO ID
     */
    @Override
    public UserProfileResponse getUserById(UUID userId) {
        User user = findUserByIdOrThrow(userId);
        return userMapper.toProfileResponse(user);
    }

    /**
     * ADMIN: TÌM KIẾM NGƯỜI DÙNG PHÂN TRANG
     */
    @Override
    public PageResponse<UserProfileResponse> searchUsers(
            String keyword,
            UserStatus status,
            int page,
            int size,
            String sort
    ) {
        // KIẾN THỨC 5: Phân trang và Sắp xếp trong Spring Data (Pageable):
        // Giúp tránh việc truy vấn toàn bộ hàng triệu bản ghi cùng lúc làm tràn bộ nhớ (OutOfMemory).
        Sort sortObj = parseSortParameter(sort);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), sortObj);

        // Thực hiện tìm kiếm trong DB
        Page<User> userPage = userRepository.searchUsers(keyword, status, pageable);

        // Chuyển đổi danh sách Entity trong page thành danh sách DTO
        List<UserProfileResponse> dtoList = userPage.getContent()
                .stream()
                .map(userMapper::toProfileResponse)
                .toList();

        // Đóng gói vào PageResponse chuẩn đã định nghĩa trong common-lib
        return PageResponse.<UserProfileResponse>builder()
                .content(dtoList)
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .build();
    }

    /**
     * ADMIN: KHÓA / MỞ KHÓA TÀI KHOẢN (Cập nhật trạng thái)
     */
    @Override
    @Transactional
    public UserProfileResponse updateStatus(UUID userId, UserStatusUpdateRequest request) {
        User user = findUserByIdOrThrow(userId);
        user.setStatus(request.getStatus());

        User updated = userRepository.save(user);
        return userMapper.toProfileResponse(updated);
    }

    /**
     * ADMIN: XÓA MỀM (Soft Delete)
     * KIẾN THỨC 6: Tại sao trong Microservices nên dùng Xóa Mềm thay vì Xóa Cứng (Hard Delete)?
     * - Nếu thực hiện "DELETE FROM users WHERE id = ...", các service khác như Order, Payment,
     *   Notification đang lưu userId này sẽ bị mất tham chiếu lịch sử (mồ côi dữ liệu).
     * - Xóa mềm chỉ đổi trạng thái status = DELETED. Người dùng không thể đăng nhập được nữa,
     *   nhưng dữ liệu cũ và tính toàn vẹn hệ thống vẫn được bảo tồn phục vụ báo cáo / kiểm toán.
     */
    @Override
    @Transactional
    public void deleteUser(UUID userId) {
        User user = findUserByIdOrThrow(userId);
        user.setStatus(UserStatus.DELETED);
        userRepository.save(user);
    }

    // =========================================================================
    // HÀM TIỆN ÍCH DÙNG NỘI BỘ (Private Helper Methods)
    // =========================================================================

    private User findUserByIdOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "USER_NOT_FOUND",
                        "Không tìm thấy người dùng với ID: " + userId
                ));
    }

    /**
     * Phân tích chuỗi sort dạng "createdAt,desc" hoặc "fullName,asc"
     */
    private Sort parseSortParameter(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = sort.split(",");
        String property = parts[0].trim();
        Sort.Direction direction = (parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc"))
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }
}
