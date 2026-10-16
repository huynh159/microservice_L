package com.example.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * ==============================================================================
 * BỘ LỌC GHI LOG & ĐO LƯỜNG HIỆU NĂNG TOÀN CỤC (REQUEST LOGGING GLOBAL FILTER)
 * ==============================================================================
 * TƯ DUY KIẾN TRÚC:
 * 1. Giám sát APM / Observability tại Gateway:
 *    - Toàn bộ lưu lượng mạng (traffic) đều qua Gateway, đây là nơi lý tưởng nhất để:
 *      + Đo thời gian xử lý toàn trình (Latency / Execution Time) của từng API.
 *      + Phát hiện các API chậm (Slow Requests > 1000ms).
 *      + Ghi nhận mã phản hồi HTTP Status Code (200, 400, 401, 500,...).
 *
 * 2. Cơ chế Pre-filter & Post-filter trong Reactive Spring Cloud Gateway:
 *    - Pre-filter: Chạy trước lệnh `chain.filter(exchange)` -> Ghi log BẮT ĐẦU request và bấm giờ `startTime`.
 *    - Post-filter: Chạy trong block `.then(Mono.fromRunnable(...))` -> Được kích hoạt SAU KHI
 *      downstream service hoàn tất xử lý và chuẩn bị gửi response về Client.
 *    - Tính toán độ trễ: `duration = System.currentTimeMillis() - startTime`.
 * ==============================================================================
 */
@Component
public class RequestLoggingGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();
        String traceId = exchange.getRequest().getHeaders().getFirst(TraceIdGlobalFilter.TRACE_ID_HEADER);

        // Pre-filter: Log khi nhận request
        log.info("[GATEWAY START] [{}] {} | TraceId: {}", method, path, traceId);

        // Chuyển tiếp request và kích hoạt hook xử lý hậu kỳ (Post-filter)
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long duration = System.currentTimeMillis() - startTime;
            HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
            int status = statusCode != null ? statusCode.value() : 500;

            if (status >= 400) {
                log.warn("[GATEWAY END] [{}] {} -> Status: {} (Thất bại) | Thời gian: {}ms | TraceId: {}",
                        method, path, status, duration, traceId);
            } else {
                log.info("[GATEWAY END] [{}] {} -> Status: {} (Thành công) | Thời gian: {}ms | TraceId: {}",
                        method, path, status, duration, traceId);
            }
        }));
    }

    @Override
    public int getOrder() {
        // Đặt mức ưu tiên thấp nhất (LOWEST_PRECEDENCE) để hook .then() sẽ chạy cuối cùng
        // khi toàn bộ các filter khác và downstream service đã xử lý xong response!
        return Ordered.LOWEST_PRECEDENCE;
    }
}
