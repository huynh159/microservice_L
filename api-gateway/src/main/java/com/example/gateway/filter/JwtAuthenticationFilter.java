package com.example.gateway.filter;

import com.example.common.dto.response.ErrorResponse;
import com.example.gateway.security.JwtService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * ==============================================================================
 * BỘ LỌC XÁC THỰC JWT TOÀN CỤC TẠI CỔNG BIÊN (EDGE JWT AUTHENTICATION FILTER)
 * ==============================================================================
 * TƯ DUY KIẾN TRÚC & LUỒNG HOẠT ĐỘNG:
 *
 * 1. Nguyên lý Gateway làm lá chắn bảo vệ (Shield Pattern):
 *    - Toàn bộ request từ bên ngoài vào hệ thống đều phải đi qua Filter này.
 *    - Các endpoint công khai (Public) như: Đăng nhập, Đăng ký, Refresh Token, Actuator...
 *      được đi qua thẳng mà không cần kiểm tra Token (Whitelisting).
 *    - Các endpoint yêu cầu quyền hạn (Protected/Private) bắt buộc phải mang theo
 *      Access Token trong header "Authorization: Bearer <token>".
 *
 * 2. Cơ chế Header Enrichment (Làm giàu Header cho Downstream Services):
 *    - Khi Token hợp lệ, Gateway giải mã các Claims quan trọng:
 *      + User ID (UUID)
 *      + Email
 *      + Danh sách Roles (ví dụ: ROLE_USER, ROLE_ADMIN)
 *    - Gateway dùng phương thức mutate() để đính kèm các header mới vào request:
 *      + X-User-Id: 123e4567-e89b-12d3-a456-426614174000
 *      + X-User-Email: user@example.com
 *      + X-User-Roles: ROLE_USER,ROLE_ADMIN
 *    - Khi downstream microservices (user-service, chat-service) nhận request,
 *      service có thể lấy ngay thông tin định danh từ Header mà không tốn công giải mã lại.
 *
 * 3. Trả về mã lỗi 401 Unauthorized chuẩn hóa JSON:
 *    - Nếu thiếu Token hoặc Token hết hạn/sai chữ ký:
 *      Gateway ngắt ngay chuỗi filter (Short-circuiting), đóng gói đối tượng ErrorResponse
 *      thành JSON và trả về trực tiếp cho Client, ngăn triệt để request độc hại vào mạng nội bộ.
 * ==============================================================================
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        HttpMethod method = request.getMethod();

        // 1. Kiểm tra xem endpoint có nằm trong danh sách Public (Whitelisted) hay không
        if (isPublicEndpoint(method, path)) {
            log.debug("Bỏ qua xác thực JWT cho public endpoint: [{} {}]", method, path);
            return chain.filter(exchange);
        }

        // 2. Trích xuất Header Authorization
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Từ chối truy cập [{} {}]: Thiếu hoặc sai định dạng Authorization header", method, path);
            return onError(exchange, HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_MISSING",
                    "Yêu cầu không chứa Access Token hợp lệ trong Header Authorization");
        }

        String token = authHeader.substring(7).trim();

        // 3. Xác minh tính hợp lệ và chữ ký số của JWT
        if (!jwtService.validateToken(token)) {
            log.warn("Từ chối truy cập [{} {}]: JWT không hợp lệ hoặc đã hết hạn", method, path);
            return onError(exchange, HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID",
                    "Access Token không hợp lệ hoặc đã hết hạn");
        }

        // 4. Trích xuất thông tin người dùng từ JWT Claims
        String userId = jwtService.extractUserId(token);
        String email = jwtService.extractEmail(token);
        Set<String> roles = jwtService.extractRoles(token);
        String rolesStr = String.join(",", roles);

        log.debug("Xác thực Gateway thành công cho user: {} ({}) với roles: [{}]", email, userId, rolesStr);

        // 5. Làm giàu Header (Header Enrichment) cho downstream microservices
        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-User-Id", userId)
                .header("X-User-Email", email)
                .header("X-User-Roles", rolesStr)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    /**
     * Danh sách kiểm tra các URL được phép truy cập công khai (Public Whitelist):
     * - POST /api/v1/auth/login: Đăng nhập
     * - POST /api/v1/auth/refresh: Làm mới token
     * - POST /api/v1/users: Đăng ký tài khoản người dùng mới
     * - /ws/**: Kết nối WebSocket (chat-service tự xác thực ở STOMP Frame)
     * - /actuator/**: Endpoint giám sát sức khỏe dịch vụ
     * - /v3/api-docs/**, /swagger-ui/**: Tài liệu Swagger OpenAPI
     */
    private boolean isPublicEndpoint(HttpMethod method, String path) {
        // WebSocket Chat Handshake
        if (path.startsWith("/ws")) {
            return true;
        }

        // Tài liệu API & Giám sát sức khỏe
        if (pathMatcher.match("/actuator/**", path)
                || pathMatcher.match("/v3/api-docs/**", path)
                || pathMatcher.match("/swagger-ui/**", path)
                || pathMatcher.match("/swagger-ui.html", path)
                || pathMatcher.match("/webjars/**", path)) {
            return true;
        }

        // Các API Xác thực & Đăng ký người dùng
        if (HttpMethod.POST.equals(method)) {
            if (pathMatcher.match("/api/v1/auth/login", path)
                    || pathMatcher.match("/api/v1/auth/refresh", path)
                    || pathMatcher.match("/api/v1/users", path)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Tạo HTTP Response lỗi 401 Unauthorized dạng JSON chuẩn hóa theo định dạng ErrorResponse của common-lib
     */
    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // Lấy TraceId đã được TraceIdGlobalFilter sinh trước đó (hoặc tự sinh dự phòng)
        String traceId = exchange.getRequest().getHeaders().getFirst(TraceIdGlobalFilter.TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        response.getHeaders().set(TraceIdGlobalFilter.TRACE_ID_HEADER, traceId);

        // Đóng gói đối tượng ErrorResponse
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .code(code)
                .message(message)
                .path(exchange.getRequest().getPath().value())
                .traceId(traceId)
                .build();

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorResponse);
        } catch (JsonProcessingException e) {
            String fallbackJson = "{\"status\":401,\"error\":\"Unauthorized\",\"code\":\"" + code + "\",\"message\":\"" + message + "\"}";
            bytes = fallbackJson.getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // Chạy ngay sau TraceIdGlobalFilter (HIGHEST_PRECEDENCE) để đảm bảo luôn có TraceId
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
