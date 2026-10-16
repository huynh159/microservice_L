package com.example.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * ==============================================================================
 * BỘ LỌC ĐỊNH DANH PHÂN TÁN TOÀN CỤC (DISTRIBUTED TRACE ID GLOBAL FILTER)
 * ==============================================================================
 * TƯ DUY KIẾN TRÚC:
 * 1. Distributed Tracing là gì?
 *    - Trong hệ thống phân tán, một thao tác của người dùng có thể đi qua:
 *      Client -> API Gateway -> user-service -> Kafka -> notification-service.
 *    - Nếu xảy ra lỗi hoặc chậm ở giữa chừng, làm sao biết được log ở các service
 *      khác nhau thuộc về cùng MỘT request duy nhất?
 *    - Giải pháp: Gắn một mã định danh duy nhất (X-Trace-Id) vào Header ngay tại Gateway!
 *
 * 2. Luồng hoạt động của Filter này:
 *    a) Request từ Client tới Gateway:
 *       - Kiểm tra header "X-Trace-Id". Nếu Client đã truyền thì giữ nguyên.
 *       - Nếu chưa có, Gateway tự động sinh UUID mới ngẫu nhiên.
 *    b) Pre-filter Phase:
 *       - Biến đổi (Mutate) ServerHttpRequest để chèn "X-Trace-Id" vào request gửi tới Microservice.
 *       - Nhờ đó, TraceIdFilter ở user-service và các service khác sẽ nhận được TraceId này!
 *    c) Post-filter Phase:
 *       - Đăng ký hook `beforeCommit()` để tự động gắn "X-Trace-Id" vào Header của HTTP Response trả về cho Client.
 *       - Giúp lập trình viên Frontend / QA / DevOps khi thấy lỗi trên màn hình có thể lấy ngay TraceId để tra cứu log.
 *
 * 3. Thứ tự ưu tiên (Order):
 *    - Đặt HIGHEST_PRECEDENCE để chạy ĐẦU TIÊN trong toàn bộ chuỗi Filter.
 * ==============================================================================
 */
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TraceIdGlobalFilter.class);
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. Lấy X-Trace-Id từ client gửi lên (nếu có)
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);

        // 2. Nếu chưa có, tự động sinh mã UUID mới duy nhất
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = UUID.randomUUID().toString();
        } else {
            traceId = traceId.trim();
        }

        final String finalTraceId = traceId;

        // 3. Biến đổi (mutate) request để đẩy X-Trace-Id sang downstream service
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(TRACE_ID_HEADER, finalTraceId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        // 4. Đính kèm X-Trace-Id vào HTTP Response header trả về cho Client
        mutatedExchange.getResponse().beforeCommit(() -> {
            mutatedExchange.getResponse().getHeaders().set(TRACE_ID_HEADER, finalTraceId);
            return Mono.empty();
        });

        // 5. Chuyển giao request đã được làm giàu sang Filter tiếp theo
        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        // Ưu tiên chạy cao nhất để mọi Filter phía sau đều có sẵn X-Trace-Id
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
