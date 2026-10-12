package com.example.chatservice.config;

import com.example.chatservice.security.WebSocketAuthChannelInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * ==============================================================================
 * CẤU HÌNH HỆ THỐNG WEBSOCKET & STOMP MESSAGE BROKER
 * ------------------------------------------------------------------------------
 * KIẾN THỨC CỐT LÕI:
 * 1. @EnableWebSocketMessageBroker:
 *    - Kích hoạt tính năng Message Broker định tuyến tin nhắn trong Spring.
 * 2. Message Broker (Đơn vị điều phối tin nhắn):
 *    - /topic: Dùng cho kênh Phát thanh chung (Broadcast) hoặc phòng chat chung.
 *      Ví dụ: /topic/rooms/{roomId} -> Bất kỳ ai trong phòng này đều nhận được tin nhắn.
 *      Ví dụ: /topic/admin/inbox -> Thông báo cho tất cả Admin khi có khách hàng mở chat.
 *    - /queue: Dùng cho kênh Tin nhắn riêng tư 1-1 (Point-to-Point).
 * 3. Application Destination Prefixes (/app):
 *    - Khi Client muốn gửi dữ liệu lên Server xử lý (lưu DB), Client sẽ gửi đến:
 *      /app/chat.sendMessage -> Spring sẽ điều hướng vào hàm @MessageMapping trong Controller.
 * 4. Stomp Endpoint (/ws-chat):
 *    - Địa chỉ URL để Client bắt đầu bắt tay kết nối (Handshake).
 *    - Hỗ trợ fallback SockJS (nếu mạng hoặc trình duyệt cũ chặn WebSocket thì tự lùi về HTTP Long-Polling).
 * 5. ChannelRegistration Interceptors:
 *    - Đăng ký WebSocketAuthChannelInterceptor để chặn và xác thực JWT trước khi cho phép vào hệ thống.
 * ==============================================================================
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthChannelInterceptor authChannelInterceptor;

    public WebSocketConfig(WebSocketAuthChannelInterceptor authChannelInterceptor) {
        this.authChannelInterceptor = authChannelInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Kích hoạt In-Memory Simple Broker cho các kênh lắng nghe
        config.enableSimpleBroker("/topic", "/queue");

        // Tiền tố cho các API mà Client gửi lên Server
        config.setApplicationDestinationPrefixes("/app");

        // Tiền tố cho các tin nhắn gửi riêng cho từng cá nhân (convertAndSendToUser)
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Đăng ký endpoint kết nối WebSocket kèm fallback SockJS
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // Đăng ký thêm endpoint thuần (Pure WebSocket) không qua SockJS (cho Postman hoặc client native)
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Đăng ký Interceptor xác thực Token JWT khi Client kết nối
        registration.interceptors(authChannelInterceptor);
    }
}
