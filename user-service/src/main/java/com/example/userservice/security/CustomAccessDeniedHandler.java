package com.example.userservice.security;

import com.example.common.dto.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * ==============================================================================
 * BỘ XỬ LÝ LỖI TỪ CHỐI TRUY CẬP (ACCESS DENIED HANDLER) - HTTP 403
 * ------------------------------------------------------------------------------
 * Khi client đã đăng nhập hợp lệ (ví dụ có role ROLE_USER) nhưng cố tình gọi các
 * endpoint chỉ dành riêng cho Quản trị viên (ROLE_ADMIN) được bảo vệ bởi @PreAuthorize.
 * -> Spring Security sẽ kích hoạt handle() để trả về cấu trúc ErrorResponse JSON chuẩn.
 * ==============================================================================
 */
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public CustomAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        // Thiết lập HTTP Status 403 Forbidden và Content-Type là application/json
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        // Lấy Trace ID từ Header để theo dõi vết lỗi (Distributed Tracing), nếu không có thì tự sinh mới
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        // Tạo đối tượng ErrorResponse chuẩn chung của hệ thống Microservices
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error(HttpStatus.FORBIDDEN.getReasonPhrase())
                .code("FORBIDDEN")
                .message("Bạn không có quyền hạn truy cập tài nguyên này")
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();

        // Chuyển đối tượng ErrorResponse thành chuỗi JSON và ghi thẳng ra response body
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
