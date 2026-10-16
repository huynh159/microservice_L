package com.example.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Kiểm thử Khởi động Context của API Gateway")
class ApiGatewayApplicationTests {

    @Test
    @DisplayName("Spring Application Context của Gateway phải khởi động thành công mà không có lỗi xung đột")
    void contextLoads() {
        // Kiểm tra xem toàn bộ Beans, Route Locator và Filter đã nạp chính xác
    }
}
