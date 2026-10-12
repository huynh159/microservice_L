package com.example.userservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * ==============================================================================
 * BỘ LỌC TRACE ID PHÂN TÁN (DISTRIBUTED TRACING FILTER) - BƯỚC 6.1
 * ==============================================================================
 * 1. Đọc header "X-Trace-Id" từ request đến (nếu Gateway hoặc Client truyền vào).
 * 2. Nếu chưa có, tự động sinh UUID mới duy nhất cho chu trình request.
 * 3. Đưa traceId vào SLF4J MDC để mọi dòng log trong suốt request tự động in traceId.
 * 4. Gắn "X-Trace-Id" vào response header để Client/DevOps dễ dàng đối chiếu.
 * ==============================================================================
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String MDC_TRACE_ID_KEY = "traceId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = UUID.randomUUID().toString();
        } else {
            traceId = traceId.trim();
        }

        try {
            MDC.put(MDC_TRACE_ID_KEY, traceId);
            response.setHeader(TRACE_ID_HEADER, traceId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TRACE_ID_KEY);
        }
    }
}
