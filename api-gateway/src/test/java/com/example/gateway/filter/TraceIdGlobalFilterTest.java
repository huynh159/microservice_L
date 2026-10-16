package com.example.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Kiểm thử Bộ lọc Trace ID Phân tán (TraceIdGlobalFilter)")
class TraceIdGlobalFilterTest {

    private TraceIdGlobalFilter traceIdFilter;

    @BeforeEach
    void setUp() {
        traceIdFilter = new TraceIdGlobalFilter();
    }

    @Test
    @DisplayName("Nếu request chưa có X-Trace-Id thì Gateway phải tự động sinh UUID mới")
    void shouldGenerateTraceIdWhenMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/me").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<String> downstreamTraceId = new AtomicReference<>();
        GatewayFilterChain chain = mutatedExchange -> {
            downstreamTraceId.set(mutatedExchange.getRequest().getHeaders().getFirst(TraceIdGlobalFilter.TRACE_ID_HEADER));
            return Mono.empty();
        };

        StepVerifier.create(traceIdFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(downstreamTraceId.get())
                .isNotNull()
                .isNotBlank();
    }

    @Test
    @DisplayName("Nếu request đã có X-Trace-Id sẵn từ Client thì Gateway phải bảo toàn giá trị đó")
    void shouldPreserveExistingTraceId() {
        String existingTraceId = "custom-trace-id-12345";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/me")
                .header(TraceIdGlobalFilter.TRACE_ID_HEADER, existingTraceId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<String> downstreamTraceId = new AtomicReference<>();
        GatewayFilterChain chain = mutatedExchange -> {
            downstreamTraceId.set(mutatedExchange.getRequest().getHeaders().getFirst(TraceIdGlobalFilter.TRACE_ID_HEADER));
            return Mono.empty();
        };

        StepVerifier.create(traceIdFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(downstreamTraceId.get()).isEqualTo(existingTraceId);
    }
}
