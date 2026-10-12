package com.example.userservice.config;

import com.example.userservice.security.CustomAccessDeniedHandler;
import com.example.userservice.security.CustomAuthenticationEntryPoint;
import com.example.userservice.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * ==============================================================================
 * CẤU HÌNH BẢO MẬT HỆ THỐNG (SPRING SECURITY CONFIGURATION) - BƯỚC 3.5
 * ------------------------------------------------------------------------------
 * 1. Kiến trúc Stateless (Không lưu Session):
 *    - SessionCreationPolicy.STATELESS: Máy chủ không lưu trữ HttpSession trên RAM.
 *    - Mọi request được chứng thực độc lập thông qua chuỗi JSON Web Token (JWT)
 *      được gửi trong Header "Authorization: Bearer <token>".
 *
 * 2. Phân vùng quyền truy cập (Role-Based Access Control):
 *    - Public: Đăng ký tài khoản (POST /api/v1/users), Đăng nhập (/auth/login),
 *      Làm mới token (/auth/refresh), Swagger UI & Actuator Health check.
 *    - USER / Authenticated: Xem & sửa hồ sơ cá nhân (/users/me), Đổi mật khẩu (/users/me/password),
 *      Đăng xuất (/auth/logout).
 *    - ADMIN: Tra cứu danh sách người dùng (/users), Xem chi tiết user (/users/{id}),
 *      Khóa/mở khóa (/users/{id}/status), Xóa mềm user (DELETE /users/{id}).
 *
 * 3. Xử lý lỗi bảo mật tập trung:
 *    - CustomAuthenticationEntryPoint: Trả về HTTP 401 chuẩn JSON ErrorResponse khi thiếu/sai token.
 *    - CustomAccessDeniedHandler: Trả về HTTP 403 chuẩn JSON ErrorResponse khi thiếu quyền (ví dụ User gọi API Admin).
 * ==============================================================================
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    // Bộ lọc kiểm tra và giải mã token JWT trong mỗi request gửi tới
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    
    // Bộ xử lý lỗi trả về HTTP 401 khi người dùng chưa đăng nhập hoặc token sai/hết hạn
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    
    // Bộ xử lý lỗi trả về HTTP 403 khi người dùng đã đăng nhập nhưng không đủ quyền (ví dụ User gọi API Admin)
    private final CustomAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            CustomAuthenticationEntryPoint authenticationEntryPoint,
            CustomAccessDeniedHandler accessDeniedHandler
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    /**
     * BEAN MÃ HÓA MẬT KHẨU (PASSWORD ENCODER):
     * - Sử dụng thuật toán BCrypt để băm mật khẩu một chiều trước khi lưu vào Database.
     * - BCrypt tự động sinh Salt ngẫu nhiên và có Work Factor để làm chậm quá trình băm,
     *   giúp chống lại các hình thức tấn công Rainbow Table và Brute-force vét cạn.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * BEAN QUẢN LÝ XÁC THỰC (AUTHENTICATION MANAGER):
     * - Cung cấp AuthenticationManager mặc định của Spring Security.
     * - Dùng để xác thực thông tin đăng nhập khi cần kiểm tra username/password tập trung.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * CẤU HÌNH CHUỖI BỘ LỌC BẢO MẬT (SECURITY FILTER CHAIN):
     * Định nghĩa các quy tắc bảo mật chính cho toàn bộ HTTP request gửi đến hệ thống:
     * 1. Tắt CSRF (vì kiến trúc RESTful API dùng JWT Stateless, không dùng Session Cookie).
     * 2. Đặt Session sang STATELESS (không tạo HttpSession lưu trên RAM server).
     * 3. Đăng ký các bộ xử lý ngoại lệ (401 Unauthorized, 403 Forbidden).
     * 4. Phân vùng quyền truy cập cho từng URL pattern (Public, User, Admin).
     * 5. Chèn bộ lọc JwtAuthenticationFilter trước UsernamePasswordAuthenticationFilter.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Tắt bảo vệ CSRF vì API sử dụng JWT trong Authorization Header, không dùng Cookie Session nên không bị tấn công CSRF
                .csrf(AbstractHttpConfigurer::disable)
                
                // Cấu hình không lưu trạng thái (Stateless): Server không lưu Session trên RAM, xác thực hoàn toàn qua JWT
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                
                // Đăng ký các Handler xử lý lỗi bảo mật tập trung trả về JSON theo chuẩn chung
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint) // Xử lý lỗi 401 khi chưa xác thực
                        .accessDeniedHandler(accessDeniedHandler)           // Xử lý lỗi 403 khi thiếu quyền hạn
                )
                
                // Định nghĩa quy tắc kiểm soát quyền truy cập theo từng đường dẫn (URL)
                .authorizeHttpRequests(auth -> auth
                        // 1. NHÓM ENDPOINT CÔNG KHAI (PUBLIC - AI CŨNG TRUY CẬP ĐƯỢC):
                        .requestMatchers(
                                "/actuator/**",         // Kiểm tra sức khỏe hệ thống (Health check)
                                "/v3/api-docs/**",       // Tài liệu OpenAPI / Swagger JSON
                                "/swagger-ui/**",        // Giao diện Swagger UI
                                "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll() // Đăng ký tài khoản mới (POST)
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh").permitAll() // Đăng nhập và Làm mới token

                        // 2. NHÓM ENDPOINT DÀNH CHO NGƯỜI DÙNG ĐÃ ĐĂNG NHẬP (USER / AUTHENTICATED):
                        // Chỉ cần mang theo Access Token hợp lệ là được phép truy cập
                        .requestMatchers("/api/v1/auth/logout").authenticated() // Đăng xuất thu hồi token
                        .requestMatchers("/api/v1/users/me", "/api/v1/users/me/**").authenticated() // Xem, sửa hồ sơ cá nhân, đổi mật khẩu

                        // 3. NHÓM ENDPOINT QUẢN TRỊ DÀNH RIÊNG CHO QUẢN TRỊ VIÊN (ROLE_ADMIN):
                        // Bắt buộc trong Token phải chứa quyền "ROLE_ADMIN". Hàm hasRole("ADMIN") tự động so khớp tiền tố ROLE_
                        .requestMatchers(HttpMethod.GET, "/api/v1/users").hasRole("ADMIN")          // Xem danh sách toàn bộ người dùng
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/*").hasRole("ADMIN")        // Xem chi tiết bất kỳ user nào theo ID
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/users/*/status").hasRole("ADMIN") // Khóa / Mở khóa tài khoản user
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/users/*").hasRole("ADMIN")      // Xóa mềm tài khoản user

                        // 4. MỌI ENDPOINT CÒN LẠI ĐỀU PHẢI XÁC THỰC (Mặc định chặn truy cập ẩn danh)
                        .anyRequest().authenticated()
                )
                // Đặt bộ lọc JWT (JwtAuthenticationFilter) chạy trước bộ lọc UsernamePasswordAuthenticationFilter mặc định
                // để giải mã token và nạp Authentication vào SecurityContextHolder trước khi các bộ lọc phía sau kiểm tra quyền
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
