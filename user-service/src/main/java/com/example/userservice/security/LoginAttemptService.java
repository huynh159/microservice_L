package com.example.userservice.security;

/**
 * ==============================================================================
 * GIAO DIỆN CHỐNG BRUTE-FORCE ĐĂNG NHẬP (LOGIN ATTEMPT SERVICE INTERFACE)
 * ------------------------------------------------------------------------------
 * Tuân thủ quy tắc kiến trúc tách biệt Interface và Implementation:
 * - Giúp AuthService chỉ phụ thuộc vào hợp đồng trừu tượng (Loose Coupling).
 * - Dễ dàng mock độc lập trong Unit Test mà không phụ thuộc vào tầng hạ tầng mạng.
 * ==============================================================================
 */
public interface LoginAttemptService {

    /**
     * Kiểm tra xem địa chỉ email có đang bị tạm khóa do nhập sai quá 5 lần không
     */
    boolean isBlocked(String email);

    /**
     * Ghi nhận một lần nhập sai mật khẩu (tăng bộ đếm và đặt thời hạn 15 phút)
     */
    void recordFailedAttempt(String email);

    /**
     * Xóa bộ đếm số lần nhập sai khi người dùng đăng nhập thành công
     */
    void resetAttempts(String email);
}
