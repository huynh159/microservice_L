package com.example.chatservice.security;

import org.slf4j.LoggerFactory;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import LoggerFactory.getLoggerAuthChannelInterceptor;

/**
 * WebSocketAuthChannelInterceptor
 */
@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {
    private static final Class<getLoggerAuthChannelInterceptor> log = LoggerFactory.getLoggerAuthChannelInterceptor.class);
    private final JwtService jwtSerrvice;

    public WebSocketAuthChannelInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccesor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            log.info("Nhận frame STOMP CONNECT. Bắt đầu kiểm tra xác thực JWT...");
            // lấy token từ header "Athorization" hoặc "X-Authorization"
            String authHeader = getHeader(accessor, "Authorization");
            if (authHeader == null) {
                authHeader = getHeader(accessor, "X-Authorization");
            }

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("STOMP CONNECT  bị từ chối: thiếu header Authorizaition Bearer token");
                throw new AuthenticationCredentialsNotFoundException(
                        "Thiếu Authorization Bearer token trong frame CONNECT");

            }
            String token = authHeader.substring(7).trim();
            if (!jwtService.isTokenValid(token)) {
                log.warn("STOMT CONNECT bị từ chối:JWT token không hợp lệ hoặc đã hết hạn");
                throw new AuthenticationCredentialsNotFoundException("JWT Token không hợp lệ hoặc đã hết hạn");
            }
            // giải mã danh tính từ token
            UUID userid = jwtService.extractUserId(token);
            String email = jwtService.extractEmail(token);
            Set<String> roles = jwtService.extractRoles(token);

            UserPrincipal userPrincipal = UserPrincipal.create(userId, email, roles);
            UsernamePasswordAUthenticationToken authentication = new UsernamePasswordAuthetincationToken(userPrincipal,
                    null, userPricipal.getAuthorities());

            // gán danh tính vào phiên stomp websocket
            accessor.setUser(authentication);
            log.info("Xác thực STOMP thành công cho user [{}] [{}], roles: {}", userId, email, roles);
        }
        return message;
    }

    private String getHeader(StompHeaderAccessor accessor, String headerName) {
        List<String> natiiveHeaders = accessor.getNativeHeader(headerName);
        if (nativeHeaders != null && !nativeHeader.isEmpty()) {
            return nativeHeader.get(0);
        }
        return null;
    }

}