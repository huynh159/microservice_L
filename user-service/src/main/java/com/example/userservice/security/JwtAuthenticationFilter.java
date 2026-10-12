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

    /**
     * PHƯƠNG THỨC LỌC CHÍNH (THỰC THI DUY NHẤT 1 LẦN CHO MỖI REQUEST):
     * - Chặn bắt HTTP Request trước khi đến Controller.
     * - Trích xuất chuỗi JWT trong Header "Authorization: Bearer <token>".
     * - Nếu token hợp lệ: Giải mã claims (userId, email, roles), tạo Authentication và lưu vào SecurityContext.
     * - Nếu không có token (ví dụ các API public như /login, /register): Bỏ qua, để các bộ lọc sau quyết định.
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            // Bước 1: Trích xuất token thô từ Header Authorization của request
            String jwt = extractJwtFromRequest(request);

            // Bước 2: Kiểm tra nếu có token và token hợp lệ (đúng chữ ký số và chưa hết hạn)
            if (StringUtils.hasText(jwt) && jwtService.validateToken(jwt)) {
                // Bước 3: Trích xuất các Claims từ Payload của token JWT
                UUID userId = jwtService.extractUserId(jwt);     // Lấy User ID (sub claim)
                String email = jwtService.extractEmail(jwt);       // Lấy Email
                Set<String> roles = jwtService.extractRoles(jwt);  // Lấy danh sách vai trò (ví dụ ["ROLE_USER"])

                // Bước 4: Tạo đối tượng UserPrincipal đóng gói danh tính và danh sách quyền hạn (GrantedAuthority)
                UserPrincipal principal = UserPrincipal.create(userId, email, roles);

                // Bước 5: Khởi tạo đối tượng Authentication chuẩn của Spring Security
                // Tham số: (principal, credentials = null vì stateless, authorities = danh sách quyền ROLE_...)
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                
                // Gắn thêm thông tin chi tiết của web request (IP, Session ID...) vào authentication token
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Bước 6: NẠP VÀO SECURITY CONTEXT:
                // Lưu danh tính vào SecurityContextHolder của Thread hiện tại. Kể từ thời điểm này,
                // Spring Security chính thức công nhận request này "ĐÃ ĐĂNG NHẬP" và biết rõ các quyền hạn!
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            // Bắt ngoại lệ để tránh làm sập request, ghi log cảnh báo
            log.error("Không thể thiết lập thông tin xác thực người dùng trong Security Context: {}", ex.getMessage());
        }

        // Bước 7: Luôn chuyển tiếp request cho bộ lọc tiếp theo trong chuỗi Filter Chain thực thi
        filterChain.doFilter(request, response);
    }

    /**
     * HÀM HỖ TRỢ TRÍCH XUẤT CHUỖI JWT TỪ HEADER HTTP:
     * - Header chuẩn có dạng: "Authorization: Bearer eyJhbGciOi..."
     * - Hàm sẽ kiểm tra tiền tố "Bearer ", sau đó cắt bỏ tiền tố 7 ký tự này để lấy đúng chuỗi JWT thô.
     */
    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length()).trim();
        }
        return null; // Không có Header Authorization hoặc không bắt đầu bằng "Bearer "
    }
}
