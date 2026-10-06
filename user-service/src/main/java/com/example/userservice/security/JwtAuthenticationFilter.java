package com.example.userservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * ==============================================================================
 * BỘ LỌC XÁC THỰC JWT (JWT AUTHENTICATION FILTER) - BƯỚC 3.5
 * ------------------------------------------------------------------------------
 * 1. OncePerRequestFilter:
 *    - Đảm bảo bộ lọc này chỉ thực thi duy nhất 1 LẦN cho mỗi HTTP Request gửi tới.
 *    - Nằm ở hàng đầu trong chuỗi bảo mật SecurityFilterChain trước UsernamePasswordAuthenticationFilter.
 *
 * 2. Luồng xử lý từng request:
 *    - Bước 1: Trích xuất token từ header "Authorization: Bearer <token>".
 *    - Bước 2: Kiểm tra tính hợp lệ của token qua JwtService (chữ ký, thời hạn).
 *    - Bước 3: Nếu hợp lệ, trích xuất UUID người dùng, email, và danh sách vai trò (roles).
 *    - Bước 4: Khởi tạo đối tượng UserPrincipal và nạp vào SecurityContextHolder.
 *    - Bước 5: Chuyển giao request cho các Controller hoặc Filter tiếp theo xử lý.
 * ==============================================================================
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String jwt = extractJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtService.validateToken(jwt)) {
                UUID userId = jwtService.extractUserId(jwt);
                String email = jwtService.extractEmail(jwt);
                Set<String> roles = jwtService.extractRoles(jwt);

                UserPrincipal principal = UserPrincipal.create(userId, email, roles);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Nạp thông tin xác thực vào SecurityContext của luồng xử lý hiện tại
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            log.error("Không thể thiết lập thông tin xác thực người dùng trong Security Context: {}", ex.getMessage());
        }

        // Cho phép request tiếp tục đi qua các bộ lọc phía sau
        filterChain.doFilter(request, response);
    }

    /**
     * Trích xuất JWT từ Header Authorization
     */
    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
