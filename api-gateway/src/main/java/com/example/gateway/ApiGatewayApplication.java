package com.example.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ==============================================================================
 * CỔNG GIAO TIẾP TẬP TRUNG (API GATEWAY) - KIẾN TRÚC MICROSERVICES
 * ==============================================================================
 * Vai trò cốt lõi của API Gateway trong hệ thống:
 * 1. Single Entry Point (Cửa ngõ duy nhất):
 *    - Toàn bộ Client (Web, Mobile, Postman) chỉ cần kết nối tới cổng 8080.
 *    - Che giấu hoàn toàn địa chỉ IP, cổng nội bộ của các microservice phía sau.
 * 2. Reverse Proxy & Dynamic Routing:
 *    - Điều hướng thông minh request dựa trên URL Path:
 *      + /api/v1/auth/**, /api/v1/users/** -> user-service (Port 8081)
 *      + /api/v1/chat/**, /ws/**          -> chat-service (Port 8083)
 * 3. Edge Authentication & Token Enrichment:
 *    - Xác thực JWT tập trung tại cổng biên trước khi request chạm vào mạng nội bộ.
 *    - Làm giàu request header (X-User-Id, X-User-Email, X-User-Roles) cho downstream.
 * 4. Distributed Tracing:
 *    - Tự động phát sinh hoặc chuyển tiếp X-Trace-Id xuyên suốt mọi dịch vụ.
 * 5. Centralized CORS & Logging:
 *    - Cấu hình chia sẻ tài nguyên nguồn gốc chéo (CORS) một lần duy nhất.
 *    - Đo lường thời gian thực thi (Latency) của từng request.
 * ==============================================================================
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
