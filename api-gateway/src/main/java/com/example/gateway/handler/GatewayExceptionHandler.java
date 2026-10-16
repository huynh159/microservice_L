package com.example.gateway.handler;

import com.example.common.dto.response.ErrorResponse;
import com.example.gateway.filter.TraceIdGlobalFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * ==============================================================================
 * BỘ XỬ LÝ NGOẠI LỆ TOÀN CỤC CỦA GATEWAY (GATEWAY ERROR WEB EXCEPTION HANDLER)
 * ==============================================================================
 * TƯ DUY KIẾN TRÚC:
 * 1. Tại sao không dùng @ControllerAdvice & @ExceptionHandler thông thường?
 *    - Trong Spring Cloud Gateway, không có Controller (vì Gateway là Reactive Reverse Proxy).
 *    - Request được xử lý bởi chuỗi Netty Channel / WebFlux Filter.
 *    - Mọi ngoại lệ (Route 404 Not Found, Service Down 503, Timeout 504) đều bắn ra ngoài WebFlux.
 *    - Do đó, Spring WebFlux cung cấp giao thức ErrorWebExceptionHandler để bắt lỗi cấp hệ thống!
 *
 * 2. Đảm bảo chuẩn hóa JSON ErrorResponse đồng nhất toàn hệ thống:
 *    - Tránh tình trạng Client nhận được trang HTML Whitelabel mặc định của Spring Boot.
 *    - Tự động chuyển đổi:
 *      + ConnectException (service chết hoặc chưa bật) -> HTTP 503 Service Unavailable
 *      + ResponseStatusException 404 -> HTTP 404 Not Found
 *      + Các ngoại lệ khác -> HTTP 500 Internal Server Error
 *    - Luôn luôn gắn X-Trace-Id vào response để dễ dàng gỡ lỗi (Debugging).
 * ==============================================================================
 */
@Component
@Order(-2) // Đặt độ ưu tiên -2 để ghi đè DefaultErrorWebExceptionHandler (vốn có Order là -1)
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public GatewayExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String errorCode = "GATEWAY_INTERNAL_ERROR";
        String message = "Đã xảy ra lỗi không mong muốn tại API Gateway";

        // 1. Phân loại ngoại lệ hệ thống
        if (ex instanceof ResponseStatusException responseStatusException) {
            status = HttpStatus.resolve(responseStatusException.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            errorCode = "GATEWAY_" + status.name();
            message = responseStatusException.getReason() != null ? responseStatusException.getReason() : status.getReasonPhrase();
        } else if (ex instanceof ConnectException || (ex.getCause() != null && ex.getCause() instanceof ConnectException)) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            errorCode = "SERVICE_UNAVAILABLE";
            message = "Dịch vụ đích hiện tại không phản hồi hoặc đang bảo trì. Vui lòng thử lại sau.";
        }

        log.error("[GATEWAY ERROR] Đường dẫn: {} | Lỗi: {} | Message: {}",
                exchange.getRequest().getPath().value(), ex.getClass().getSimpleName(), ex.getMessage());

        // 2. Thiết lập HTTP Header
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String traceId = exchange.getRequest().getHeaders().getFirst(TraceIdGlobalFilter.TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        response.getHeaders().set(TraceIdGlobalFilter.TRACE_ID_HEADER, traceId);

        // 3. Đóng gói đối tượng ErrorResponse
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .code(errorCode)
                .message(message)
                .path(exchange.getRequest().getPath().value())
                .traceId(traceId)
                .build();

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorResponse);
        } catch (JsonProcessingException jsonException) {
            String fallback = "{\"status\":" + status.value() + ",\"error\":\"" + status.getReasonPhrase() + "\",\"message\":\"" + message + "\"}";
            bytes = fallback.getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
