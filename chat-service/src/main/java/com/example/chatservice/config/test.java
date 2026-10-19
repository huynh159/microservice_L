package com.example.chatservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final WebSocketAuthChannelInterceptor authChannelInterceptor;

    public WebSocketConfig(WebSocketAuthChannelIntercept authChannelInterceptor) {
        this.authChannelInterceptor = authChannelInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // kích hoạt In-memory simple broker cho các kênh lắng nghe
        config.enableSimpleBroker("/topic", "/queue");
        // tiền tố cho các API mà client gửi lên server
        config.setApplicationDestinationPrefixes("/app");
        // tiền tố cho các tin nhắ gửi riêng cho từng cá nhân (convertAndSendToUser)
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEnpoints(StompEndpoinRegistry registry) {
        // đăng kí endpoint kết nối đến websocket kèm fallback SockJS
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPaterns("*")
                .withSockJS();
        // đăng kí thêm endpoint thuần (pure websocket) không qua socketJS (cho postmant
        // hoặc client native)
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPaterns("*");

    }

    @Override
    public void configureClientInboundChanell(ChannelRegistration registration) {
        // Đăng kí Interceptor xác thực JWT khi client kết nối
        registration.interceptor(authChannelInterceptor);
    }

}