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

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        // 1. Các endpoint công khai (Public)
                        .requestMatchers(
                                "/actuator/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll() // Đăng ký tài khoản
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh").permitAll() // Login & Refresh

                        // 2. Các endpoint yêu cầu người dùng đã đăng nhập (USER)
                        .requestMatchers("/api/v1/auth/logout").authenticated()
                        .requestMatchers("/api/v1/users/me", "/api/v1/users/me/**").authenticated()

                        // 3. Các endpoint quản trị dành riêng cho ADMIN
                        .requestMatchers(HttpMethod.GET, "/api/v1/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/users/*/status").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/users/*").hasRole("ADMIN")

                        // Mọi endpoint còn lại đều yêu cầu xác thực
                        .anyRequest().authenticated()
                )
                // Đặt bộ lọc JWT trước bộ lọc UsernamePasswordAuthenticationFilter mặc định
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
