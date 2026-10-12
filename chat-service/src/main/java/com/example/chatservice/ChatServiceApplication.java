package com.example.chatservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * ==============================================================================
 * CHAT REALTIME MICROSERVICE
 * ------------------------------------------------------------------------------
 * Khởi chạy Chat Service (Port 8083) sử dụng WebSocket + STOMP Protocol
 * Kết nối tương tác với User Service (Port 8081) để xác thực người dùng.
 * ==============================================================================
 */
@SpringBootApplication
@EnableJpaAuditing
public class ChatServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatServiceApplication.class, args);
    }
}
