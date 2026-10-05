package com.example.userservice.controller;

import com.example.common.dto.response.PageResponse;
import com.example.common.exception.UnauthorizedException;
import com.example.userservice.dto.request.ChangePasswordRequest;
import com.example.userservice.dto.request.UserRegistrationRequest;
import com.example.userservice.dto.request.UserStatusUpdateRequest;
import com.example.userservice.dto.request.UserUpdateRequest;
import com.example.userservice.dto.response.UserProfileResponse;
import com.example.userservice.entity.UserStatus;
import com.example.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * ==============================================================================
 * KIẾN THỨC CỐT LÕI VỀ REST CONTROLLER TRONG SPRING BOOT:
 * ------------------------------------------------------------------------------
 * 1. @RestController = @Controller + @ResponseBody:
 *    - Tự động chuyển đổi các đối tượng trả về (DTO, List, Map) thành định dạng JSON.
 * 2. @RequestMapping("/api/v1/users"):
 *    - Định nghĩa đường dẫn gốc (Base Path) tuân theo chuẩn RESTful API versioning (/api/v1).
 * 3. Mã phản hồi HTTP (HTTP Status Codes) chuẩn quốc tế:
 *    - 200 OK: Truy vấn thành công hoặc cập nhật thành công có trả về dữ liệu.
 *    - 201 Created: Tạo mới thành công (kèm header Location trỏ tới URL tài nguyên mới).
 *    - 204 No Content: Thao tác thành công nhưng không cần trả về dữ liệu trong body (Đổi mật khẩu, Xóa).
 *    - 400 Bad Request: Dữ liệu gửi lên sai định dạng hoặc vi phạm ràng buộc validation.
 *    - 401 Unauthorized: Chưa xác thực (chưa đăng nhập hoặc token không hợp lệ).
 *    - 403 Forbidden: Đã đăng nhập nhưng không đủ quyền hạn (ví dụ User thường gọi API của Admin).
 *    - 404 Not Found: Không tìm thấy tài nguyên theo ID yêu cầu.
 *    - 409 Conflict: Xung đột tài nguyên (ví dụ email đã tồn tại trong hệ thống).
 * 4. @Valid: Kích hoạt Bean Validation kiểm tra các ràng buộc (@NotBlank, @Email, @Size)
 *    đã khai báo trong DTO Request trước khi chạy vào ruột hàm!
 * ==============================================================================
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Management", description = "Các API đăng ký, xem/sửa hồ sơ cá nhân và quản trị tài khoản người dùng")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * ĐĂNG KÝ NGƯỜI DÙNG MỚI (Public)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Đăng ký tài khoản người dùng mới (Public)",
            description = "Tạo mới tài khoản người dùng với email, mật khẩu và họ tên. Vai trò mặc định là ROLE_USER.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đăng ký tài khoản thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu đăng ký không hợp lệ (sai định dạng email, mật khẩu yếu)"),
            @ApiResponse(responseCode = "409", description = "Email đã được sử dụng bởi người dùng khác")
    })
    public ResponseEntity<UserProfileResponse> register(
            @Valid @RequestBody UserRegistrationRequest request
    ) {
        UserProfileResponse response = userService.register(request);

        // Chuẩn RESTful: Trả về HTTP 201 Created kèm Header Location trỏ tới /api/v1/users/{id}
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    /**
     * XEM HỒ SƠ CỦA BẢN THÂN (Yêu cầu đăng nhập)
     */
    @GetMapping("/me")
    @Operation(summary = "Xem thông tin hồ sơ của chính mình (Current User)",
            description = "Lấy thông tin người dùng đang đăng nhập thông qua Authentication Context hoặc Header X-User-Id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy hồ sơ thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực hoặc token không hợp lệ"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng")
    })
    public ResponseEntity<UserProfileResponse> getCurrentUserProfile(
            Authentication authentication,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId
    ) {
        UUID currentUserId = resolveUserId(authentication, headerUserId);
        UserProfileResponse profile = userService.getProfile(currentUserId);
        return ResponseEntity.ok(profile);
    }

    /**
     * CẬP NHẬT HỒ SƠ CỦA BẢN THÂN (Yêu cầu đăng nhập)
     */
    @PutMapping("/me")
    @Operation(summary = "Cập nhật thông tin hồ sơ cá nhân",
            description = "Cho phép người dùng sửa đổi họ tên, số điện thoại hoặc ảnh đại diện (avatar).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật hồ sơ thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu gửi lên không hợp lệ"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực")
    })
    public ResponseEntity<UserProfileResponse> updateCurrentUserProfile(
            Authentication authentication,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @Valid @RequestBody UserUpdateRequest request
    ) {
        UUID currentUserId = resolveUserId(authentication, headerUserId);
        UserProfileResponse updatedProfile = userService.updateProfile(currentUserId, request);
        return ResponseEntity.ok(updatedProfile);
    }

    /**
     * ĐỔI MẬT KHẨU (Yêu cầu đăng nhập)
     */
    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Đổi mật khẩu tài khoản cá nhân",
            description = "Xác minh mật khẩu cũ và đổi sang mật khẩu mới. Trả về HTTP 204 No Content.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đổi mật khẩu thành công"),
            @ApiResponse(responseCode = "400", description = "Mật khẩu cũ không đúng hoặc mật khẩu mới trùng mật khẩu cũ"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực")
    })
    public ResponseEntity<Void> changePassword(
            Authentication authentication,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        UUID currentUserId = resolveUserId(authentication, headerUserId);
        userService.changePassword(currentUserId, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * QUẢN TRỊ VIÊN: TÌM KIẾM DANH SÁCH NGƯỜI DÙNG CÓ PHÂN TRANG (ADMIN)
     */
    @GetMapping
    @Operation(summary = "Tìm kiếm danh sách người dùng có phân trang (ADMIN)",
            description = "Hỗ trợ lọc theo từ khóa (email, họ tên, sđt), theo trạng thái tài khoản, phân trang và sắp xếp.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy danh sách người dùng thành công")
    })
    public ResponseEntity<PageResponse<UserProfileResponse>> searchUsers(
            @Parameter(description = "Từ khóa tìm kiếm theo họ tên, email hoặc số điện thoại")
            @RequestParam(name = "keyword", required = false) String keyword,

            @Parameter(description = "Lọc theo trạng thái người dùng (ACTIVE, LOCKED, DELETED)")
            @RequestParam(name = "status", required = false) UserStatus status,

            @Parameter(description = "Chỉ số trang (bắt đầu từ 0)", example = "0")
            @RequestParam(name = "page", defaultValue = "0") int page,

            @Parameter(description = "Số lượng bản ghi trên một trang", example = "20")
            @RequestParam(name = "size", defaultValue = "20") int size,

            @Parameter(description = "Trường sắp xếp và thứ tự sắp xếp (ví dụ: createdAt,desc hoặc fullName,asc)")
            @RequestParam(name = "sort", defaultValue = "createdAt,desc") String sort
    ) {
        PageResponse<UserProfileResponse> result = userService.searchUsers(keyword, status, page, size, sort);
        return ResponseEntity.ok(result);
    }

    /**
     * QUẢN TRỊ VIÊN: XEM CHI TIẾT NGƯỜI DÙNG THEO ID (ADMIN)
     */
    @GetMapping("/{id}")
    @Operation(summary = "Xem thông tin chi tiết người dùng theo ID (ADMIN)",
            description = "Lấy thông tin tài khoản người dùng dựa trên định danh UUID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy thông tin thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng với ID tương ứng")
    })
    public ResponseEntity<UserProfileResponse> getUserById(
            @Parameter(description = "Định danh UUID của người dùng cần tra cứu")
            @PathVariable("id") UUID id
    ) {
        UserProfileResponse profile = userService.getUserById(id);
        return ResponseEntity.ok(profile);
    }

    /**
     * QUẢN TRỊ VIÊN: KHÓA / MỞ KHÓA TÀI KHOẢN (ADMIN)
     */
    @PatchMapping("/{id}/status")
    @Operation(summary = "Cập nhật trạng thái tài khoản người dùng (Khóa/Mở khóa) (ADMIN)",
            description = "Cho phép quản trị viên chuyển đổi trạng thái người dùng (ACTIVE / LOCKED / DELETED).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật trạng thái thành công"),
            @ApiResponse(responseCode = "400", description = "Trạng thái gửi lên không hợp lệ"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng")
    })
    public ResponseEntity<UserProfileResponse> updateStatus(
            @Parameter(description = "UUID của người dùng cần thay đổi trạng thái")
            @PathVariable("id") UUID id,
            @Valid @RequestBody UserStatusUpdateRequest request
    ) {
        UserProfileResponse updated = userService.updateStatus(id, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * QUẢN TRỊ VIÊN: XÓA MỀM NGƯỜI DÙNG (ADMIN)
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xóa mềm người dùng (Soft Delete) (ADMIN)",
            description = "Chuyển trạng thái người dùng thành DELETED để vô hiệu hóa tài khoản mà vẫn bảo toàn tính toàn vẹn dữ liệu.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Xóa người dùng thành công (không có nội dung phản hồi)"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng")
    })
    public ResponseEntity<Void> deleteUser(
            @Parameter(description = "UUID của người dùng cần xóa mềm")
            @PathVariable("id") UUID id
    ) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // HÀM TIỆN ÍCH TRÍCH XUẤT USER ID CỦA NGƯỜI DÙNG HIỆN TẠI
    // =========================================================================
    private UUID resolveUserId(Authentication authentication, String headerUserId) {
        // 1. Kiểm tra từ Spring Security Context (nếu đã xác thực qua JWT ở Bước 3.5)
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            try {
                return UUID.fromString(authentication.getName());
            } catch (IllegalArgumentException ignored) {
                // Tên principal không phải là chuỗi UUID chuẩn, tiếp tục kiểm tra header
            }
        }

        // 2. Kiểm tra từ Header X-User-Id (hữu ích khi test API hoặc chạy qua Gateway)
        if (headerUserId != null && !headerUserId.isBlank()) {
            try {
                return UUID.fromString(headerUserId.trim());
            } catch (IllegalArgumentException ex) {
                throw new UnauthorizedException("INVALID_USER_ID_HEADER", "Header X-User-Id không đúng định dạng UUID");
            }
        }

        // 3. Nếu không có bất kỳ thông tin nhận diện nào, ném lỗi 401 Unauthorized
        throw new UnauthorizedException("UNAUTHORIZED", "Yêu cầu đăng nhập để thực hiện thao tác này");
    }
}
