package com.example.userservice.security;

import com.example.common.dto.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * ==============================================================================
 * BỘ XỬ LÝ LỖI CHƯA XÁC THỰC (AUTHENTICATION ENTRY POINT) - HTTP 401
 * ------------------------------------------------------------------------------
 * Khi client gọi một API yêu cầu đăng nhập mà:
 * 1. Không truyền Header Authorization (thiếu token).
 * 2. Token đã hết hạn hoặc chữ ký bị giả mạo.
 * -> Spring Security sẽ kích hoạt commence() để trả về cấu trúc ErrorResponse JSON chuẩn.
 * ==============================================================================
 */
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public CustomAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error(HttpStatus.UNAUTHORIZED.getReasonPhrase())
                .code("UNAUTHORIZED")
                .message("Yêu cầu xác thực tài khoản hoặc token không hợp lệ/đã hết hạn")
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
